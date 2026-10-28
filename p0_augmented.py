"""第 0 层：Augmented LLM（增强型 LLM）。
原文说这是所有 agentic 系统的"地基"：模型 + 工具 + 检索 + 记忆。
这里只写最朴素的一层——模型自己决定调哪个工具，
我们做的是：把工具定义好 + 把工具结果喂回去。没有循环，一步就停。
"""
import json
import llm

# ---- 1. 给模型看的工具"说明书"。写得好不好直接决定它用得对不对 ----
TOOLS = [{
    "type": "function",
    "function": {
        "name": "get_weather",
        "description": "查询某城市当前天气。只在你需要真实天气数据时调用。",
        "parameters": {
            "type": "object",
            "properties": {
                "city": {"type": "string", "description": "城市名，例如 上海"}
            },
            "required": ["city"],
        },
    },
}]

# ---- 2. 工具的真实实现（这里用假的，换成真 API 就是生产版）----
def get_weather(city: str) -> str:
    fake = {"上海": "多云 18°C", "北京": "晴 12°C", "深圳": "雷阵雨 26°C"}
    return fake.get(city, f"查不到 {city} 的天气")

REGISTRY = {"get_weather": get_weather}


def run(user_input: str):
    messages = [
        {"role": "system", "content": "你是天气助手，需要数据时调用工具。"},
        {"role": "user", "content": user_input},
    ]
    msg = llm.chat(messages, tools=TOOLS)
    calls = msg.get("tool_calls") or []
    if not calls:
        return llm.say(msg)          # 模型觉得不用工具，直接答

    # 模型发起工具调用 → 我们执行 → 把结果塞回对话
    messages.append(msg)
    for c in calls:
        name = c["function"]["name"]
        args = json.loads(c["function"]["arguments"])
        print(f"  [工具] {name}({args})")
        result = REGISTRY[name](**args)
        print(f"  [结果] {result}")
        messages.append({"role": "tool", "tool_call_id": c["id"], "content": result})

    # 拿到真数据后再让模型用它回答（注意：这已经算一轮"循环"了）
    return llm.say(llm.chat(messages))


if __name__ == "__main__":
    for q in ["今天上海穿什么衣服合适？", "1+1等于几？"]:
        print(f"\n>>> {q}")
        print(run(q))
