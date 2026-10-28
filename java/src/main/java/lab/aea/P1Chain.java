package lab.aea;

/**
 * 第 1 层：Prompt Chaining（提示链）。
 *
 * <p>任务拆成固定顺序的几步，上一步的输出是下一步的输入。
 * 关键是中间那道 <b>gate</b>：程序化检查，不合格就重来或直接放弃，
 * 别把垃圾传下去烧后面的 token。
 */
public final class P1Chain {

    static String outline(String topic) {
        return Llm.text("为「" + topic + "」列一个 3 条要点的大纲，每条一行，行首用 - 开头，不要别的字。",
                0.3, 400);
    }

    /** 程序化门禁：正好 3 条，且每条 >= 8 字。不靠模型自评。 */
    static boolean gate(String outlineText) {
        String[] lines = outlineText.lines()
                .map(String::trim)
                .filter(l -> l.startsWith("-"))
                .toArray(String[]::new);
        if (lines.length != 3) return false;
        for (String l : lines) {
            if (l.length() <= 9) return false;   // "- " 占两个字符，正文须 > 7
        }
        return true;
    }

    static String draft(String topic, String outlineText) {
        return Llm.text("按下面大纲写一段 150 字左右的推广文案，不要标题：\n" + outlineText, 0.5, 600);
    }

    public static String run(String topic, int maxTries) {
        String ol = null;
        boolean ok = false;
        for (int attempt = 1; attempt <= maxTries; attempt++) {
            ol = outline(topic);
            ok = gate(ol);
            long n = ol.lines().filter(l -> l.trim().startsWith("-")).count();
            System.out.println("[第" + attempt + "次大纲] gate=" + (ok ? "过" : "挂") + " 条数=" + n);
            System.out.println(ol);
            if (ok) break;
        }
        if (!ok) {
            return "大纲两次都没过大纲门禁，直接放弃（这就是 gate 的价值：不往下烧钱）";
        }
        String text = draft(topic, ol);
        System.out.println("[正文] 字数=" + text.replaceAll("\\s", "").length());
        return text;
    }

    public static void main(String[] args) {
        try { main0(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static void main0() {
        System.out.println("\n=== 最终输出 ===");
        System.out.println(run("给程序员用的番茄钟 App", 2));
    }
}
