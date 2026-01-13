package bupt.staticllm.core.extractor.impl;

import bupt.staticllm.common.model.UnifiedIssue;
import bupt.staticllm.core.extractor.ContextExtractor;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class JavaParserContextExtractor implements ContextExtractor {

    @Override
    public void enrichContext(List<UnifiedIssue> issues, String sourceRoot) {
        if (issues == null || issues.isEmpty()) return;

        // 1. 初始化 SymbolSolver
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        // 尝试添加源码目录作为解析源
        try {
            if (new File(sourceRoot).exists()) {
                typeSolver.add(new JavaParserTypeSolver(new File(sourceRoot)));
            }
        } catch (Exception e) {
            log.warn("源码目录解析失败: {}", sourceRoot);
        }
        
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        // 配置 StaticJavaParser
        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(symbolSolver);
        StaticJavaParser.setConfiguration(config);

        // 2. 遍历问题提取上下文
        for (UnifiedIssue issue : issues) {
            try {
                String fullContext = extractForIssue(issue, sourceRoot);
                issue.setCodeSnippet(fullContext);
            } catch (Exception e) {
                log.error("提取上下文失败: {}", issue.getFilePath(), e);
                issue.setCodeSnippet("// Context extraction failed: " + e.getMessage());
            }
        }
    }

    private String extractForIssue(UnifiedIssue issue, String sourceRoot) {
        String relativePath = issue.getFilePath();
        if (relativePath == null) return "// No file path";

        // 构建绝对路径 (SpotBugs 有时返回相对路径 src/main/java/...)
        // 如果 relativePath 包含了 src/main/java 前缀，需要小心拼接
        Path srcPath = Paths.get(sourceRoot);
        // 简单的尝试寻找文件
        File targetFile = findFile(srcPath, relativePath);
        
        if (!targetFile.exists()) {
            return "// File not found: " + relativePath;
        }

        try {
            CompilationUnit cu = StaticJavaParser.parse(targetFile);
            StringBuilder context = new StringBuilder();

            // 1. 提取当前类字段 (Level 0)
            int line = issue.getStartLine();
            Optional<ClassOrInterfaceDeclaration> classDeclOpt = cu.findFirst(ClassOrInterfaceDeclaration.class, 
                c -> isLineInNode(c, line));
            
            if (classDeclOpt.isPresent()) {
                ClassOrInterfaceDeclaration classDecl = classDeclOpt.get();
                context.append("// --- Class Fields ---\n");
                for (FieldDeclaration field : classDecl.getFields()) {
                    context.append(field.toString()).append("\n");
                }
                context.append("\n");
            }

            // 2. 提取当前方法 (Level 0)
            Optional<MethodDeclaration> methodDeclOpt = cu.findFirst(MethodDeclaration.class, 
                m -> isLineInNode(m, line));

            if (methodDeclOpt.isPresent()) {
                MethodDeclaration method = methodDeclOpt.get();
                context.append("// --- Target Method (Level 0) ---\n");
                context.append(method.toString()).append("\n");

                // 3. 提取被调用的方法 (Level +1) - 简单的一层向下
                context.append("\n// --- Called Methods (Level +1) ---\n");
                method.findAll(MethodCallExpr.class).forEach(call -> {
                    try {
                        ResolvedMethodDeclaration resolved = call.resolve();
                        // 过滤掉 JDK 方法，只看项目内部代码
                        // 这是一个简单的启发式过滤，避免打印 System.out.println 等
                        String packageName = resolved.getPackageName();
                        if (!packageName.startsWith("java.") && !packageName.startsWith("javax.")) {
                             context.append("// Call: ").append(resolved.getQualifiedSignature()).append("\n");
                             // 注意：要获取源码需要找到对应的定义节点，这在跨文件时比较复杂
                             // 这里暂时只输出签名，避免性能爆炸
                        }
                    } catch (Exception ignored) {
                        // 无法解析的方法忽略
                    }
                });
            } else {
                // 如果不在方法内（比如在字段初始化处报错），提取周围代码
                context.append("// --- Code Fragment ---\n");
                String[] lines = Files.readAllLines(targetFile.toPath()).toArray(new String[0]);
                int start = Math.max(0, line - 5);
                int end = Math.min(lines.length, line + 5);
                for (int i = start; i < end; i++) {
                    context.append(lines[i]).append("\n");
                }
            }

            return context.toString();

        } catch (IOException e) {
            return "// Error reading file: " + e.getMessage();
        }
    }

    /**
     * 递归查找文件，处理路径不匹配问题
     */
    private File findFile(Path root, String partialPath) {
        File direct = root.resolve(partialPath).toFile();
        if (direct.exists()) return direct;
        
        // 尝试去除 src/main/java 前缀匹配
        // SpotBugs sourcePath: com/example/Test.java
        // Root: D:/project/src/main/java
        return new File(root.toFile(), partialPath);
    }

    private boolean isLineInNode(Node node, int line) {
        return node.getBegin().isPresent() && node.getEnd().isPresent() 
            && node.getBegin().get().line <= line 
            && node.getEnd().get().line >= line;
    }
}
