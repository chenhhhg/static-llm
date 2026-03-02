# 静态分析评估框架使用指南

## 简介
本框架用于评估静态分析工具（如 SpotBugs + LLM）在标准数据集（如 OWASP Benchmark）上的表现。它通过对比实际分析结果与预期结果（Ground Truth），自动计算精确度、召回率、F1 分数和 Benchmark 得分。

## 核心组件
- **EvaluationProvider**: 负责加载基准测试用例（如解析 CSV）。
- **EvaluationStrategy**: 负责执行匹配逻辑（如类名匹配、漏洞类别映射）。
- **EvaluationService**: 协调评估过程，生成报告。

## 支持的基准测试
目前支持 **OWASP Benchmark v1.2**。

### CSV 格式要求
OWASP Benchmark 的预期结果文件（`expectedresults-1.2.csv`）应包含以下列（无标题行或跳过标题行）：
1. Test Name (e.g., `BenchmarkTest00001`)
2. Category (e.g., `sql-injection`)
3. Real Vulnerability (e.g., `true` or `false`)
4. CWE ID (e.g., `89`)

## API 使用

### 执行评估
**POST** `/api/evaluation/evaluate`

**参数：**
- `taskId`: 分析任务 ID（必需）
- `benchmarkType`: 基准测试类型，默认为 `OWASP-1.2`
- `benchmarkPath`: 基准测试数据文件（CSV）的绝对路径（必需）

**示例响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "tpCount": 10,
    "fpCount": 2,
    "fnCount": 5,
    "tnCount": 20,
    "precision": 0.83,
    "recall": 0.66,
    "f1Score": 0.73,
    "benchmarkScore": 55.5,
    "categoryStats": {
      "sql-injection": {
        "tp": 5,
        "fp": 1,
        "fn": 2,
        "tn": 10,
        "recall": 0.71
      }
    },
    "details": [...]
  }
}
```

## 扩展指南
要支持新的基准测试：
1. 实现 `EvaluationProvider` 接口以加载新的数据格式。
2. 实现 `EvaluationStrategy` 接口以定义匹配规则。
3. 将实现类注册为 Spring Bean。
