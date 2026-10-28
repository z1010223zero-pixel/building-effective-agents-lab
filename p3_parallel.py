"""第 3 层：Parallelization（并行化），两种用法。
A. Sectioning：把任务切成互不依赖的子块，一起跑，最后代码里拼起来。
   —— 快，而且每块注意力更集中（原文强调这点）。
B. Voting：同一个任务跑多次/多种 prompt，代码里投票。
   —— 提高置信度，代价是钱多。

注意：这里用的是真并发（ThreadPoolExecutor），不是假装并行。
"""
import re
from concurrent.futures import ThreadPoolExecutor
import llm

# ---------- A. Sectioning ----------
SEC = {
    "安全": "只从安全角度审查这段代码，列出最严重的问题，每条一行，没发现就写 无。",
    "性能": "只从性能角度审查这段代码，列出最严重的问题，每条一行，没发现就写 无。",
    "可读性": "只从可读性角度审查这段代码，列出最严重的问题，每条一行，没发现就写 无。",
}
CODE = """
def get_user(id):
    import sqlite3
    conn = sqlite3.connect("app.db")
    cur = conn.execute(f"SELECT * FROM users WHERE id = {id}")
    return cur.fetchone()
"""


def section(name, rule):
    out = llm.text([
        {"role": "system", "content": rule},
        {"role": "user", "content": CODE},
    ], max_tokens=300)
    return name, out


def run_sectioning():
    with ThreadPoolExecutor(max_workers=len(SEC)) as ex:
        results = list(ex.map(lambda kv: section(*kv), SEC.items()))  # 真并行
    print("=== A. Sectioning（三路并行审查） ===")
    for name, out in results:
        print(f"\n[{name}]\n{out}")


# ---------- B. Voting ----------
VOTE_PROMPT = """判断下面这条商品评价是否属于"虚假好评"。
只输出 YES 或 NO。
评价：{review}"""
REVIEWS = [
    "好评！好评！好评！五星好评，物流很快，老板人很好，下次还来，好评好评好评好评。",
    "键盘手感偏硬，用了两周后空格键开始有异响，联系客服换了一个。",
]


def one_vote(args):
    review, temp = args
    raw = llm.text([{"role": "user", "content": VOTE_PROMPT.format(review=review)}],
                   temperature=temp, max_tokens=4)
    m = re.search(r"YES|NO", raw.upper())
    return m.group(0) if m else "?"


def run_voting(n=3):
    print("\n=== B. Voting（每条评 3 票，不同温度制造多样性） ===")
    tasks = [(r, t) for r in REVIEWS for t in (0.0, 0.7, 1.0)]
    with ThreadPoolExecutor(max_workers=6) as ex:
        votes = list(ex.map(one_vote, tasks))
    per = n
    for i, r in enumerate(REVIEWS):
        v = votes[i * per:(i + 1) * per]
        yes = v.count("YES")
        verdict = "YES(虚假)" if yes > per // 2 else "NO"
        print(f"\n{v} -> 票数 YES={yes}/{per} 结论={verdict}\n  评价：{r[:30]}...")


if __name__ == "__main__":
    run_sectioning()
    run_voting()
