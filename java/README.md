# Java 版：Anthropic《Building Effective Agents》六层实现

和 [master 分支的 Python 版](../README.md) 一一对应，逐层对照看最有效：

| Python | Java | 原文对应 |
|---|---|---|
| `llm.py` | `src/main/java/lab/aea/Llm.java` | 基础客户端（JDK HttpClient + Jackson，无框架） |
| `p0_augmented.py` | `P0Augmented.java` | Building block: Augmented LLM |
| `p1_chain.py` | `P1Chain.java` | Prompt chaining（含 gate） |
| `p2_routing.py` | `P2Routing.java` | Routing（含兜底标签） |
| `p3_parallel.py` | `P3Parallel.java` | Parallelization（sectioning + voting） |
| `p4_orchestrator.py` | `P4Orchestrator.java` | Orchestrator-workers（模型动态拆任务） |
| `p5_evaluator.py` | `P5Evaluator.java` | Evaluator-optimizer（阈值 + 最大轮数） |
| `p6_agent.py` | `P6Agent.java` | Autonomous agent（循环 + ground truth + guardrail） |

## 环境

- JDK 21（虚拟线程 `Executors.newVirtualThreadPerTaskExecutor()` 用于 p3/p4 真并发）
- Maven 3.9+
- 唯一依赖：Jackson（解析 JSON）

## 跑

```bash
export LLM_BASE=http://127.0.0.1:8390/v1
export LLM_KEY=sk-xxx
export LLM_MODEL=DeepSeek-Flash

./run.sh          # 全部
./run.sh p6       # 单跑某一层（p0..p6）
```

或直接建包再跑：

```bash
mvn -q -B package -DskipTests
java -jar target/building-effective-agents-java-1.0.0-jar-with-dependencies.jar p4
```

## Java 版和 Python 版的差异点（真正值得注意的）

1. **并发**：Python 用 `ThreadPoolExecutor`，Java 用虚拟线程，写法都是 `CompletableFuture.supplyAsync(...)` 的 map + join。
2. **消息体**：Python 里是裸 dict，Java 里是 `List<Map<String,Object>>` + Jackson 的 `ObjectNode`。工具调用的 `tool_calls` 结构完全一样（OpenAI 协议没变）。
3. **工具注册**：Python 用 dict 映射函数；Java 用 `Map<String, ToolFn>`，`ToolFn` 是能抛 `Exception` 的函数接口 —— 因为 Java 的受检异常不能直接塞进 `Function`。
4. **guardrail**：两边都做 `realpath` 前缀校验，Java 里抛 `SecurityException`，Python 里抛 `PermissionError`；错误信息都被回灌给模型让它自愈。
5. **p6 的 glob**：Java 的 `PathMatcher` 不认 `**/*.py` 这种写法，所以代码里做了退化处理（剥掉 `**/` 前缀），否则模型会反复试错 —— 这正是 ACI 设计（工具描述+错误提示）值得投入的地方。

## 一个真实的坑（留作教材）

第一次跑 p6 时模型用了 `list_files(pattern="**/*.py")`，Java 抛 `InvalidPathException`，
模型看到错误信息后改成 `*.py` 就成功了 —— **工具的错误信息写得能指导下一步，agent 就能自愈；
写个 null pointer 或 500 堆栈过去，它只能瞎试**。这就是原文 Appendix 2 "Prompt Engineering your Tools" 讲的事。
