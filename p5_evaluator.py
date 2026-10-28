"""第 5 层：Evaluator-Optimizer（评估者-优化者循环）。
一个 LLM 生成，另一个 LLM 按明确标准打分并给具体修改意见，循环到达标或触顶。
原文要点：适用条件有两个——(1) 明确评价标准；(2) 迭代能带来可测的改进。
所以这个循环必须有 terminating condition，不然就是烧钱机。
"""
import json
import llm

DRAFT_SYS = "你是技术博客写手。根据要求写一段 80-120 字的产品简介，不要标题、不要 emoji。"

EVAL_PROMPT = """你是严格的技术编辑。按下面 4 项标准给这段简介打分（0-10），
只输出 JSON：{{"scores":{{"准确":n,"具体":n,"简洁":n,"无浮夸":n}},"total":n,"fix":"一句话具体改法"}}
标准说明：
- 准确：不夸大、不编造功能
- 具体：有可验证的细节，不是"强大/高效/赋能"这类空话
- 简洁：80-120 字，无冗余
- 无浮夸：不出现"革命性/颠覆/极致"等词
简介：
{draft}"""

THRESHOLD = 32   # 满分 40


def evaluate(draft):
    raw = llm.text([{"role": "user", "content": EVAL_PROMPT.format(draft=draft)}],
                   temperature=0)
    s = raw.strip()
    if "```" in s:
        s = s.split("```")[1].replace("json", "", 1)
    return json.loads(s.strip())


def optimize(draft, fix):
    return llm.text([
        {"role": "system", "content": DRAFT_SYS},
        {"role": "user", "content": f"上一版：{draft}\n编辑意见：{fix}\n按意见重写，保持 80-120 字。"},
    ])


def run(topic, max_rounds=3):
    draft = llm.text([
        {"role": "system", "content": DRAFT_SYS},
        {"role": "user", "content": f"产品：{topic}"},
    ])
    for i in range(1, max_rounds + 1):
        ev = evaluate(draft)
        print(f"[第{i}轮] total={ev['total']}/40 {ev['scores']}")
        print(f"   草稿：{draft}")
        print(f"   意见：{ev['fix']}")
        if ev["total"] >= THRESHOLD:
            print("   -> 达标，跳出循环")
            return draft
        draft = optimize(draft, ev["fix"])
    print("   -> 触顶退出，返回最后一版")
    return draft


if __name__ == "__main__":
    final = run("一个只监听本机、能把任何 OpenAI 兼容请求转发的极简 Python 代理，30 行代码")
    print("\n=== 最终稿 ===\n" + final)
