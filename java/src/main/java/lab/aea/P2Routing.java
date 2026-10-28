package lab.aea;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 第 2 层：Routing（路由）—— 先分类，再送进各自专用的处理链。
 *
 * <p>原文两个典型用途：① 不同客服意图走不同流程；② 简单题给便宜模型，难题给贵模型。
 * 这里的路由器是一个只输出标签的小 LLM 调用（temperature=0、限长），
 * 真实生产里分类器可以更小、甚至用正则/传统分类器。
 *
 * <p>注意最后的兜底：分类器也会翻车，未知标签必须落到 other。
 */
public final class P2Routing {

    private static final Map<String, String> HANDLERS = new LinkedHashMap<>();

    static {
        HANDLERS.put("refund", "你是退款专员。给出退款可行性判断与具体操作步骤，语气克制，不超过 120 字。");
        HANDLERS.put("tech", "你是技术支持。先用一句话确认故障现象，再给 1-3 个可验证的排查步骤，不超过 120 字。");
        HANDLERS.put("billing", "你是账单专员。解释可能的扣费原因并说明如何自查账单明细，不超过 120 字。");
        HANDLERS.put("other", "你是通用客服。简短回应并追问一个关键信息，不超过 80 字。");
    }

    static String route(String msg) {
        String raw = Llm.text("""
                你是客服意图分类器。只输出一个英文标签，不要解释。
                可选标签：refund / tech / billing / other
                用户消息：
                """ + msg, 0.0, 8);
        String tag = raw.trim().toLowerCase().replaceAll("^[\\s.。,，:：\"'`]+|[\\s.。,，:：\"'`]+$", "");
        return HANDLERS.containsKey(tag) ? tag : "other";   // 兜底
    }

    static String handle(String msg, String tag) {
        return Llm.text(HANDLERS.get(tag), msg);
    }

    public static void main(String[] args) {
        try { main0(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static void main0() {
        String[] cases = {
                "我上周买的键盘有异响，想退货，钱什么时候到账？",
                "App 一打开就闪退，安卓 14。",
                "这个月为什么多扣了 30 块？",
                "你们周末上班吗？",
        };
        for (String m : cases) {
            String tag = route(m);
            System.out.println("\n>>> " + m + "\n    route -> " + tag);
            System.out.println("    " + handle(m, tag).replace("\n", "\n    "));
        }
    }
}
