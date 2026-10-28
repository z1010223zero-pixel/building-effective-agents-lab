package lab.aea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 第 0 层：Augmented LLM（增强型 LLM）—— 所有 agentic 系统的地基。
 *
 * <p>要点：模型自己决定调哪个工具；我们负责「写清说明书 → 执行 → 把结果回灌」。
 * 这里没有 while 循环，一步就停，好让你看清一次工具调用到底发生了什么。
 */
public final class P0Augmented {

    private static final Map<String, Function<Map<String, Object>, String>> REGISTRY = new HashMap<>();

    private static final ArrayNode TOOLS = Llm.json().createArrayNode();

    static {
        // ---- 1. 工具说明书：写得好不好直接决定模型调用得对不对 ----
        ObjectNode t = Llm.json().createObjectNode();
        t.put("type", "function");
        ObjectNode fn = t.putObject("function");
        fn.put("name", "get_weather");
        fn.put("description", "查询某城市当前天气。只在你需要真实天气数据时调用。");
        ObjectNode params = fn.putObject("parameters");
        params.put("type", "object");
        params.putObject("properties").putObject("city")
                .put("type", "string").put("description", "城市名，例如 上海");
        params.putArray("required").add("city");
        TOOLS.add(t);

        // ---- 2. 真实实现（这里是假的，换成真 API 就是生产版）----
        REGISTRY.put("get_weather", args -> {
            String city = String.valueOf(args.getOrDefault("city", ""));
            return switch (city) {
                case "上海" -> "多云 18°C";
                case "北京" -> "晴 12°C";
                case "深圳" -> "雷阵雨 26°C";
                default -> "查不到 " + city + " 的天气";
            };
        });
    }

    public static String run(String userInput) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是天气助手，需要数据时调用工具。"));
        messages.add(Map.of("role", "user", "content", userInput));

        ObjectNode msg = Llm.chat(messages, TOOLS);
        JsonNode calls = msg.path("tool_calls");
        if (!calls.isArray() || calls.isEmpty()) {
            return Llm.say(msg);          // 模型觉得不用工具，直接答
        }

        messages.add(toMap(msg));
        for (JsonNode c : calls) {
            String name = c.path("function").path("name").asText();
            Map<String, Object> args = parseArgs(c.path("function").path("arguments").asText());
            System.out.println("  [工具] " + name + "(" + args + ")");
            String result = REGISTRY.get(name).apply(args);
            System.out.println("  [结果] " + result);

            Map<String, Object> toolMsg = new HashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", c.path("id").asText());
            toolMsg.put("content", result);
            messages.add(toolMsg);
        }
        // 拿到真数据后再让模型用它回答
        return Llm.say(Llm.chat(messages, null));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toMap(ObjectNode n) { return Llm.json().convertValue(n, Map.class); }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseArgs(String json) {
        try {
            return Llm.json().readValue(json == null || json.isBlank() ? "{}" : json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static void main(String[] args) {
        try { main0(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static void main0() {
        for (String q : new String[]{"今天上海穿什么衣服合适？", "1+1等于几？"}) {
            System.out.println("\n>>> " + q);
            System.out.println(run(q));
        }
    }
}
