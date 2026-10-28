"""第 2 层：Routing（路由）。
先分类，再送进各自专用的处理链。
原文两个典型用途：
  1) 不同客服意图 → 不同流程/工具
  2) 简单题给便宜模型，难题给贵模型（省钱又不掉质量）
这里的路由器是一个"只输出标签"的小 LLM 调用（temperature=0，限制候选集）。
真实生产里分类器可以是很小的模型甚正则，没必要上大模型。
"""
import llm

CATEGORIES = {
    "refund":  "退款/退货/钱没退回来",
    "tech":    "用不了、报错、登录不上、性能问题",
    "billing": "账单金额、发票、扣费异常",
    "other":   "其他",
}

ROUTER_PROMPT = """你是客服意图分类器。只输出一个英文标签，不要解释。
可选标签：refund / tech / billing / other
用户消息：
{msg}"""

# 每条链子有自己专用 prompt + 专用"工具"，互不污染
HANDLERS = {
    "refund": "你是退款专员。给出退款可行性判断与具体操作步骤，语气克制，不超过 120 字。",
    "tech":   "你是技术支持。先用一句话确认故障现象，再给 1-3 个可验证的排查步骤，不超过 120 字。",
    "billing": "你是账单专员。解释可能的扣费原因并说明如何自查账单明细，不超过 120 字。",
    "other":  "你是通用客服。简短回应并追问一个关键信息，不超过 80 字。",
}


def route(msg):
    raw = llm.text([{"role": "user", "content": ROUTER_PROMPT.format(msg=msg)}],
                   temperature=0, max_tokens=8)
    tag = raw.strip().lower().strip(".,:：\"' ")
    if tag not in HANDLERS:          # 分类器也会翻车，必须兜底
        tag = "other"
    return tag


def handle(msg, tag):
    return llm.text([
        {"role": "system", "content": HANDLERS[tag]},
        {"role": "user", "content": msg},
    ])


if __name__ == "__main__":
    cases = [
        "我上周买的键盘有异响，想退货，钱什么时候到账？",
        "App 一打开就闪退，安卓 14。",
        "这个月为什么多扣了 30 块？",
        "你们周末上班吗？",
    ]
    for m in cases:
        tag = route(m)
        print(f"\n>>> {m}\n    route -> {tag}")
        print("    " + handle(m, tag).replace("\n", "\n    "))
