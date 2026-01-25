Static-LLM 项目阶段性总结报告
1. 项目架构 (Spring Boot Multi-Module)
   项目旨在实现一个静态分析 + LLM 修复的自动化流水线，结合了RAG (检索增强生成) 技术以提供更精准的修复建议。
   模块划分
   static-llm (Root): 父工程，管理依赖版本。
   static-llm-common: 公共模块。
   实体: AnalysisTask (MyBatis-Plus Entity), UnifiedIssue (归一化缺陷模型)。
   枚举: TaskStatus (状态机), ReturnCode。
   响应: Result<T> (统一 API 响应)。
   static-llm-adapter: 外部工具适配层。
   Analysis: SpotBugsAdapter (ProcessBuilder 调用 SpotBugs CLI)。
   LLM: DeepSeekLlmClient (Hutool 调用 DeepSeek API), MockLlmClient。
   static-llm-core: 核心业务逻辑层。
   Normalizer: SpotBugsNormalizer (XML -> UnifiedIssue)。
   Extractor: JavaParserContextExtractor (AST/SymbolSolver 提取源码上下文，支持跨函数 Level+1)。
   static-llm-web: Web 服务与调度层。
   Service: AnalysisTaskServiceImpl (异步状态机驱动流程), ChromaOllamaRagServiceImpl (RAG 服务)。
   Controller: TaskController (任务管理), KnowledgeController (知识库管理)。
   Infra: MySQL (持久化任务), ChromaDB (向量存储), Ollama (Embedding 模型)。
   核心技术栈
   框架: Spring Boot 3.1.5, MyBatis-Plus 3.5.5
   分析工具: SpotBugs 4.9.8 (CLI mode)
   代码解析: JavaParser + JavaSymbolSolver
   LLM 编排: LangChain4j 0.35.0 (Ollama, Chroma 适配器)
   向量库: ChromaDB (Docker)
   Embedding: BGE-M3 (via Ollama)
   大模型: DeepSeek-Chat (Online API)
2. 当前进度说明
   ✅ 已完成功能
   工程脚手架: 建立了 Maven 多模块结构。
   静态分析接入: 封装了 SpotBugs 的命令行调用，并解析其 XML 报告为统一格式。
   LLM 接入: 完成了 DeepSeek API 的对接。
   任务管理:
   移除了 Quartz，改为基于 MySQL 的持久化任务队列。
   实现了 SUBMITTED -> COMPLETED 的完整状态流转。
   实现了异步执行 (@Async) 与任务取消机制。
   源码上下文提取: 使用 JavaParser 提取出错方法的完整代码及被调用方法的签名 (Context Level +1)。
   RAG 系统 (进阶版):
   集成了 ChromaDB (向量存储) 和 Ollama/BGE-M3 (Embedding)。
   实现了知识检索接口，并在 LLM 调用前自动增强 Prompt。
   实现了动态知识添加接口 (POST /api/knowledge/add)。
   🚧 待优化/进行中
   代码获取: 目前仅支持分析本地路径，Git Clone 功能预留了 TODO。
   上下文深度: Call Graph 目前仅向下搜索一层 (Callee)，尚未实现向上搜索 (Caller)。
3. 待办事项 (ToDo List)
   P0: 核心功能完善
   [ ] Git 代码拉取: 实现 static-llm-code-retrieval 模块，支持从 Git URL 拉取代码并自动编译 (mvn package) 生成 Jar 包。
   [ ] 结果持久化: 将 UnifiedIssue 列表（包含修复建议）存入 MySQL 单独的表 (analysis_issue)，而不仅仅是存 JSON 到 Task 表。
   P1: RAG 增强
   [ ] 知识库初始化: 编写脚本或 Admin 接口，批量导入 CWE/OWASP 等标准安全文档。
   [ ] 混合检索: 引入关键词检索 (Keyword Search) 配合向量检索，提升特定术语（如 "SQL注入"）的召回率。
   P2: 用户体验与扩展
   [ ] 前端可视化: 开发一个简单的 Vue/React 前端，展示任务进度、代码高亮和修复建议对比。
   [ ] 更多分析工具: 接入 CheckStyle 或 PMD，丰富检测维度。
   快速启动指南
   启动中间件:
   docker run -d -p 8000:8000 chromadb/chroma    ollama pull bge-m3
   配置数据库: MySQL 创建 static_llm 库，导入建表语句。
   配置应用: 修改 application.yml 中的 DB 密码、DeepSeek Key、Chroma/Ollama 地址。
   运行: 启动 StaticLlmApplication。