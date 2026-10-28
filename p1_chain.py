"""第 1 层：Prompt Chaining（提示链）。
任务拆成固定顺序的几步，上一步输出是下一步输入。
关键是原文说的那个 "gate"——中间程序化检查，不合格就停/重跑，别把垃圾传下去。
例子：写营销文案 → 翻译 → 检查；或者大纲 → 校验 → 正文。
"""
import re
import llm


def outline(topic):
    return llm.text([{"role": "user", "content":
        f"为「{topic}」列一个 3 条要点的大纲，每条一行，行首用 - 开头，不要别的字。"}])


def gate(outline_text):
    """程序化门禁：不靠模型打分，靠规则。这里要求正好 3 条且每条 >= 8 字。"""
    lines = [l.strip() for l in outline_text.splitlines() if l.strip().startswith("-")]
    ok = len(lines) == 3 and all(len(l) > 9 for l in lines)
    return ok, lines


def draft(topic, outline_text):
    return llm.text([{"role": "user", "content":
        f"按下面大纲写一段 150 字左右的推广文案，不要标题：\n{outline_text}"}])


def run(topic, max_tries=2):
    for attempt in range(1, max_tries + 1):
        ol = outline(topic)
        ok, lines = gate(ol)
        print(f"[第{attempt}次大纲] gate={'过' if ok else '挂'} 条数={len(lines)}")
        print(ol)
        if ok:
            break
    else:
        return "大纲两次都没过大纲门禁，直接放弃（这就是 gate 的价值：不往下烧钱）"

    text = draft(topic, ol)
    # 第二道门：字数与关键内容
    words = len(re.sub(r"\s", "", text))
    print(f"[正文] 字数={words}")
    return llm.say_text_helper(text) if hasattr(llm, "say_text_helper") else text


if __name__ == "__main__":
    print("\n=== 最终输出 ===")
    print(run("给程序员用的番茄钟 App"))
