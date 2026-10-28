package lab.aea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 最小 LLM 客户端（Java 版）。
 *
 * <p>对应 Python 的 {@code llm.py}：只用 JDK 自带 {@link HttpClient} 打 OpenAI 兼容接口，
 * 不引入任何 agent 框架。配置全部来自环境变量：
 * <pre>
 *   LLM_BASE   默认 http://127.0.0.1:8390/v1
 *   LLM_KEY    API Key
 *   LLM_MODEL  默认 DeepSeek-Flash
 * </pre>
 */
public final class Llm {

    private static final ObjectMapper M = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private static final String BASE = strip(env("LLM_BASE", "http://127.0.0.1:8390/v1"), "/");
    private static final String KEY = env("LLM_KEY", "");
    private static final String MODEL = env("LLM_MODEL", "DeepSeek-Flash");

    private Llm() {}

    /** 一次对话补全，返回 assistant message（可能带 tool_calls）。 */
    public static ObjectNode chat(List<Map<String, Object>> messages, ArrayNode tools) {
        return chat(messages, tools, MODEL, 0.3, 1500);
    }

    public static ObjectNode chat(List<Map<String, Object>> messages, ArrayNode tools,
                                  String model, double temperature, int maxTokens) {
        ObjectNode body = M.createObjectNode();
        body.put("model", model);
        body.set("messages", M.valueToTree(messages));
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        if (tools != null) body.set("tools", tools);

        // 三次重试，指数退避 —— 和 Python 版一致
        RuntimeException last = null;
        for (int i = 0; i < 3; i++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/chat/completions"))
                        .timeout(Duration.ofSeconds(180))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + KEY)
                        .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body)))
                        .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2) {
                    throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + resp.body());
                }
                return (ObjectNode) M.readTree(resp.body()).path("choices").path(0).path("message");
            } catch (Exception e) {
                last = new RuntimeException(e);
                try { Thread.sleep(1000L << i); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw last == null ? new IllegalStateException("chat failed") : last;
    }

    /** 只要文本的便捷版。 */
    public static String text(String system, String user) {
        List<Map<String, Object>> msgs = new ArrayList<>();
        if (system != null) msgs.add(Map.of("role", "system", "content", system));
        msgs.add(Map.of("role", "user", "content", user));
        return text(msgs);
    }

    public static String text(List<Map<String, Object>> msgs) {
        return say(chat(msgs, null));
    }

    public static String text(String system, String user, double temperature, int maxTokens) {
        List<Map<String, Object>> msgs = new ArrayList<>();
        if (system != null) msgs.add(Map.of("role", "system", "content", system));
        msgs.add(Map.of("role", "user", "content", user));
        return say(chat(msgs, null, MODEL, temperature, maxTokens));
    }

    public static String text(String user, double temperature, int maxTokens) {
        return say(chat(List.of(Map.of("role", "user", "content", user)), null, MODEL, temperature, maxTokens));
    }

    /** 取出内容并剥掉 <ds_safety> 这类后处理尾巴。 */
    public static String say(JsonNode msg) {
        String c = msg.path("content").asText("");
        int i = c.indexOf("<ds_safety>");
        return (i >= 0 ? c.substring(0, i) : c).trim();
    }

    /** 从模型输出里抠出 JSON：容忍 ```json 围栏和前后废话。 */
    public static String extractJson(String raw) {
        String s = raw.trim();
        int fence = s.indexOf("```");
        if (fence >= 0) {
            s = s.substring(fence + 3);
            if (s.startsWith("json")) s = s.substring(4);
            int end = s.indexOf("```");
            if (end >= 0) s = s.substring(0, end);
            return s.trim();
        }
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return (a >= 0 && b > a) ? s.substring(a, b + 1) : s;
    }

    public static ObjectMapper json() { return M; }

    private static String env(String k, String dflt) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? dflt : v;
    }

    private static String strip(String s, String suffix) {
        return s.endsWith(suffix) ? s.substring(0, s.length() - suffix.length()) : s;
    }
}
