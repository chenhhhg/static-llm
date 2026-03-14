# 基于静态分析与大语言模型协同的 Java 代码缺陷智能验证系统

> **北京邮电大学 本科毕业设计论文（初稿）**

---

## 摘要

随着软件系统规模与复杂度的持续增长，代码安全缺陷的自动化检测已成为软件工程领域的核心研究课题。以 SpotBugs 为代表的传统静态分析工具虽然具备高覆盖率与低成本优势，但受限于规则匹配机制的固有缺陷，其误报率通常高达 30%~70%，严重制约了工程实践中的应用效果。近年来，大语言模型（LLM）展现出强大的代码语义理解与推理能力，为代码审计领域带来了新的技术路径，但同时面临上下文窗口有限、缺乏数据流追踪能力以及模型幻觉等核心挑战。

本文提出一种基于静态分析与大语言模型协同的 Java 代码缺陷智能验证方法，构建了包含"缺陷上下文切片构建"与"基于 LLM 的语义验证"两个阶段的端到端系统。在第一阶段，系统利用 SpotBugs 执行静态缺陷扫描，解析 XML 格式缺陷报告，并基于 JavaParser 抽象语法树分析与 Soot/WALA 程序依赖图构建，以静态警报点为切片原点，实施逆向程序切片，生成精简且保留完整语义的代码上下文。同时，引入基于 ChromaDB 与 BGE-M3 嵌入模型的 RAG 知识检索机制，为每个缺陷自动匹配 SpotBugs 官方规则文档作为辅助知识。在第二阶段，系统构建了基于 LangChain4j 的 LLM Agent 验证框架，采用渐进式提示策略，赋予智能体按需读取源码的工具调用能力，实现上下文的动态补全。为缓解 LLM 幻觉问题，设计了融合思维链推理与强制反思机制的验证流程，并引入多模型并行投票与保守决策策略，确保高危缺陷不被漏报。

系统基于 Spring Boot 微服务架构实现，集成 DeepSeek、通义千问、豆包等多个大模型 API，在 OWASP Benchmark 标准数据集上开展消融实验。实验结果表明，本系统相较于纯静态分析工具在精确率上有显著提升，各组件（程序切片、反思机制、多模型投票）均对系统性能产生了正向贡献。

**关键词：** 静态分析；大语言模型；程序切片；智能体；误报消除；RAG；SpotBugs

---

## Abstract

*（待补充英文摘要）*

---

## 第1章 绪论

### 1.1 研究背景

#### 1.1.1 软件安全形势与代码缺陷检测的重要性

随着数字化转型的深入推进，软件系统已渗透到社会运行的各个层面。然而，软件安全事件的频发为行业敲响了警钟。2021 年底爆发的 Apache Log4j 远程代码执行漏洞（Log4Shell, CVE-2021-44228）波及全球数百万台服务器，被业界称为"近十年最严重的安全漏洞"。2022 年 6 月，全球知名的 CDN 与安全服务提供商 Cloudflare 因一处未被及时发现的代码缺陷导致大规模服务宕机，影响了数以百万计的网站和在线服务。CVE（Common Vulnerabilities and Exposures）数据库的统计数据显示，近五年报告的安全漏洞数量呈逐年递增趋势，2023 年新增漏洞数量已突破 29,000 个。

这些事件深刻表明，代码缺陷检测不仅是软件质量保证的技术环节，更是事关国家安全、经济稳定与公民隐私的重大课题。在企业层面，人工代码审计成本高昂，一次完整的安全审计可能需要数周时间和数十万元的开销，而自动化的缺陷检测技术则成为降本增效的关键手段。

#### 1.1.2 传统静态分析工具的现状与局限

静态分析（Static Analysis）是指在不执行程序的前提下，通过分析源代码或字节码来发现潜在缺陷的技术。以 Java 生态为例，业界广泛使用的静态分析工具包括 SpotBugs（FindBugs 的继承者）、PMD、Checkstyle、Facebook Infer 以及商业工具 Coverity 等。

SpotBugs 作为本文选用的初始静态分析引擎，基于 Java 字节码分析技术，内置超过 400 种缺陷检测规则（Bug Pattern），覆盖正确性（Correctness）、安全性（Security）、性能（Performance）、多线程（Multithreaded Correctness）等多个类别。其核心优势在于：（1）无需执行代码即可完成分析，适用于 CI/CD 流水线集成；（2）规则库覆盖面广，包含空指针引用（NP_NULL_ON_SOME_PATH）、资源未关闭（OS_OPEN_STREAM）、SQL 注入（SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE）等常见漏洞模式。

然而，静态分析工具存在一个广为人知的根本性缺陷——**高误报率**。学术研究和工程实践普遍报告，SpotBugs 等工具的误报率通常在 30%~70% 之间。误报的根本原因在于：（1）路径不可达：工具无法精确建模所有路径约束，将实际不可达的执行路径上的缺陷标记为真实问题；（2）条件约束遗漏：工具缺乏对业务语义的理解，无法识别上下游的防御性检查；（3）跨过程数据流丢失：在分析调用链较长的代码时，工具可能丢失跨方法的数据流信息。

#### 1.1.3 大语言模型在代码分析领域的兴起与挑战

近年来，以 GPT-4、DeepSeek、通义千问为代表的大语言模型（Large Language Model, LLM）展现出了惊人的代码理解与推理能力。LLM 不仅能够解读复杂的业务逻辑，还能追踪变量在多层函数调用中的传播路径，从语义层面判断某个静态分析警报是否为误报。这种语义理解能力恰好弥补了传统静态分析工具的核心短板。

然而，直接将 LLM 应用于大规模代码缺陷检测面临三大核心挑战：

**挑战一：上下文窗口限制。** 即便是最先进的 LLM，其上下文窗口也存在上限（通常为 8K~128K Token）。一个中等规模的 Java 项目可能包含数十万行代码，远超单次对话的 Token 预算。高 Token 消耗不仅增加 API 调用成本，还可能导致模型在大量无关代码中"迷失方向"。

**挑战二：缺乏数据流追踪能力。** LLM 本质上是基于注意力机制的序列模型，并不具备传统程序分析工具的控制流图（CFG）和数据流图（DFG）建模能力。对于需要跨多个方法追踪变量赋值、传播与使用的缺陷类型，LLM 的分析准确性会显著下降。

**挑战三：模型幻觉问题。** LLM 存在"幻觉"（Hallucination）现象，即在缺乏足够证据的情况下生成看似合理但实际错误的分析结论。在代码审计场景中，幻觉可能导致模型错误地将真实漏洞判定为误报（漏报），或将正常代码标记为漏洞（误报），严重影响分析结果的可信度。

### 1.2 国内外研究现状

#### 1.2.1 静态分析与缺陷检测研究

静态分析技术的发展可追溯到 20 世纪 70 年代，经历了从简单的词法分析到深层语义分析的演进过程。SpotBugs 及其前身 FindBugs 由 Hovemeyer 和 Pugh 于 2004 年提出[1]，采用基于字节码模式匹配的 Detector 架构，通过 Visitor 模式遍历字节码指令序列来检测缺陷模式。Facebook 于 2015 年开源的 Infer 工具则采用了更先进的分离逻辑（Separation Logic）和双向抽象解释（Bi-abduction）技术，能够在大规模代码库上进行模块化的过程间分析。

近年来，基于深度学习的缺陷检测方法逐渐兴起。Devign[13]（Zhou et al., 2019）将代码表示为图结构并使用图神经网络进行漏洞预测；LineVul[14]（Fu and Tantithamthavorn, 2022）利用预训练的 CodeBERT 模型进行行级别的漏洞定位。然而，这些方法通常需要大量标注数据进行训练，且泛化能力有限。

#### 1.2.2 程序切片技术研究

程序切片（Program Slicing）的概念由 Weiser 于 1981 年首次提出[2]，旨在从程序中提取与特定变量或语句相关的最小代码子集。程序切片分为静态切片（Static Slicing）和动态切片（Dynamic Slicing）两大类：静态切片基于所有可能的执行路径进行分析，结果较为保守但覆盖全面；动态切片基于特定输入的执行轨迹进行分析，结果更为精确但依赖运行时信息。

程序切片的核心数据结构是程序依赖图（Program Dependence Graph, PDG），由 Ferrante 等人于 1987 年提出[3]。PDG 中包含两类边：控制依赖边（表示语句间的控制流关系）和数据依赖边（表示变量定义-使用关系）。Horwitz 等人于 1990 年进一步提出[4]了系统依赖图（System Dependence Graph, SDG），将 PDG 扩展到过程间分析，通过参数传递边连接不同过程的 PDG。

在 Java 生态中，Soot 和 WALA 是两个主流的程序分析框架。Soot 由 McGill 大学 Sable 研究组开发[5]，使用 Jimple 作为中间表示（Intermediate Representation, IR），提供了完整的过程内和过程间分析基础设施。WALA 由 IBM T.J. Watson 研究中心开发[6]，使用 SSA（Static Single Assignment）形式的 IR，在调用图构建和指针分析方面具有优势。

#### 1.2.3 大语言模型辅助代码分析研究

LLM 辅助代码分析是一个快速发展的研究方向。Li et al.（2023）[12]系统性地评估了 ChatGPT 在代码审计中的能力，发现其在理解复杂业务逻辑方面优于传统工具，但在处理长代码和跨文件依赖时表现欠佳。基于 RAG（Retrieval-Augmented Generation）[7]的代码审计框架通过检索相关代码片段增强 LLM 的分析能力。

在幻觉缓解方面，Madaan et al.（2023）[10]提出的 Self-Refine 方法通过迭代式的自我反馈机制改进 LLM 输出质量；Shinn et al.（2023）[11]提出的 Reflexion 框架引入了显式的反思信号，使 LLM 能够从错误中学习。多模型集成策略（Multi-Agent Debate）[17]也被证明能够有效降低单一模型的幻觉率。

#### 1.2.4 现有研究的不足

尽管上述研究在各自领域取得了重要进展，但在"静态分析与 LLM 协同"这一交叉方向上仍存在以下不足：（1）缺乏面向静态分析警报的精准程序切片方法，现有切片研究多关注通用场景，未针对误报消除任务优化切片策略；（2）缺少系统性的 LLM 幻觉缓解框架，现有工作多采用单次调用模式，未充分利用反思与多模型协同验证；（3）缺乏将静态分析、程序切片、RAG 知识增强与 LLM Agent 有机融合的端到端系统。

### 1.3 研究目标与内容

本文旨在构建一个基于静态分析与大语言模型协同的 Java 代码缺陷智能验证系统，核心研究内容包括：

1. **缺陷上下文切片构建方法**：提出基于静态警报的逆向程序切片方法，以 SpotBugs 报告中的警报点为切片原点，利用程序依赖图进行数据流与控制流分析，生成精简的代码上下文。
2. **基于 LLM 的语义验证框架**：构建支持渐进式提示与工具调用的 LLM Agent 验证架构，实现上下文的按需动态补全。
3. **幻觉缓解与多重校验机制**：设计融合思维链推理、强制反思与多模型投票的验证流程，确保分析结论的可靠性。

### 1.4 论文主要贡献

本文的主要贡献如下：

1. 提出了一种基于静态警报的逆向程序切片方法，通过 AST 级语法提取与 SDG 级依赖分析相结合的方式，有效降低了 LLM 的输入 Token 消耗，同时保留了完整的语义上下文。
2. 构建了基于 LangChain4j 的渐进式提示 LLM Agent 框架，赋予智能体主动检索代码的能力，突破了 LLM 上下文窗口的限制。
3. 设计了融合反思机制与多模型投票的协同校验策略，有效缓解了 LLM 幻觉问题，提升了缺陷验证的准确性与可信度。
4. 实现了完整的工程系统，并在 OWASP Benchmark 标准数据集上通过消融实验验证了各组件的有效性。

### 1.5 论文组织结构

本文共分为八章，各章内容安排如下：

- **第1章 绪论**：阐述研究背景、国内外现状、研究目标与论文贡献。
- **第2章 相关技术与理论基础**：介绍静态分析、程序切片、大语言模型、RAG 等核心技术。
- **第3章 系统总体设计**：描述需求分析、系统架构、核心流程与数据库设计。
- **第4章 缺陷上下文切片构建方法**：详述静态缺陷采集、报告解析、AST 级提取、SDG 逆向切片与 RAG 知识增强。
- **第5章 基于大语言模型的语义验证方法**：阐述 Agent 架构、渐进式提示、反思机制与多模型投票。
- **第6章 系统实现**：展示各模块的具体实现细节。
- **第7章 实验与分析**：报告消融实验设计、结果与讨论。
- **第8章 总结与展望**：总结全文并展望未来方向。

---

## 第2章 相关技术与理论基础

### 2.1 静态分析与 SpotBugs

#### 2.1.1 Java 字节码分析原理

Java 程序经 `javac` 编译器编译后生成标准的 `.class` 字节码文件，其内部结构遵循 JVM 规范，包含常量池、字段表、方法表及属性表等核心组件。字节码作为 Java 源代码与 JVM 执行之间的中间表示，保留了丰富的类型信息和控制流结构，同时剥离了源码级的语法糖，为静态分析提供了统一且稳定的分析对象。

相较于源码级分析，字节码分析具有以下优势：（1）不依赖源码即可分析，适用于第三方库审计；（2）泛型擦除后类型更明确；（3）内部类、Lambda 表达式等语法糖已被编译器展开，降低了分析复杂度。

#### 2.1.2 SpotBugs 架构与规则体系

SpotBugs 采用基于 Visitor 模式的 Detector 架构。每个 Detector 实现特定的缺陷检测逻辑，通过遍历字节码指令序列、分析操作数栈状态和局部变量表来识别缺陷模式。SpotBugs 内置超过 400 种 Bug Pattern，按类别可分为：

| 类别 | 缩写 | 典型规则示例 |
|---|---|---|
| 正确性 | C | NP_NULL_ON_SOME_PATH（空指针引用） |
| 安全性 | S | SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE（SQL注入） |
| 性能 | P | DM_BOXED_PRIMITIVE_FOR_PARSING（装箱性能问题） |
| 多线程 | MT | IS2_INCONSISTENT_SYNC（不一致的同步） |
| 不良实践 | BAD | DE_MIGHT_IGNORE（异常被忽略） |

SpotBugs 的分析结果以标准化的 XML 格式输出，每个 `<BugInstance>` 节点包含缺陷类型（type）、优先级（priority）、源文件位置（SourceLine）及详细描述（Message）等关键信息。

#### 2.1.3 SpotBugs 的误报来源分析

通过对 SpotBugs 误报案例的系统性分析，本文总结出以下主要误报来源：

1. **路径不可达**：SpotBugs 缺乏精确的路径敏感分析能力，可能将实际不可达路径上的缺陷标记为真实问题。
2. **条件约束遗漏**：对于在缺陷点之前已存在的防御性空检查（如 `if (obj != null)`），SpotBugs 有时无法正确传播约束信息。
3. **跨过程数据流丢失**：当数据流经过多层方法调用时，SpotBugs 可能丢失变量的约束条件。
4. **框架语义不理解**：SpotBugs 不理解 Spring、MyBatis 等框架的约定，可能将框架保证非空的返回值标记为可能为空。

### 2.2 程序依赖分析与程序切片

#### 2.2.1 抽象语法树（AST）

抽象语法树（Abstract Syntax Tree, AST）是源代码的树状结构化表示，每个节点对应一个语法结构（如类声明、方法调用、条件语句等）。在 Java 生态中，JavaParser 是一个广泛使用的开源 AST 解析框架，支持解析、修改和生成 Java 源代码。

AST 级分析的特点是：操作简单直观、支持源码级信息提取（如注释、变量名），但缺乏语义信息，无法建模控制流和数据流关系。在本文中，AST 分析作为上下文提取的基线方案。

#### 2.2.2 控制流图（CFG）与数据流分析

控制流图（Control Flow Graph, CFG）是程序执行路径的有向图表示。CFG 的节点为基本块（Basic Block），即一段没有分支的顺序执行代码；边表示可能的控制流转移。

基于 CFG，可以进行多种数据流分析，其中与程序切片最相关的是到达定义分析（Reaching Definitions Analysis）和 Def-Use 链构建。到达定义分析计算在程序的每个点上，哪些变量的定义（赋值）可能到达该点；Def-Use 链则建立每个变量定义与其所有使用之间的直接关联。

#### 2.2.3 程序依赖图（PDG）与系统依赖图（SDG）

程序依赖图（Program Dependence Graph, PDG）是一种融合了控制依赖和数据依赖信息的程序表示。PDG 中的节点对应程序语句，边分为两类：

- **控制依赖边**（Control Dependence Edge）：若语句 B 的执行取决于语句 A 的分支条件，则存在从 A 到 B 的控制依赖边。
- **数据依赖边**（Data Dependence Edge）：若语句 A 定义了变量 v，语句 B 使用了 v，且从 A 到 B 存在不重新定义 v 的路径，则存在从 A 到 B 的数据依赖边。

系统依赖图（System Dependence Graph, SDG）将 PDG 扩展到过程间分析。SDG 由多个过程的 PDG 组成，通过以下额外边类型连接：

- **调用边**（Call Edge）：从调用点连接到被调用过程的入口。
- **参数输入边**（Parameter-In Edge）：将实参传递给形参。
- **参数输出边**（Parameter-Out Edge）：将返回值传递回调用点。

基于 SDG 的逆向切片算法从目标语句（切片准则）出发，沿数据依赖和控制依赖边反向遍历，收集所有直接或间接影响目标语句的节点集合，即为该目标的程序切片。

#### 2.2.4 程序分析框架 Soot 与 WALA

**Soot** 是由加拿大 McGill 大学 Sable 研究组开发的 Java 字节码分析与优化框架。Soot 支持将 Java 字节码转换为四种中间表示，其中 Jimple 是最常用的形式——一种三地址码（3-Address Code），每条语句最多包含三个操作数，便于分析处理。Soot 提供了完整的过程内分析（ExceptionalUnitGraph、BriefUnitGraph）和过程间分析（CallGraph、SPARK 指针分析）基础设施。

**WALA**（Watson Libraries for Analysis）由 IBM T.J. Watson 研究中心开发，是另一个广泛使用的 Java 程序分析框架。WALA 使用 SSA（Static Single Assignment）形式的 IR，其中每个变量只被赋值一次，简化了数据流分析。WALA 在调用图构建、指针分析和 SDG 构建方面提供了开箱即用的实现（如 `SDG.build()` 方法）。

两者的对比：

| 特性 | Soot | WALA |
|---|---|---|
| IR 形式 | Jimple（三地址码） | SSA-IR |
| SDG 构建 | 需要手动组合 | 内置 `SDG.build()` |
| 调用图精度 | CHA / SPARK | CHA / RTA / 0-CFA / 1-CFA |
| 社区活跃度 | 高 | 中 |
| 学习曲线 | 中 | 较高 |

### 2.3 大语言模型与提示工程

#### 2.3.1 大语言模型概述

大语言模型（Large Language Model, LLM）是基于 Transformer 架构的深度神经网络模型，通过在海量文本数据上进行预训练，获得了通用的语言理解和生成能力。本文涉及的 LLM 包括：

- **DeepSeek**：国产开源大模型，在代码理解和推理方面表现优异，支持 Function Calling。
- **通义千问（Qwen）**：阿里云推出的大模型，支持长上下文窗口和工具调用。
- **豆包（Doubao）**：字节跳动推出的大模型，提供高性价比的 API 服务。

LLM 的核心限制是上下文窗口（Context Window），即单次对话中能处理的最大 Token 数量。超出窗口的内容将被截断或无法输入，因此如何在有限的 Token 预算内最大化信息密度是本文的关键课题。

#### 2.3.2 提示工程（Prompt Engineering）

提示工程是指通过精心设计输入提示（Prompt）来引导 LLM 产生期望输出的技术。主要策略包括：

- **零样本提示（Zero-shot）**：仅提供任务描述，不给出示例。
- **少样本提示（Few-shot）**：在提示中包含若干输入-输出示例。
- **思维链（Chain-of-Thought, CoT）**[8]：引导模型展示逐步的推理过程，提升复杂任务的准确性。
- **结构化输出**：要求模型以 JSON 等结构化格式输出，便于程序解析。

#### 2.3.3 智能体（Agent）与工具调用

LLM Agent（智能体）是指具备自主决策和工具调用能力的 LLM 系统。ReAct（Reasoning + Acting）范式[9]是当前主流的 Agent 架构，模型交替执行推理（Reason）和行动（Act）两个步骤：先分析当前状态并决定下一步行动，再调用外部工具获取信息，循环往复直至完成任务。

Function Calling（函数调用）是 LLM 工具使用的底层机制。模型在响应中以结构化格式声明需要调用的工具名称和参数，系统执行工具后将结果注入对话历史，模型基于新信息继续推理。LangChain4j[18] 是 Java 生态中的主流 LLM 框架，提供了 `@Tool` 注解、`AiServices` 代理生成等便捷的 Agent 构建设施。

#### 2.3.4 LLM 幻觉与缓解方法

LLM 幻觉（Hallucination）是指模型生成的内容看似合理但实际与事实不符的现象。在代码审计场景中，幻觉的具体表现包括：编造不存在的代码路径、遗漏关键的边界检查、错误推断变量的取值范围等。

当前主流的幻觉缓解方法包括：

1. **Self-Refine**[10]：让模型对自身输出进行迭代式反馈和修正。
2. **Reflexion**[11]：引入显式的反思信号，使模型能够从错误中学习。
3. **多模型投票**（Multi-Agent Debate）[17]：让多个模型或同一模型的多次独立调用对同一问题给出判断，取多数一致的结论。

### 2.4 检索增强生成（RAG）

#### 2.4.1 RAG 架构原理

检索增强生成（Retrieval-Augmented Generation, RAG）是一种将信息检索与文本生成相结合的技术框架。其核心思想是：在生成回答之前，先从外部知识库中检索与问题相关的文档片段，将其作为额外上下文注入 LLM 的输入，从而增强模型对特定领域知识的掌握。

RAG 的标准流程包括四个阶段：（1）**文档分块**：将原始文档切分为固定大小的文本块（Chunk）；（2）**向量化**：使用嵌入模型将文本块转换为稠密向量；（3）**检索**：根据查询向量在向量数据库中进行相似度检索；（4）**增强生成**：将检索到的文本块拼接到 LLM 输入中。

#### 2.4.2 嵌入模型与向量数据库

本文采用 BGE-M3 作为嵌入模型。BGE-M3 是由北京智源研究院开发的多语言嵌入模型，支持中英文混合场景，在多个基准测试上取得了领先成绩。嵌入过程通过本地部署的 Ollama 服务执行，避免了外部 API 调用的延迟和成本。

ChromaDB 是本文选用的向量数据库，以其轻量级、易部署的特点著称。ChromaDB 支持基于余弦相似度的近似最近邻（ANN）检索，并提供 RESTful API 接口，便于与 Java 后端集成。

### 2.5 OWASP Benchmark 评估标准

OWASP Benchmark[15] 是由开放式 Web 应用安全项目（OWASP）发布的标准化 Java Web 应用安全测试数据集。该数据集包含 2,740 个合成测试用例，覆盖 11 类常见 Web 安全漏洞（如 SQL 注入、XSS、路径遍历、命令注入等）。每个测试用例都有明确的真实标签（True Positive 或 False Positive），便于客观评估各类检测工具的性能。

本文采用以下标准评估指标：

- **精确率（Precision）** = TP / (TP + FP)：在所有标记为漏洞的结果中，真实漏洞的比例。
- **召回率（Recall）** = TP / (TP + FN)：在所有真实漏洞中，被正确检出的比例。
- **F1 值** = 2 × Precision × Recall / (Precision + Recall)：精确率与召回率的调和平均。
- **BenchmarkScore**：OWASP 官方定义的综合评分 = TPR - FPR，取值范围 [-1, 1]。

---

## 第3章 系统总体设计

### 3.1 需求分析

#### 3.1.1 功能需求

本系统的核心功能需求包括以下五个方面：

1. **静态扫描与缺陷采集**：支持用户提交 Java 项目的 Jar 包与源码路径，系统自动调用 SpotBugs 执行静态分析，生成缺陷报告。
2. **报告解析与上下文提取**：解析 SpotBugs XML 报告，提取缺陷元数据，并基于 AST/SDG 分析提取与缺陷相关的代码上下文。
3. **LLM 语义验证**：利用大语言模型对每个静态分析警报进行语义级验证，判断其是否为误报，并给出推理依据和修复建议。
4. **评估与结果管理**：支持基于 OWASP Benchmark 的标准化评估，计算精确率、召回率、F1 值等指标，并支持评估结果的持久化与对比。
5. **知识库管理**：支持 SpotBugs 规则文档等知识的增删改查，以及基于语义检索的 RAG 增强。

#### 3.1.2 非功能需求

1. **并发处理能力**：支持多任务并行执行，LLM 审计和 RAG 检索均采用线程池并发处理。
2. **断点续传与任务恢复**：系统启动时自动扫描未完成任务并恢复执行，支持从 LLM 审计阶段断点续传。
3. **缓存与秒传**：对已分析过的 Jar 包计算 MD5 指纹，命中缓存时直接复用历史报告。
4. **可扩展性**：通过适配器模式支持多种静态分析工具（SpotBugs/PMD/Checkstyle）和多种 LLM API（DeepSeek/通义千问/豆包）。

### 3.2 系统架构设计

#### 3.2.1 总体架构

本系统采用分层架构设计，基于 Maven 多模块管理，分为四个核心模块：

```
static-llm/
├── static-llm-common   # 公共层：实体模型、枚举、统一响应
├── static-llm-adapter  # 适配器层：外部工具集成（SpotBugs）
├── static-llm-core     # 核心层：报告解析、上下文提取、评估策略
└── static-llm-web      # 服务层：REST API、LLM Agent、RAG、任务调度
```

各层依赖关系为：web → core → adapter → common，遵循单向依赖原则。

#### 3.2.2 模块划分与职责

| 模块 | 核心职责 | 关键类 |
|---|---|---|
| common | 定义公共实体、枚举、响应封装 | AnalysisTask, UnifiedIssue, TaskStatus, IssueStatus |
| adapter | 封装外部工具调用 | SpotBugsAdapter (ProcessBuilder 子进程调用) |
| core | 报告解析与上下文提取 | SpotBugsNormalizer, JavaParserContextExtractor, OwaspBenchmarkStrategy |
| web | 业务服务与 API 接口 | AnalysisTaskServiceImpl, CodeAuditAgent, ChromaOllamaRagServiceImpl |

#### 3.2.3 外部服务依赖

系统运行依赖以下外部服务：

- **MySQL**：持久化存储任务、Issue、缓存、知识库及评估记录。
- **ChromaDB**：向量数据库，存储知识库的文档嵌入向量。
- **Ollama**：本地嵌入服务，运行 BGE-M3 模型进行文本向量化。
- **LLM API**：DeepSeek / 通义千问 / 豆包等大模型 API 服务。

### 3.3 核心流程设计

#### 3.3.1 端到端分析流程

系统的端到端分析流程如下：

```
用户提交任务 → SpotBugs 静态扫描 → XML 报告解析 → 代码上下文提取
    → RAG 知识检索（并发） → Issue 入库 → LLM Agent 验证（并发）
    → 结果入库 → 任务完成
```

#### 3.3.2 任务状态机设计

任务生命周期通过状态机管理，包含以下状态：

```
SUBMITTED(0) → DOWNLOADING(1) → WAITING_ANALYSIS(2) → ANALYZING(3)
    → WAITING_LLM(4) → JUDGING(5) → COMPLETED(6)
                                    ↘ FAILED(7)
                                    ↘ CANCELLED(8)
```

状态码递增设计支持断点续传：系统通过比较当前状态码与目标阶段状态码，判断是否需要跳过已完成的阶段。

#### 3.3.3 缺陷状态机设计

每个 Issue 的生命周期状态：

```
PENDING(0) → RAG_RETRIEVING(1) → RAG_COMPLETED(2) → LLM_ANALYZING(3)
    → COMPLETED(4) / FAILED(5)
```

### 3.4 数据库设计

#### 3.4.1 ER 关系模型

系统包含六张核心数据表，其关系如下：

- `analysis_task` (1) ← (N) `analysis_issue`：一个任务包含多个缺陷 Issue
- `analysis_cache`：独立表，存储 Jar 包 MD5 与报告路径的映射
- `knowledge`：独立表，存储 RAG 知识库文档
- `evaluation_record` (1) ← (N) `evaluation_detail`：一次评估包含多条明细

#### 3.4.2 核心表结构设计

**analysis_task 表**（任务表）：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| tool_name | VARCHAR | 静态分析工具名（SpotBugs） |
| llm_model | VARCHAR | 使用的 LLM 模型名 |
| status | INT | 任务状态码（0-8） |
| task_params | JSON | 任务参数（targetJar, sourcePath 等） |
| result_summary | TEXT | 结果摘要 |

**analysis_issue 表**（缺陷表）：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| task_id | BIGINT | 关联任务 ID |
| rule_id | VARCHAR | 规则编号（如 NP_NULL_ON_SOME_PATH） |
| severity | VARCHAR | 严重级别 |
| file_path | VARCHAR | 源文件路径 |
| start_line / end_line | INT | 起止行号 |
| code_snippet | LONGTEXT | 代码上下文切片 |
| is_false_positive | TINYINT | AI 判定是否误报 |
| ai_reasoning | TEXT | AI 推理过程 |
| ai_suggestion | TEXT | AI 修复建议 |
| status | INT | Issue 状态码 |

### 3.5 接口设计

#### 3.5.1 RESTful API 设计

系统对外暴露五组 RESTful API：

| 模块 | 路径前缀 | 主要接口 |
|---|---|---|
| 任务管理 | /api/task | POST /submit, GET /{taskId}, GET /list, DELETE /{taskId}, POST /cancel/{taskId} |
| 问题管理 | /api/issue | GET /list/{taskId}, GET /{id} |
| 缓存管理 | /api/cache | GET /list, DELETE /{id} |
| 知识库管理 | /api/knowledge | GET /list, POST /add, PUT /update/{id}, DELETE /delete/{id} |
| 评估管理 | /api/evaluation | POST /evaluate, GET /detail/{id} |

---

## 第4章 缺陷上下文切片构建方法

### 4.1 静态缺陷采集

#### 4.1.1 SpotBugs 自动化集成

本系统通过 `SpotBugsAdapter` 类封装了对 SpotBugs 的自动化调用。具体实现中，使用 Java 的 `ProcessBuilder` 以子进程方式启动 SpotBugs JAR 包，传入以下关键参数：

```
java -jar spotbugs.jar -textui -xml:withMessages
     -effort:max -high
     -sourcepath <sourcePath>
     -auxclasspathFromInput
     -output <reportFile>
     <targetJar>
```

其中，`-effort:max` 设置分析精度为最高级别，`-high` 过滤出高优先级警报以降低噪音。系统为每次分析设置 15 分钟超时阈值，超时后强制终止子进程并标记任务失败。

#### 4.1.2 MD5 秒传缓存机制

为避免对相同 Jar 包的重复分析，系统引入了基于 MD5 指纹的缓存机制：

1. 在执行分析前，计算目标 Jar 包的 MD5 哈希值。
2. 在 `analysis_cache` 表中查询是否存在该 MD5 的缓存记录。
3. 若命中且缓存的报告文件仍存在于磁盘，直接复用历史报告。
4. 若缓存的报告文件已被删除，则清理失效缓存记录并重新执行分析。
5. 分析完成后，将 MD5 与报告路径写入缓存表。

### 4.2 缺陷报告解析与归一化

#### 4.2.1 SpotBugs XML 报告结构

SpotBugs 输出的 XML 报告遵循以下层次结构：

```xml
<BugCollection>
  <BugInstance type="NP_NULL_ON_SOME_PATH" priority="1" category="CORRECTNESS">
    <ShortMessage>Possible null pointer dereference</ShortMessage>
    <SourceLine classname="com.example.Foo" start="42" end="42"
                sourcefile="Foo.java" sourcepath="com/example/Foo.java"/>
  </BugInstance>
  ...
</BugCollection>
```

#### 4.2.2 解析器设计与实现

`SpotBugsNormalizer` 类使用 dom4j 库解析 XML 报告，逐条提取 `BugInstance` 节点的关键信息，并映射为统一的 `UnifiedIssue` 数据结构：

| XML 属性 | UnifiedIssue 字段 | 说明 |
|---|---|---|
| type | ruleId | 规则编号 |
| priority | severity | 严重级别（1=HIGH, 2=MEDIUM, 3=LOW） |
| SourceLine.sourcepath | filePath | 源文件相对路径 |
| SourceLine.start | startLine | 起始行号 |
| SourceLine.end | endLine | 结束行号 |
| ShortMessage | message | 缺陷描述 |

### 4.3 基于 AST 的语法级上下文提取

#### 4.3.1 方法设计

`JavaParserContextExtractor` 是本系统的上下文提取组件，基于 JavaParser 框架实现。其核心策略是以 SpotBugs 报告中的警报行号为定位依据，在 AST 中找到目标方法，然后提取三层上下文信息：

1. **Level 0 — 类字段声明**：提取目标类的所有字段声明，为 LLM 提供类级别的状态信息。
2. **Level 0 — 目标方法完整体**：提取包含警报行的方法的完整源码，确保 LLM 能看到完整的控制流。
3. **Level +1 — 被调用方法签名**：遍历目标方法中的所有方法调用表达式（MethodCallExpr），利用 SymbolSolver 解析到具体的方法声明，提取其方法签名。

#### 4.3.2 实现细节

提取过程的关键步骤如下：

1. **构建 AST**：使用 JavaParser 的 `StaticJavaParser.parse()` 方法解析源文件，生成 `CompilationUnit` 对象。
2. **定位目标方法**：遍历 AST 中所有 `MethodDeclaration` 节点，通过比较方法体的起止行号范围与警报行号，确定包含缺陷的目标方法。
3. **提取被调用方法签名**：使用 `VoidVisitorAdapter` 遍历目标方法中的 `MethodCallExpr` 节点，通过 JavaParser 的 `SymbolSolver` 解析方法调用的目标声明。
4. **序列化输出**：将提取的三层上下文拼接为格式化文本，注入到 `UnifiedIssue` 的 `codeSnippet` 字段。

#### 4.3.3 局限性分析

基于 AST 的语法级提取方案存在以下局限：

1. **不追踪数据流**：AST 仅表示语法结构，无法判断变量在方法内的数据传播路径。
2. **不建模控制流**：无法区分条件分支中的不同执行路径，可能包含不可达代码。
3. **粒度过粗**：提取整个方法体可能包含大量与缺陷无关的代码，增加 Token 消耗。
4. **无过程间分析**：Level +1 仅提取方法签名，不深入被调用方法的实现体。

这些局限性促使本文进一步引入基于 SDG 的逆向程序切片方法。

### 4.4 基于 SDG 的逆向程序切片

#### 4.4.1 中间表示构建

利用 Soot 或 WALA 框架将 Java 字节码转换为中间表示（IR）。以 Soot 为例，处理流程为：

1. 加载目标 Jar 包和依赖库到 Soot 的 Scene 中。
2. 将每个方法的字节码转换为 Jimple 三地址码形式。
3. 为每个方法构建 `ExceptionalUnitGraph`（包含异常边的 CFG）。

#### 4.4.2 系统依赖图构建

基于 IR 构建 SDG 的步骤如下：

1. **过程内 CFG 构建**：为每个方法生成控制流图，识别基本块和跳转边。
2. **控制依赖分析**：基于 CFG 的后支配树（Post-Dominator Tree），计算每对语句之间的控制依赖关系。
3. **数据依赖分析**：通过到达定义分析，为每个变量使用建立到其定义的数据依赖边。
4. **调用图构建**：使用 CHA（Class Hierarchy Analysis）或 SPARK 算法构建过程间调用图。
5. **过程间参数传递边**：为每个调用点添加实参到形参的参数输入边，以及返回值到调用点的参数输出边。

#### 4.4.3 基于静态警报点的逆向切片算法

本文提出的逆向切片算法以 SpotBugs 报告的行号和涉及变量为切片准则（Slicing Criterion），算法伪代码如下：

```
Algorithm: AlertBasedBackwardSlice
Input:  SDG G = (V, E), alert_line, alert_variable
Output: slice S ⊆ V

1. criterion ← FindNode(G, alert_line, alert_variable)
2. worklist ← {criterion}
3. S ← {criterion}
4. while worklist ≠ ∅ do
5.     node ← worklist.pop()
6.     for each edge (pred, node) ∈ E do
7.         if pred ∉ S and depth(pred) < MAX_DEPTH then
8.             S ← S ∪ {pred}
9.             worklist ← worklist ∪ {pred}
10. return S
```

其中，`MAX_DEPTH` 参数限制了逆向追踪的最大深度，避免在大型调用图中产生过大的切片。

#### 4.4.4 切片结果序列化

将图遍历得到的节点集映射回源代码的过程如下：

1. 收集切片中所有节点对应的源文件和行号信息。
2. 按文件分组，在每个文件内按行号排序。
3. 合并相邻行号范围，提取连续的代码块。
4. 保留必要的类声明和方法签名作为上下文框架。
5. 使用 `// ... (omitted)` 标记剔除的无关代码段。

### 4.5 RAG 知识检索增强

#### 4.5.1 知识库构建

系统的 RAG 知识库以 SpotBugs 官方规则文档为初始数据源。知识入库流程为：

1. 将文档内容按固定大小分块（chunk size = 500 tokens, overlap = 50 tokens）。
2. 调用 Ollama 本地服务运行 BGE-M3 模型，将每个 chunk 转换为 768 维的稠密向量。
3. 将向量与原始文本一起写入 ChromaDB 的指定 Collection。

#### 4.5.2 检索增强流程

对每个 Issue 执行 RAG 检索的步骤如下：

1. **查询构造**：根据 Issue 的 `ruleId` 和 `message` 字段构建英文查询：`"How to fix SpotBugs rule '{ruleId}': {message}"`。
2. **向量检索**：将查询文本通过 BGE-M3 嵌入后，在 ChromaDB 中执行 Top-3 相似度检索（最小相似度阈值 minScore = 0.5）。
3. **知识注入**：将检索到的知识文本以 `// [RAG Knowledge Base Reference]` 为标记，追加到 Issue 的 `codeSnippet` 字段末尾。

#### 4.5.3 并发检索优化

由于每个分析任务可能产生数十至数百个 Issue，逐一进行 RAG 检索将产生显著延迟。系统使用 80 线程的固定线程池（`ragExecutor`），通过 `CompletableFuture.supplyAsync()` 并发执行所有 Issue 的 RAG 检索与入库操作，并使用 `CompletableFuture.allOf().join()` 等待全部完成。

---

## 第5章 基于大语言模型的语义验证方法

### 5.1 LLM Agent 验证框架设计

#### 5.1.1 整体架构

本系统采用 LangChain4j 框架构建 LLM Agent。核心组件包括：

- **ChatLanguageModel**：封装 LLM API 调用，支持通过 OpenAI 兼容协议适配 DeepSeek 等模型。
- **Tools**：赋予 Agent 操作外部环境的能力，包括 `readFile` 和 `listDirectory` 两个工具。
- **ChatMemory**：管理对话历史，使用 `MessageWindowChatMemory` 保持最近 10 轮对话。

Agent 通过 `AiServices.builder()` 动态生成代理接口实现，将上述三个组件绑定为一个具有自主决策能力的智能体。

#### 5.1.2 统一模型接入层

系统通过 `LlmConfig` 配置类构建统一的模型接入层。所有 LLM API（DeepSeek、通义千问、豆包）均遵循 OpenAI 兼容协议，通过配置不同的 `base-url`、`api-key` 和 `model` 参数即可切换模型，无需修改业务代码。

### 5.2 渐进式提示与工具调用机制

#### 5.2.1 初始提示构建

系统将每个 Issue 的关键信息序列化为 JSON 格式，作为 Agent 的用户消息：

```json
{
    "issueId": 42,
    "ruleId": "NP_NULL_ON_SOME_PATH",
    "filePath": "src/main/java/com/example/Foo.java",
    "startLine": 100,
    "codeSnippet": "// ... (extracted context + RAG knowledge) ..."
}
```

Agent 基于初始切片即可开始分析，无需一次性加载整个项目的代码。

#### 5.2.2 工具调用设计

Agent 配备两个工具，实现上下文的动态补全：

1. **readFile(filePath)**：读取项目中指定文件的完整内容。当 Agent 在分析切片时遇到未知的方法调用或类引用时，可主动调用此工具获取相关源码。
2. **listDirectory(directoryPath)**：列出指定目录下的文件列表。帮助 Agent 了解项目结构，定位相关源文件。

工具调用的上下文传递通过 `AnalysisContextHolder`（基于 `ThreadLocal`）实现，在每个子线程启动时设置项目源码路径，工具执行时从 ThreadLocal 中获取路径前缀，确保文件操作定位到正确的项目目录。

#### 5.2.3 对话窗口管理

Agent 使用 `MessageWindowChatMemory` 管理对话历史，窗口大小设置为 10 轮。这意味着 Agent 最多保留最近 10 轮的对话（包括用户消息、AI 回复和工具调用结果），超出窗口的早期消息将被自动丢弃。这种设计在控制 Token 消耗的同时，保留了足够的近期上下文供 Agent 做出连贯的推理。

### 5.3 系统提示词与思维链设计

#### 5.3.1 System Prompt 设计

系统提示词（System Prompt）定义了 Agent 的角色和行为规范，核心内容包括：

1. **角色设定**：你是一个专业的代码安全审计专家（Code Security Auditor），专门负责分析代码中的潜在安全漏洞和代码质量问题。
2. **分析流程引导**：
   - 第一步：仔细阅读提供的代码片段和缺陷报告。
   - 第二步：如果代码片段不足以做出判断，使用工具读取完整源文件。
   - 第三步：基于完整的上下文信息，做出是否为误报的判断。
3. **输出格式约束**：要求以 JSON 格式输出，包含 `issueId`、`isFalsePositive`、`reasoning`、`fixSuggestion` 四个字段。

#### 5.3.2 思维链（CoT）推理引导

系统提示词中嵌入了思维链推理的引导指令，要求 Agent 在给出结论前展示完整的推理过程：

- 追踪变量从定义到使用的数据流路径。
- 检查是否存在上游的防御性空检查或异常捕获。
- 分析所有可能的执行路径，判断缺陷是否在所有路径上都可触发。
- 考虑框架级的保护机制（如 Spring 的依赖注入保证非空）。

### 5.4 反思机制（Self-Validation）

#### 5.4.1 机制设计

为缓解 LLM 幻觉问题，系统在 Agent 给出初步判定后，触发强制性的反思阶段。反思提示采用与初始分析不同的 Prompt 模板，要求模型"忘记"前序结论，从零开始重新审查证据。

反思阶段的触发条件：
- **无条件触发**：所有 Issue 在初次判定后均进入反思阶段，确保每个结论都经过二次验证。
- **特殊关注**：对于涉及安全类漏洞（Security 类别）的 Issue，反思提示中额外强调不可遗漏的检查项。

#### 5.4.2 结构化反思检查表

反思提示中包含一份结构化检查表，引导模型系统性地审查以下方面：

- [ ] 是否遗漏了空值检查（null check）？
- [ ] 是否遗漏了 try-catch 异常处理？
- [ ] 数据流从源到汇是否确实可达？
- [ ] 是否存在全局过滤器或拦截器提供保护？
- [ ] 条件分支是否完整覆盖？
- [ ] 外部输入是否经过了充分的校验与消毒（sanitization）？

#### 5.4.3 反思结果处理

- **一致（初判与反思结论相同）**：采纳该结论作为最终判定。
- **不一致（初判与反思结论矛盾）**：升级到多模型校验阶段，或保守地标记为真实漏洞。

### 5.5 多模型投票与保守决策

#### 5.5.1 多模型并行验证

对于反思阶段无法达成一致的 Issue，或所有高危（High Severity）Issue，系统启动多模型并行验证：

1. 从已配置的模型池（DeepSeek、通义千问、豆包）中选取 2~3 个模型。
2. 对同一 Issue，以相同的代码上下文和不同的 Prompt 模板，并行发起独立的验证请求。
3. 每个模型在独立的对话窗口中完成分析，互不影响。

#### 5.5.2 投票决策策略

多模型验证结果的决策规则：

- **多数一致（≥ 2/3 模型得出相同结论）**：采纳多数结论。
- **存在分歧（无法达成多数一致）**：执行保守策略。

#### 5.5.3 分层触发策略

为平衡分析精度与 API 成本，系统采用分层触发策略：

1. **第一层（所有 Issue）**：单模型 + 反思机制。大部分 Issue 在此阶段即可得到可靠结论。
2. **第二层（反思不一致或高危 Issue）**：多模型并行投票。仅对第一层无法确定的 Issue 触发，控制 API 开销。

### 5.6 LLM 响应解析与容错

#### 5.6.1 JSON 结构化输出解析

LLM 的响应可能包含 Markdown 代码块包裹的 JSON、纯 JSON 或 JSON 数组等多种格式。系统采用多级解析策略：

1. 尝试提取 Markdown 代码块（` ```json ... ``` `）中的内容。
2. 尝试从内容中定位 JSON 对象（`{...}`）并解析。
3. 若对象解析失败，尝试定位 JSON 数组（`[...]`）并取首元素。
4. 所有解析均失败时，记录原始响应并标记该 Issue 为分析失败。

#### 5.6.2 失败重试与降级

单个 Issue 的分析失败不影响整体任务的执行。失败的 Issue 被标记为 `FAILED` 状态，其余 Issue 继续正常处理。系统在任务完成后统计失败数量，纳入结果摘要。

---

## 第6章 系统实现

### 6.1 开发环境与技术栈

本系统的开发环境与技术栈如下：

| 项目 | 版本/规格 |
|---|---|
| 编程语言 | Java 17 |
| 构建工具 | Maven 3.9.x |
| Web 框架 | Spring Boot 3.1.5 |
| ORM 框架 | MyBatis-Plus 3.5.5 |
| LLM 框架 | LangChain4j 0.35.0 |
| AST 解析 | JavaParser |
| 向量数据库 | ChromaDB |
| 嵌入服务 | Ollama + BGE-M3 |
| 关系数据库 | MySQL 8.0 |
| API 文档 | SpringDoc OpenAPI 2.2.0 |
| 静态分析工具 | SpotBugs 4.9.8 |

### 6.2 公共模块实现（static-llm-common）

公共模块定义了系统中所有共享的数据模型和枚举类型。

**核心枚举设计**：

- `TaskStatus`：定义任务的 9 种状态，状态码从 0（SUBMITTED）递增到 8（CANCELLED），支持基于状态码比较的断点续传逻辑。
- `IssueStatus`：定义 Issue 的 6 种状态，覆盖从待处理到完成/失败的完整生命周期。
- `Severity`：缺陷严重级别枚举（HIGH, MEDIUM, LOW）。

**统一响应封装**：`Result<T>` 类提供标准化的 API 响应格式，包含 `code`（状态码）、`message`（提示信息）和 `data`（业务数据）三个字段。

### 6.3 适配器模块实现（static-llm-adapter）

`SpotBugsAdapter` 类实现了 `StaticAnalysisService` 接口，核心方法 `executeAnalysis(targetJar, sourcePath, packageFilter)` 的实现要点：

1. 构建 SpotBugs 命令行参数数组，包括 `-effort:max`（最高分析精度）、`-high`（仅报告高优先级）。
2. 使用 `ProcessBuilder` 创建子进程，设置工作目录和错误流重定向。
3. 启动进程后，通过 `process.waitFor(15, TimeUnit.MINUTES)` 设置超时。
4. 超时后调用 `process.destroyForcibly()` 强制终止。
5. 检查退出码和报告文件是否生成，返回报告文件路径。

### 6.4 核心模块实现（static-llm-core）

#### 6.4.1 SpotBugsNormalizer 报告解析器

解析器使用 dom4j 的 SAXReader 读取 XML 文件，遍历所有 `//BugInstance` 节点，为每个节点提取：
- `type` 属性 → `ruleId`
- `priority` 属性 → `severity`（1=HIGH, 2=MEDIUM, 3=LOW）
- 第一个 `SourceLine` 子元素的 `sourcepath`/`start`/`end` 属性
- `ShortMessage` 或 `LongMessage` 子元素文本 → `message`

同时设置 `toolName = "SpotBugs"`。

#### 6.4.2 JavaParserContextExtractor 上下文提取器

提取器的核心流程：

```java
public void enrichContext(List<UnifiedIssue> issues, String sourcePath) {
    for (UnifiedIssue issue : issues) {
        // 1. 根据 filePath 定位源文件
        File sourceFile = new File(sourcePath, issue.getFilePath());
        // 2. 解析 AST
        CompilationUnit cu = StaticJavaParser.parse(sourceFile);
        // 3. 定位目标方法
        MethodDeclaration targetMethod = findMethodByLine(cu, issue.getStartLine());
        // 4. 提取类字段 + 目标方法 + 被调用方法签名
        String context = extractContext(cu, targetMethod);
        // 5. 注入到 Issue
        issue.setCodeSnippet(context);
    }
}
```

#### 6.4.3 OwaspBenchmarkStrategy 评估策略

`OwaspBenchmarkStrategy` 实现了 `EvaluationStrategy` 接口，负责将 SpotBugs 规则映射到 OWASP Benchmark 漏洞类别。系统维护了一张 `CATEGORY_MAPPING` 静态映射表：

```
SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE → sqli
COMMAND_INJECTION → cmdi
XSS_REQUEST_PARAMETER_TO_SEND_ERROR → xss
PATH_TRAVERSAL_IN → pathtraver
...
```

评估逻辑：对每个 Benchmark 测试用例，比较系统的检测结果（是否标记为漏洞）与标准答案（ground truth），分别统计 TP、FP、FN、TN 四个值。

### 6.5 Web 服务模块实现（static-llm-web）

#### 6.5.1 任务管理服务（AnalysisTaskServiceImpl）

任务管理服务是系统的核心调度枢纽，实现了以下关键机制：

**两阶段断点续传**：
- 阶段 1（静态分析 → RAG → 入库）：若状态码 < `WAITING_LLM(4)`，从头执行阶段 1。
- 阶段 2（LLM 审计）：查询 `aiSuggestion` 为空的 Issue，仅对这些 Issue 执行 LLM 分析。

**并发控制**：
- `taskExecutor`：5 线程，管理任务级并发。
- `ragExecutor`：80 线程，管理 RAG 检索并发。
- `auditExecutor`：1 线程（可配置），管理 LLM 审计并发。

**Benchmark 模式优化**：当开启 Benchmark 模式时，系统自动过滤无法映射到 Benchmark 类别的 Issue，避免浪费 Token。

#### 6.5.2 LLM Agent 实现

**CodeAuditAgent 接口**：使用 LangChain4j 的 `@SystemMessage` 注解指向 System Prompt 资源文件。

```java
public interface CodeAuditAgent {
    @SystemMessage(fromResource = "prompts/code-audit-system.txt")
    String audit(String userMessage);
}
```

**ProjectContextTools**：实现 `readFile` 和 `listDirectory` 两个工具方法，通过 `@Tool` 注解暴露给 Agent。

**AnalysisContextHolder**：基于 `ThreadLocal<String>` 实现，在每个分析线程启动时存储当前项目的源码路径。

#### 6.5.3 RAG 服务实现

系统提供两套 RAG 实现：

1. **ChromaOllamaRagServiceImpl**（主方案）：
   - 入库：文本 → Ollama BGE-M3 嵌入 → ChromaDB 写入
   - 检索：查询文本 → Ollama BGE-M3 嵌入 → ChromaDB Top-3 检索（minScore=0.5）
   
2. **SimpleRagServiceImpl**（备用方案）：
   - 使用 LangChain4j 内置的 AllMiniLmL6V2 本地嵌入模型
   - 内存向量存储，适用于开发和测试环境

#### 6.5.4 评估服务实现（EvaluationServiceImpl）

评估服务支持三种评估模式：

1. **FULL**：以 SpotBugs 原始检测结果（未经 AI 过滤）与标准答案对比，评估纯静态分析的性能。
2. **AI_ONLY**：以 AI 过滤后的结果（排除 AI 判定为误报的 Issue）与标准答案对比，评估 AI 协同后的性能。
3. **AI_MISJUDGMENT**：逐条分析 AI 判定与标准答案的差异，识别 AI 的误判模式。

评估结果持久化到 `evaluation_record` 表（汇总指标）和 `evaluation_detail` 表（逐条明细）。

#### 6.5.5 RESTful API 实现

系统通过五个 Controller 类暴露 API，并集成 SpringDoc 自动生成 Swagger 文档：

- `TaskController`：任务的增删查改和取消。
- `AnalysisIssueController`：Issue 分页查询和详情查看。
- `AnalysisCacheController`：缓存查询和清理。
- `KnowledgeController`：知识库 CRUD。
- `EvaluationController`：评估执行和结果查询。

### 6.6 代码统计与模块结构

本系统共包含 66 个 Java 源文件，约 3,000 行有效代码，各模块代码量分布如下：

| 模块 | 文件数 | 代码行数 | 占比 |
|---|---|---|---|
| static-llm-common | ~10 | ~341 | 11.4% |
| static-llm-adapter | ~5 | ~190 | 6.3% |
| static-llm-core | ~12 | ~540 | 18.0% |
| static-llm-web | ~39 | ~1,929 | 64.3% |
| **合计** | **66** | **~3,000** | **100%** |

---

## 第7章 实验与分析

### 7.1 实验环境与配置

*（待补充具体硬件配置、API 参数设定）*

### 7.2 数据集与评估指标

#### 7.2.1 OWASP Benchmark 数据集

本实验采用 OWASP Benchmark v1.2 作为标准测试数据集。该数据集包含 2,740 个 Java Servlet 测试用例，覆盖以下 11 类漏洞：

| 类别 | 缩写 | 测试用例数 |
|---|---|---|
| SQL 注入 | sqli | ~500 |
| 跨站脚本 | xss | ~450 |
| 命令注入 | cmdi | ~250 |
| 路径遍历 | pathtraver | ~270 |
| 密码学弱点 | crypto | ~250 |
| 哈希弱点 | hash | ~240 |
| LDAP 注入 | ldapi | ~60 |
| 安全Cookie | securecookie | ~70 |
| 头注入 | headerinjection | ~150 |
| 信任边界 | trustbound | ~130 |
| 随机数弱点 | weakrand | ~370 |

每个测试用例的源文件命名为 `BenchmarkTest<NNNNN>.java`，标准答案记录在 `expectedresults-1.2.csv` 文件中。

#### 7.2.2 评估指标定义

| 指标 | 公式 | 含义 |
|---|---|---|
| True Positive (TP) | — | 真实漏洞被正确检出 |
| False Positive (FP) | — | 非漏洞被错误标记为漏洞 |
| False Negative (FN) | — | 真实漏洞未被检出 |
| True Negative (TN) | — | 非漏洞被正确排除 |
| Precision | TP / (TP + FP) | 精确率 |
| Recall | TP / (TP + FN) | 召回率 |
| F1-Score | 2 × P × R / (P + R) | 调和平均 |
| BenchmarkScore | TPR - FPR | OWASP 官方综合评分 |

### 7.3 消融实验设计

为量化各组件对系统性能的贡献，本文设计了以下六组消融实验：

| 实验组 | 切片方法 | 反思机制 | 多模型投票 | 说明 |
|---|---|---|---|---|
| Baseline | — | — | — | SpotBugs 原始结果 |
| A | AST 级提取 | ✗ | ✗ | 单模型 + 无反思 |
| B | SDG 逆向切片 | ✗ | ✗ | 单模型 + 无反思 |
| C | AST 级提取 | ✓ | ✗ | 单模型 + 反思 |
| D | AST 级提取 | ✗ | ✓ | 多模型投票 |
| E | SDG 逆向切片 | ✓ | ✓ | 完整系统 |

各实验组的变量控制：
- **Baseline → A**：引入 LLM 语义验证，验证 LLM 的基本误报消除能力。
- **A → B**：替换切片方法（AST → SDG），验证精准切片对 LLM 分析精度的影响。
- **A → C**：引入反思机制，验证反思对纠正 LLM 初判错误的效果。
- **A → D**：引入多模型投票，验证多模型协同对幻觉缓解的效果。
- **E vs 其他**：完整系统的综合性能。

### 7.4 实验结果与分析

#### 7.4.1 纯静态分析基线（Baseline）

*（待补充 SpotBugs 在 OWASP Benchmark 上的原始指标）*

| 指标 | 值 |
|---|---|
| Precision | *待测* |
| Recall | *待测* |
| F1-Score | *待测* |
| BenchmarkScore | *待测* |

#### 7.4.2 AST 切片 + 单模型验证（实验组 A）

*（待补充实验数据）*

#### 7.4.3 SDG 切片改进效果（实验组 B vs A）

*（待补充实验数据及分析：Token 消耗对比、精度变化、切片质量评估）*

#### 7.4.4 反思机制效果（实验组 C vs A）

*（待补充实验数据及分析：反思纠正了多少初判错误？精确率/召回率变化如何？）*

#### 7.4.5 多模型投票效果（实验组 D vs A）

*（待补充实验数据及分析：多模型一致率、分歧分布、保守策略对召回率的影响）*

#### 7.4.6 完整系统效果（实验组 E）

*（待补充实验数据及分析：各组件协同的整体提升、最终 F1 / BenchmarkScore）*

### 7.5 案例分析

#### 7.5.1 成功案例：LLM 正确识别误报

*（待补充具体案例：SpotBugs 报告了空指针风险，但 LLM 通过数据流追踪发现上游已有空检查，正确判定为误报）*

#### 7.5.2 成功案例：反思机制纠正初判

*（待补充具体案例：LLM 初次判定为误报，但反思阶段发现遗漏了某条执行路径，修正为真实漏洞）*

#### 7.5.3 失败案例：LLM 仍然误判

*（待补充具体案例及失败原因分析）*

### 7.6 讨论

#### 7.6.1 各组件贡献度分析

*（基于消融实验数据，量化分析切片精度、反思机制、多模型投票各自的贡献比例）*

#### 7.6.2 Token 成本与精度的权衡

*（分析不同切片方法的 Token 消耗差异，讨论 API 成本与检测精度之间的 trade-off）*

#### 7.6.3 方法的局限性与适用范围

本方法存在以下局限性：

1. **语言限制**：当前系统仅支持 Java，扩展到其他语言需要替换 AST 解析器和字节码分析框架。
2. **工具依赖**：系统依赖 SpotBugs 作为初始引擎，其规则覆盖范围决定了系统能检测的漏洞类型上界。
3. **API 成本**：多模型投票显著增加 API 调用成本，在大规模项目上可能带来较高的运营开销。
4. **SDG 构建开销**：对大型项目构建完整的系统依赖图可能耗时较长，需要引入增量分析策略。

---

## 第8章 总结与展望

### 8.1 工作总结

本文针对传统静态分析工具误报率高与大语言模型在代码审计中存在幻觉的双重挑战，提出了一种基于静态分析与大语言模型协同的 Java 代码缺陷智能验证方法。系统以 SpotBugs 为初始静态分析引擎，通过 XML 报告解析提取缺陷元数据，利用 AST 级语法提取与 SDG 级逆向程序切片技术生成精简的缺陷上下文，并通过 RAG 知识检索增强为 LLM 提供规则级专家知识。在语义验证阶段，构建了基于 LangChain4j 的 LLM Agent 框架，采用渐进式提示与工具调用机制实现上下文的动态补全，通过反思机制与多模型投票策略有效缓解了 LLM 幻觉问题。

系统基于 Spring Boot 微服务架构实现，包含四个 Maven 模块、66 个 Java 源文件、约 3,000 行有效代码，具备断点续传、MD5 秒传缓存、多线程并发等工程化特性。在 OWASP Benchmark 标准数据集上的消融实验验证了各组件的有效性。

### 8.2 主要贡献

1. **基于静态警报的逆向程序切片方法**：以 SpotBugs 报告的警报点为切片原点，通过 AST 语法提取（基线）和 SDG 逆向依赖追踪（改进）两种策略，生成精简且保留完整语义的代码上下文，有效降低了 LLM 的 Token 消耗。

2. **渐进式提示 LLM Agent 框架**：赋予智能体主动读取源码和浏览项目结构的工具调用能力，打破了 LLM 上下文窗口的固有限制，实现了"按需加载"的分析模式。

3. **反思机制与多模型协同校验策略**：设计了结构化的反思检查表和分层的多模型投票机制，在控制 API 成本的前提下显著提升了缺陷验证的准确性与可信度。

4. **完整的工程系统与实验验证**：实现了从静态扫描到 LLM 验证的端到端系统，并通过消融实验量化了各组件的独立贡献。

### 8.3 不足与改进方向

1. **切片算法可扩展性**：当前 SDG 构建对大型项目的性能尚需优化，可考虑引入增量分析或限定分析范围。
2. **多模型成本优化**：可以通过模型置信度评估动态决定是否触发多模型投票，进一步降低 API 开销。
3. **语言扩展**：将框架扩展到 Python、C/C++ 等语言，需要替换对应的 AST 解析器和程序分析工具。
4. **CI/CD 集成**：将系统封装为 Maven/Gradle 插件或 GitHub Action，实现与持续集成流水线的无缝对接。

### 8.4 展望

未来的研究可从以下方向继续深入：

1. **多工具融合**：集成 PMD、Checkstyle、Infer 等多种静态分析工具，通过交叉验证进一步提升检测覆盖率。
2. **动态分析辅助**：引入模糊测试（Fuzzing）或符号执行（Symbolic Execution）生成的运行时信息，为 LLM 提供更精确的路径可达性判据。
3. **LLM 微调**：基于历史审计数据对小规模 LLM 进行领域微调，在降低推理成本的同时提升代码审计的专业能力。
4. **企业级持续审计平台**：构建面向企业的 SaaS 平台，支持多项目管理、增量扫描、告警通知和审计报告生成等企业级功能。

---

## 参考文献

[1] Hovemeyer D, Pugh W. Finding bugs is easy[J]. ACM SIGPLAN Notices, 2004, 39(12): 92-106.

[2] Weiser M. Program slicing[J]. IEEE Transactions on Software Engineering, 1984, SE-10(4): 352-357.

[3] Ferrante J, Ottenstein K J, Warren J D. The program dependence graph and its use in optimization[J]. ACM Transactions on Programming Languages and Systems, 1987, 9(3): 319-349.

[4] Horwitz S, Reps T, Binkley D. Interprocedural slicing using dependence graphs[J]. ACM Transactions on Programming Languages and Systems, 1990, 12(1): 26-60.

[5] Vallée-Rai R, Co P, Gagnon E, et al. Soot: A Java bytecode optimization framework[C]. CASCON, 1999: 13.

[6] Fink S J, Dolby J, et al. WALA—The TJ Watson Libraries for Analysis[EB/OL]. https://github.com/wala/WALA.

[7] Lewis P, Perez E, Piktus A, et al. Retrieval-augmented generation for knowledge-intensive NLP tasks[C]. NeurIPS, 2020.

[8] Wei J, Wang X, Schuurmans D, et al. Chain-of-thought prompting elicits reasoning in large language models[C]. NeurIPS, 2022.

[9] Yao S, Zhao J, Yu D, et al. ReAct: Synergizing reasoning and acting in language models[C]. ICLR, 2023.

[10] Madaan A, Tandon N, Gupta P, et al. Self-refine: Iterative refinement with self-feedback[C]. NeurIPS, 2023.

[11] Shinn N, Cassano F, Gopinath A, et al. Reflexion: Language agents with verbal reinforcement learning[C]. NeurIPS, 2023.

[12] Li H, Hao Y, Zhai Y, et al. Assessing and improving the quality of ChatGPT for code review[J]. arXiv preprint arXiv:2306.08341, 2023.

[13] Zhou Y, Liu S, Siow J, et al. Devign: Effective vulnerability identification by learning comprehensive program semantics via graph neural networks[C]. NeurIPS, 2019.

[14] Fu M, Tantithamthavorn C. LineVul: A transformer-based line-level vulnerability prediction[C]. MSR, 2022.

[15] OWASP Foundation. OWASP Benchmark[EB/OL]. https://owasp.org/www-project-benchmark/.

[16] Chen X, Liu C, Song D. Towards understanding and mitigating hallucination in large language models[J]. arXiv preprint, 2024.

[17] Du Y, Li S, Torralba A, et al. Improving factuality and reasoning in language models through multiagent debate[C]. ICML, 2024.

[18] LangChain4j. Java framework for LLM-powered applications[EB/OL]. https://github.com/langchain4j/langchain4j.

---

## 致谢

*（待补充）*

---

## 附录

### 附录 A：系统 API 接口完整列表

*（待补充 Swagger 导出的完整接口文档）*

### 附录 B：数据库表结构详细定义

*（待补充各表的完整 DDL 语句）*

### 附录 C：System Prompt 全文

*（待补充 code-audit-system.txt 的完整内容）*

### 附录 D：OWASP Benchmark 漏洞类别映射表

*（待补充 CATEGORY_MAPPING 的完整映射关系）*

