"""第 4 层：Orchestrator-Workers（编排者-工作者）。
和 Parallelization 的区别（原文原话）：子任务不是预先定义的，
而是由编排者根据具体输入动态切分。所以子任务数量和内容每次都不同。

这也是「客服/写稿/改代码」类 agent 最常见的骨架。
"""
import json
import llm
from concurrent.futures import ThreadPoolExecutor

ORCH_PROMPT = """你是任务编排者。把用户目标拆成 2-4 个可独立完成的子任务，供并行执行。
规则：
- 每条子任务必须能独立完成，不需要等其他子任务的结果
- 不要写"汇总""整合"，那是你最后自己做的
只输出 JSON，格式：
{{"subtasks": [{{"id": "t1", "task": "..."}}]}}
用户目标：{goal}"""


def orchestrate(goal):
    raw = llm.text([{"role": "user", "content": ORCH_PROMPT.format(goal=goal)}],
                   temperature=0)
    # 模型爱包 ```json ```，剥掉再解析
    s = raw.strip()
    if "```" in s:
        s = s.split("```")[1]
        s = s.replace("json", "", 1).strip()
    return json.loads(s)["subtasks"]


def worker(sub):
    out = llm.text([{"role": "user", "content":
        f"只完成这一个子任务，不要扩展：{sub['task']}"}], max_tokens=500)
    return sub["id"], sub["task"], out


GOAL = "帮我调研并给出结论：小团队自建 LLM 网关，用 LiteLLM 还是自己写 FastAPI 代理？"


def run(goal):
    subs = orchestrate(goal)
    print(f"编排出 {len(subs)} 个子任务：")
    for s in subs:
        print(f"  {s['id']}: {s['task']}")

    with ThreadPoolExecutor(max_workers=len(subs)) as ex:
        results = list(ex.map(worker, subs))   # 动态子任务 → 并行

    print("\n=== 汇总 ===")
    return llm.say(llm.chat([{"role": "user", "content":
        f"用户目标：{goal}\n\n各子任务产出：\n\n" +
        "\n\n".join(f"[{i}] {t}\n{o}" for i, t, o in results) +
        "\n\n整合成一份结构清晰的最终答案，直接给结论和理由，不要提‘子任务’。"}], max_tokens=900))


if __name__ == "__main__":
    print(run(GOAL))
