package lab.aea;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 第 5 层：Evaluator-Optimizer（评估者-优化者循环）。
 *
 * <p>一个 LLM 生成，另一个 LLM 按<b>明确标准</b>打分并给具体修改意见，
 * 循环到达标或触顶。原文说这个模式适用的两个前提就是：
 * (1) 有清晰的评价标准；(2) 迭代真能带来可测的改进。
 *
 * <p>所以循环必须有终止条件 —— 这里就是 {@code THRESHOLD} 和 {@code maxRounds}，
 * 否则就是个烧钱机器。
 */
public final class P5Evaluator {

    private static final String DRAFT_SYS = "你是技术博客写手。根据要求写一段 80-120 字的产品简介，不要标题、不要 emoji。";

    private static final String EVAL_PROMPT = """
            你是严格的技术编辑。按下面 4 项标准给这段简介打分（0-10），
            只输出 JSON：{"scores":{"准确":n,"具体":n,"简洁":n,"无浮夸":n},"total":n,"fix":"一句话具体改法"}
            标准说明：
            - 准确：不夸大、不编造功能
            - 具体：有可验证的细节，不是"强大/高效/赋能"这类空话
            - 简洁：80-120 字，无冗余
            - 无浮夸：不出现"革命性/颠覆/极致"等词
            简介：
            %s""";

    private static final int THRESHOLD = 32;   // 满分 40

    record Eval(int total, String fix) {}

    static Eval evaluate(String draft) throws Exception {
        String raw = Llm.text(String.format(EVAL_PROMPT, draft), 0.0, 400);
        JsonNode n = Llm.json().readTree(Llm.extractJson(raw));
        return new Eval(n.path("total").asInt(0), n.path("fix").asText(""));
    }

    static String optimize(String draft, String fix) {
        return Llm.text(DRAFT_SYS, "上一版：" + draft + "\n编辑意见：" + fix + "\n按意见重写，保持 80-120 字。", 0.4, 400);
    }

    public static String run(String topic, int maxRounds) throws Exception {
        String draft = Llm.text(DRAFT_SYS, "产品：" + topic, 0.6, 400);
        for (int i = 1; i <= maxRounds; i++) {
            Eval ev = evaluate(draft);
            System.out.println("[第" + i + "轮] total=" + ev.total() + "/40");
            System.out.println("   草稿：" + draft);
            System.out.println("   意见：" + ev.fix());
            if (ev.total() >= THRESHOLD) {
                System.out.println("   -> 达标，跳出循环");
                return draft;
            }
            draft = optimize(draft, ev.fix());
        }
        System.out.println("   -> 触顶退出，返回最后一版");
        return draft;
    }

    public static void main(String[] args) throws Exception {
        try { main0(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static void main0() throws Exception {
        String fin = run("一个只监听本机、能把任何 OpenAI 兼容请求转发的极简 Python 代理，30 行代码", 3);
        System.out.println("\n=== 最终稿 ===\n" + fin);
    }
}
