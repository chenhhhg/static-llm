# Static LLM (静态代码分析与 LLM 审计系统)

Static LLM 是一个基于 **Spring Boot 3** 的智能代码审计平台，结合了传统静态分析工具 (**SpotBugs**) 的准确性与大语言模型 (**DeepSeek**) 的代码理解能力。它通过自动化流程发现代码中的潜在缺陷，利用 **RAG (检索增强生成)** 技术检索修复知识，并最终由 LLM 提供精准的修复建议。

## 🌟 核心特性 (Features)

*   **双重分析引擎**:
   *   **静态分析**: 集成 SpotBugs (4.9.8) 进行字节码级别的缺陷扫描。
   *   **LLM 审计**: 集成 DeepSeek V3/R1 模型，对静态分析结果进行二次判别（误报过滤）和修复建议生成。
*   **RAG 知识增强**:
   *   内置向量数据库 (ChromaDB) 和 Embedding 模型 (Ollama/BGE-M3)。
   *   在 LLM 分析前，自动检索相关的安全知识和修复模式，注入 Prompt 中以提高建议质量。
*   **高性能并发架构**:
   *   **任务级并发**: 支持多个分析任务并行处理。
   *   **Issue 级并发**: 针对每个缺陷点采用独立线程池并发调用 LLM，大幅缩短审计时间。
   *   **秒传机制**: 基于 Jar 包 MD5 的分析结果缓存，相同文件直接复用报告，实现“秒级”响应。
*   **健壮的任务管理**:
   *   **断点续传**: 系统重启后自动扫描并恢复未完成的任务，智能跳过已完成的步骤。
   *   **状态流转**: 清晰的任务生命周期 (SUBMITTED -> ANALYZING -> JUDGING -> COMPLETED)。
*   **上下文感知**:
   *   基于 JavaParser 自动提取缺陷相关的源码片段（Context Level +1），为 LLM 提供充足的上下文。

## 🛠 技术栈 (Tech Stack)

*   **Language**: Java 17
*   **Framework**: Spring Boot 3.1.5, MyBatis-Plus 3.5.5
*   **AI & LLM**:
   *   **LangChain4j 0.35.0**: LLM 编排框架
   *   **DeepSeek**: 推理模型
   *   **Ollama + BGE-M3**: 本地 Embedding 服务
   *   **ChromaDB**: 向量数据库
*   **Static Analysis**: SpotBugs 4.9.8 (CLI Mode)
*   **Code Parsing**: JavaParser
*   **Database**: MySQL 8.0+
*   **Docs**: SpringDoc OpenAPI 3 (Swagger)

## 🚀 快速开始 (Getting Started)

### 1. 环境准备

*   **JDK 17+**
*   **Maven 3.8+**
*   **MySQL 8.0+**: 创建数据库 `static_llm` 并导入 `static-llm-web/src/main/resources/sql/*.sql`。
*   **ChromaDB**: 启动向量数据库 (推荐 Docker)。
    ```bash
    docker run -d -p 8000:8000 chromadb/chroma
    ```
*   **Ollama**: 启动本地 Embedding 服务。
    ```bash
    ollama pull bge-m3
    ollama serve
    ```

### 2. 配置应用

修改 `static-llm-web/src/main/resources/application.yml` (或创建 `application-dev.yml`):

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/static_llm
    username: root
    password: your_password

llm:
  deepseek:
    api-key: your_deepseek_api_key
    base-url: https://api.deepseek.com
    model: deepseek-chat
```

### 3. 运行项目

```bash
cd static-llm
mvn clean package -DskipTests
java -jar static-llm-web/target/static-llm-web-1.0.0-SNAPSHOT.jar
```

### 4. 访问接口

*   **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
*   **API Docs**: `http://localhost:8080/v3/api-docs`

## 📂 项目结构 (Project Structure)

```
static-llm
├── static-llm-common    # 公共模块 (实体, 枚举, 工具类)
├── static-llm-adapter   # 适配器模块 (SpotBugs 封装)
├── static-llm-core      # 核心业务 (报告解析, 上下文提取)
└── static-llm-web       # Web 服务 (Controller, Service, RAG, LLM Agent)
```

## 📝 开发指南

详细的前端对接指南和接口说明，请参考 [FRONTEND_GUIDE.md](FRONTEND_GUIDE.md)。

## 📅 未来规划 (Roadmap)

* 添加issue的统计、多种筛选方式（状态、聚会）
* 添加与Benchmark本身的对比
* 精简上下文防止token耗费太大
