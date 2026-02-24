# 前端开发指南 (Frontend Development Guide)

本文档旨在为前端开发人员提供关于 **Static LLM (静态代码分析与 LLM 审计系统)** 的项目全貌、当前进展、未来规划以及详细的接口说明。

## 1. 项目概述

Static LLM 是一个结合了传统静态代码分析工具 (SpotBugs) 与大语言模型 (DeepSeek) 的智能代码审计平台。它旨在通过自动化流程发现代码中的潜在缺陷，并利用 LLM 提供精准的修复建议，从而提高代码质量和安全性。

### 核心流程
1.  **任务提交**: 用户上传或指定目标 Jar 包及源码路径。
2.  **静态分析**: 系统调用 SpotBugs 进行扫描，生成基础报告。
3.  **智能增强**:
    *   **上下文提取**: 自动提取缺陷相关的源码片段。
    *   **RAG 检索**: 从知识库中检索相关的修复模式和最佳实践。
4.  **LLM 审计**: 将增强后的信息发送给 DeepSeek 模型，进行误报判定和修复建议生成。
5.  **结果展示**: 前端展示最终的审计报告，包含缺陷详情、AI 分析依据和修复代码。

## 2. 项目现状 (Current Status)

截至 2026-02-20，后端已完成以下核心功能：

*   **工程架构**: 基于 Spring Boot 3 + MyBatis Plus 的多模块架构。
*   **分析引擎**: 集成 SpotBugs，支持 XML 报告解析与归一化。
*   **任务管理**:
    *   支持异步任务提交与状态流转 (SUBMITTED -> ANALYZING -> JUDGING -> COMPLETED)。
    *   支持任务取消与删除。
    *   **并发审计**: 针对每个 Issue 采用线程池并发调用 LLM，提高处理速度。
    *   **秒传机制**: 基于 Jar 包 MD5 的分析结果缓存，相同文件直接复用报告。
*   **RAG 系统**:
    *   集成 ChromaDB 向量数据库。
    *   支持知识库的增删改查 (CRUD)。
    *   分析时自动检索相关知识增强 Prompt。
*   **API 文档**: 集成 Swagger/OpenAPI 3。

## 3. 未来规划 (Roadmap)

前端开发需注意以下预留接口或未来可能变更的功能：

*   **代码获取方式**: 目前仅支持服务器本地路径 (`sourcePath`)。未来将支持 **Git Clone** 功能，前端需预留 Git 仓库地址输入框。
*   **WebSocket 推送**: 目前任务状态需轮询查询。未来计划引入 WebSocket 实现实时进度推送。
*   **用户体系**: 目前无用户权限控制。未来可能添加登录/注册及多租户隔离。
*   **交互式修复**: 未来可能支持在前端直接应用 AI 建议的修复代码 (需后端配合文件写入能力)。

## 4. 接口文档 (API Documentation)

后端服务默认运行在 `http://localhost:8080`。
Swagger UI 地址: `http://localhost:8080/swagger-ui/index.html`

### 4.1 任务管理 (Task Management)

#### 提交分析任务
*   **URL**: `/api/task/submit`
*   **Method**: `POST`
*   **Content-Type**: `application/json`
*   **Request Body**:
    ```json
    {
      "targetJar": "/path/to/application.jar",  // 目标 Jar 包绝对路径
      "sourcePath": "/path/to/source/code",     // 源码根目录绝对路径
      "packageFilter": "com.example.-"          // (可选) 包名过滤器
    }
    ```
*   **Response**:
    ```json
    {
      "code": 200,
      "msg": "success",
      "data": 12345 // 任务 ID
    }
    ```

#### 查询任务详情
*   **URL**: `/api/task/{taskId}`
*   **Method**: `GET`
*   **Response**:
    ```json
    {
      "code": 200,
      "data": {
        "id": 12345,
        "status": "COMPLETED", // SUBMITTED, WAITING_ANALYSIS, ANALYZING, WAITING_LLM, JUDGING, COMPLETED, FAILED, CANCELLED
        "toolName": "SpotBugs",
        "llmModel": "DeepSeek",
        "resultSummary": "分析完成...",
        "createdTime": "2026-02-20T10:00:00",
        "updatedTime": "2026-02-20T10:05:00"
      }
    }
    ```

#### 查询任务列表
*   **URL**: `/api/task/list`
*   **Method**: `GET`
*   **Response**:
    ```json
    {
      "code": 200,
      "data": [ ... ] // 任务对象列表
    }
    ```

#### 取消任务
*   **URL**: `/api/task/cancel/{taskId}`
*   **Method**: `POST`
*   **Response**: `{"code": 200, "msg": "success"}`

#### 删除任务
*   **URL**: `/api/task/{taskId}`
*   **Method**: `DELETE`
*   **Response**: `{"code": 200, "msg": "success"}`

### 4.2 知识库管理 (Knowledge Base)

#### 获取知识列表
*   **URL**: `/api/knowledge/list`
*   **Method**: `GET`
*   **Response**:
    ```json
    {
      "code": 200,
      "data": [
        {
          "id": 1,
          "title": "SQL Injection Fix",
          "content": "Use PreparedStatement..."
        }
      ]
    }
    ```

#### 添加知识
*   **URL**: `/api/knowledge/add`
*   **Method**: `POST`
*   **Request Body**:
    ```json
    {
      "title": "知识标题",
      "content": "知识内容详情..."
    }
    ```

#### 更新知识
*   **URL**: `/api/knowledge/update/{id}`
*   **Method**: `PUT`
*   **Request Body**:
    ```json
    {
      "title": "新标题",
      "content": "新内容..."
    }
    ```

#### 删除知识
*   **URL**: `/api/knowledge/delete/{id}`
*   **Method**: `DELETE`

## 5. 数据字典 (Data Dictionary)

### 任务状态 (TaskStatus)
| 状态码 | 描述 | 含义 |
| :--- | :--- | :--- |
| 0 | SUBMITTED | 已提交，等待调度 |
| 1 | WAITING_ANALYSIS | 等待静态分析 |
| 2 | ANALYZING | 正在进行静态分析 |
| 3 | WAITING_LLM | 等待 LLM 审计 |
| 4 | JUDGING | LLM 正在审计中 |
| 5 | COMPLETED | 完成 |
| -1 | FAILED | 失败 |
| -2 | CANCELLED | 已取消 |

## 6. 开发建议

1.  **轮询策略**: 在任务详情页，建议每 3-5 秒轮询一次 `/api/task/{taskId}` 接口，直到状态变为 `COMPLETED`、`FAILED` 或 `CANCELLED`。
2.  **Markdown 渲染**: LLM 返回的 `aiReasoning` 和 `aiSuggestion` 字段通常包含 Markdown 格式的文本（如代码块），前端需使用 Markdown 组件进行渲染。
3.  **大文件处理**: 目前文件路径为服务器本地路径，前端仅需传递字符串。若未来支持文件上传，需注意大文件上传的进度显示。
