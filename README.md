# 用可跑的代码重读《Building Effective Agents》

原文章：https://www.anthropic.com/research/building-effective-agents

每个文件对应原文一层，都是**能直接跑、能改参数看区别**的完整代码，不依赖任何 agent 框架（原文也建议先用裸 API）。

| 文件 | 原文对应 | 一句话 |
|---|---|---|
| `llm.py` | 基础 | 60 行 OpenAI 兼容客户端，其它脚本共用 |
| `p0_augmented.py` | Building block: Augmented LLM | 模型 + 一个工具，看它怎么发起 tool_call、结果怎么回灌 |
| `p1_chain.py` | Workflow: Prompt chaining | 大纲→gate 校验→正文，看 gate 不通过时如何中止 |
| `p2_routing.py` | Workflow: Routing | 分类器分流到 4 套专用 prompt，看兜底标签 |
| `p3_parallel.py` | Workflow: Parallelization | A. 三路并行代码审查（sectioning）；B. 三个温度投票（voting） |
| `p4_orchestrator.py` | Workflow: Orchestrator-workers | 模型动态拆子任务 → 并行 → 汇总；和 p3 对比"固定 vs 动态" |
| `p5_evaluator.py` | Workflow: Evaluator-optimizer | 写作 + 打分反馈循环，带阈值和最大轮数 |
| `p6_agent.py` | Agents | 真·agent loop：工具调用→环境反馈→自愈→自己判定完成 |

## 跑法

```bash
./run.sh                     # 全部跑一遍（约 10 分钟，看模型速度）
python3 p3_parallel.py       # 单跑
LLM_MODEL=DeepSeek-V4-Pro ./run.sh
```

密钥不写进代码，`run.sh` 从本机 unified-proxy 的进程环境里读，通过环境变量传给脚本。
换别家模型只要改 `LLM_BASE` / `LLM_KEY` / `LLM_MODEL` 三个环境变量（走 OpenAI 兼容协议）。

## 建议的读法

1. 先跑 `p0`，理解"工具调用"其实就是**多一轮对话**，没有任何魔法。
2. 对比 `p3` 和 `p4`：同样的并行执行，区别只在于子任务是**我们写死的**还是**模型现场拆的**。原文那句 "the key difference from parallelization is its flexibility" 就落在这几行代码上。
3. 读 `p1` 的 `gate()` 和 `p5` 的 `THRESHOLD`：workflow 的可靠性来自**代码里的检查**，不是靠模型自觉。
4. 最后读 `p6`：它把前三层的所有东西（工具、循环、地面真值、停止条件）合在一起。agent 不神秘，就是一个 while 循环。

## 刻意没做的事

- 没上 LangGraph/LangChain/Claude Agent SDK。原文明说框架会遮蔽 prompt 和响应，调试变难；先用裸 API 才看得清每层到底发生了什么。
- 没做失败重试/并发限制/成本统计等生产件，那些是工程问题，不是模式问题。
