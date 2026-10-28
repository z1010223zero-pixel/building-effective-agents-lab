"""第 6 层：Autonomous Agent（自主代理）。
前面几层的路径都是我们写死的。这一层把"下一步做什么"交还给模型：
  循环 { 模型看着历史和工具结果，决定调工具 / 给最终答案 }
原文强调三件事，这里都能看到：
  1. ground truth：每一步都从环境拿真实结果（工具返回值）
  2. 停止条件：max_iterations + 模型自己说"完成"
  3. ACI（agent-computer interface）：工具描述写清楚，错误信息写成让模型能自己纠正的样子
"""
import json, os
import llm

WORKDIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sandbox")
os.makedirs(WORKDIR, exist_ok=True)
SANDBOX = os.path.realpath(WORKDIR)


def _safe(path):
    full = os.path.realpath(os.path.join(SANDBOX, path))
    if not full.startswith(SANDBOX):
        raise PermissionError(f"越界路径被拒绝：{path}")
    return full


# ---- 工具集：3 个就够说明问题。描述要写"什么时候用/什么时候别用" ----
def list_files(pattern: str = ".") -> str:
    import glob
    hits = glob.glob(os.path.join(SANDBOX, pattern))
    if not hits:
        return "没有匹配文件。提示：pattern 支持 *.py、src/* 这类写法。"
    return "\n".join(os.path.relpath(h, SANDBOX) for h in sorted(hits))


def read_file(path: str) -> str:
    full = _safe(path)
    if not os.path.isfile(full):
        return f"文件不存在：{path}。先用 list_files 看看有什么。"
    return open(full, encoding="utf-8", errors="replace").read()[:4000]


def write_file(path: str, content: str) -> str:
    full = _safe(path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    open(full, "w", encoding="utf-8").write(content)
    return f"已写入 {path}（{len(content)} 字符）"


TOOLS = [
    {"type": "function", "function": {
        "name": "list_files",
        "description": "列出沙箱内匹配 glob 的文件。不确定文件叫什么时先用它。",
        "parameters": {"type": "object", "properties": {
            "pattern": {"type": "string", "description": "glob，默认 . 表示全部"}}}}},
    {"type": "function", "function": {
        "name": "read_file",
        "description": "读取沙箱内一个文本文件，最多 4000 字符。",
        "parameters": {"type": "object", "properties": {
            "path": {"type": "string"}}, "required": ["path"]}}},
    {"type": "function", "function": {
        "name": "write_file",
        "description": "在沙箱内写入/覆盖文件。整个文件内容一次给出，不做局部替换。",
        "parameters": {"type": "object", "properties": {
            "path": {"type": "string"}, "content": {"type": "string"}},
            "required": ["path", "content"]}}},
]
REGISTRY = {"list_files": list_files, "read_file": read_file, "write_file": write_file}

SYSTEM = """你是一个编码代理，工作目录已给定沙箱。
规则：
- 动手前先看清楚现状（list_files / read_file），不要凭猜写文件。
- 每次只做一小步，做完看结果再决定下一步。
- 任务完成时不要再调工具，直接给出简短总结。
- 遇到工具报错，读错误信息自己纠正，不要重复同样的失败调用。"""


def run(task, max_iterations=8):
    messages = [{"role": "system", "content": SYSTEM},
                {"role": "user", "content": task}]
    for step in range(1, max_iterations + 1):
        msg = llm.chat(messages, tools=TOOLS, max_tokens=1500)
        calls = msg.get("tool_calls") or []
        if not calls:
            # 模型认为完成了 —— 这就是"模型自己给的"停止条件
            print(f"[第{step}步] 模型收工")
            return llm.say(msg)

        messages.append(msg)
        for c in calls:
            name = c["function"]["name"]
            try:
                args = json.loads(c["function"]["arguments"] or "{}")
                print(f"[第{step}步] {name}({json.dumps(args, ensure_ascii=False)[:80]})")
                result = REGISTRY[name](**args)
            except Exception as e:
                # 错误也要回灌给模型，让它有机会自愈
                result = f"工具报错：{type(e).__name__}: {e}"
                print(f"        !! {result}")
            messages.append({"role": "tool", "tool_call_id": c["id"],
                             "content": str(result)[:4000]})
    return "[触顶] 达到最大步数，任务未收敛"


if __name__ == "__main__":
    q = ("沙箱里有一个 buggy.py，读它，找出并修复其中的 bug，"
         "把修好的版本写到 fixed.py，最后用一句话说明你改了什么。")
    print(run(q))
