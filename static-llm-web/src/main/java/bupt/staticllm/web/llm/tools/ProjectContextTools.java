package bupt.staticllm.web.llm.tools;

import bupt.staticllm.web.context.AnalysisContextHolder;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ProjectContextTools {

    @Tool("读取指定文件的内容，用于获取代码细节。filePath应为相对于项目根目录的路径（如 src/main/java/com/example/Demo.java）")
    public String readFile(String filePath) {
        String rootPath = AnalysisContextHolder.getSourcePath();
        if (rootPath == null || rootPath.isEmpty()) {
            return "Error: Source path context is not set.";
        }

        try {
            // 防止路径遍历攻击，虽然是在内网环境，但保持好习惯
            if (filePath.contains("..")) {
                return "Error: Access denied (relative paths with .. are not allowed)";
            }

            Path fullPath = Paths.get(rootPath, filePath);
            File file = fullPath.toFile();

            if (!file.exists()) {
                return "Error: File not found: " + filePath;
            }
            
            if (file.isDirectory()) {
                return "Error: Path is a directory, please use listDirectory tool.";
            }

            log.info("Tool Use: Reading file {}", fullPath);
            return Files.readString(fullPath);
        } catch (Exception e) {
            log.error("Tool Use Error reading file: {}", filePath, e);
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool("列出目录下的文件结构，用于了解项目结构。dirPath应为相对于项目根目录的路径，留空则列出根目录")
    public String listDirectory(String dirPath) {
        String rootPath = AnalysisContextHolder.getSourcePath();
        if (rootPath == null || rootPath.isEmpty()) {
            return "Error: Source path context is not set.";
        }

        try {
            if (dirPath == null) dirPath = "";
            if (dirPath.contains("..")) {
                return "Error: Access denied";
            }

            Path fullPath = Paths.get(rootPath, dirPath);
            File dir = fullPath.toFile();

            if (!dir.exists() || !dir.isDirectory()) {
                return "Error: Directory not found or is not a directory: " + dirPath;
            }

            log.info("Tool Use: Listing directory {}", fullPath);
            File[] files = dir.listFiles();
            if (files == null) return "Directory is empty";

            StringBuilder sb = new StringBuilder();
            for (File f : files) {
                sb.append(f.getName());
                if (f.isDirectory()) {
                    sb.append("/");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Tool Use Error listing directory: {}", dirPath, e);
            return "Error listing directory: " + e.getMessage();
        }
    }
}
