"""最小 LLM 客户端：直接打 OpenAI 兼容接口，不用任何框架。
密钥走环境变量 LLM_KEY / LLM_BASE / LLM_MODEL，代码里不硬编码。
"""
import json, os, time, urllib.request

BASE  = os.environ.get("LLM_BASE", "http://127.0.0.1:8390/v1")
KEY   = os.environ.get("LLM_KEY", "")
MODEL = os.environ.get("LLM_MODEL", "DeepSeek-Flash")


def chat(messages, tools=None, model=None, temperature=0.3, max_tokens=1024, retries=3):
    """调一次 chat/completions，返回 assistant message dict（可能带 tool_calls）。"""
    body = {"model": model or MODEL, "messages": messages,
            "temperature": temperature, "max_tokens": max_tokens}
    if tools:
        body["tools"] = tools
    data = json.dumps(body).encode()
    url = BASE.rstrip("/") + "/chat/completions"
    for i in range(retries):
        req = urllib.request.Request(url, data=data, headers={
            "Content-Type": "application/json", "Authorization": "Bearer " + KEY})
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                return json.load(r)["choices"][0]["message"]
        except Exception as e:
            if i == retries - 1:
                raise
            time.sleep(2 ** i)


def text(messages, **kw):
    """只要文本的便捷版。"""
    msg = chat(messages, **kw)
    return (msg.get("content") or "").strip()


def say(msg):
    """打印模型输出，去掉 <ds_safety> 这类后处理尾巴。"""
    c = (msg.get("content") or "")
    if "<ds_safety>" in c:
        c = c.split("<ds_safety>")[0]
    return c.strip()
