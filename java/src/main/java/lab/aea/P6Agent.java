package lab.aea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 第 6 层：Autonomous Agent（自主代理）。
 *
 * <p>前面几层的路径都是我们写死的；这一层把「下一步做什么」交还给模型：
 * <pre>{@code
 * while (步数 < 上限) {
 *     模型看着历史 + 工具结果，决定：调工具 / 给最终答案
 * }
 * }</pre>
 *
 * <p>原文强调的三件事都在这里：
 * <ol>
 *   <li><b>ground truth</b>：每一步都从环境拿真实结果（工具返回值）</li>
 *   <li><b>停止条件</b>：maxIterations + 模型自己不再发起工具调用</li>
 *   <li><b>ACI</b>：工具描述写清楚，错误信息写成模型能自己纠正的样子</li>
 * </ol>
 *
 * <p>另外还有原文说的 guardrail：所有路径都过 {@code safe()} 的 realpath 校验，越界直接抛错。
 */
public final class P6Agent {

    private static final Path SANDBOX;

    static {
        try {
            SANDBOX = Paths.get("sandbox").toAbsolutePath().normalize();
            Files.createDirectories(SANDBOX);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Path safe(String p) {
        Path full = SANDBOX.resolve(p == null ? "" : p).normalize();
        if (!full.startsWith(SANDBOX)) {
            throw new SecurityException("越界路径被拒绝：" + p);
        }
        return full;
    }

    // ---- 工具实现：错误信息要写成"下一步该干什么" ----
    static String listFiles(Map<String, Object> args) throws IOException {
        String pattern = String.valueOf(args.getOrDefault("pattern", "."));
        Path globRoot = SANDBOX;
        String glob = pattern;
        // 模型常见写法 **/*.py：退化成当前目录下的 * 前缀匹配
        if (glob.startsWith("**/")) glob = glob.substring(3);
        if (glob.contains("/")) {
            int idx = pattern.lastIndexOf('/');
            globRoot = safe(pattern.substring(0, idx));
            glob = pattern.substring(idx + 1);
        }
        if (!Files.isDirectory(globRoot)) return "目录不存在。提示：pattern 支持 *.py、src/* 这类写法。";
        try (Stream<Path> s = Files.list(globRoot)) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            List<String> hits = s.filter(p -> matcher.matches(p.getFileName()))
                    .map(p -> SANDBOX.relativize(p).toString())
                    .sorted().toList();
            return hits.isEmpty()
                    ? "没有匹配文件。提示：pattern 支持 *.py、src/* 这类写法。"
                    : String.join("\n", hits);
        }
    }

    static String readFile(Map<String, Object> args) throws IOException {
        String path = String.valueOf(args.getOrDefault("path", ""));
        Path full = safe(path);
        if (!Files.isRegularFile(full)) return "文件不存在：" + path + "。先用 list_files 看看有什么。";
        String s = Files.readString(full, StandardCharsets.UTF_8);
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }

    static String writeFile(Map<String, Object> args) throws IOException {
        String path = String.valueOf(args.getOrDefault("path", ""));
        String content = String.valueOf(args.getOrDefault("content", ""));
        Path full = safe(path);
        if (full.getParent() != null) Files.createDirectories(full.getParent());
        Files.writeString(full, content, StandardCharsets.UTF_8);
        return "已写入 " + path + "（" + content.length() + " 字符）";
    }

    interface ToolFn { String apply(Map<String, Object> args) throws Exception; }

    private static final Map<String, ToolFn> REGISTRY = new LinkedHashMap<>();
    private static final ArrayNode TOOLS = Llm.json().createArrayNode();

    static {
        REGISTRY.put("list_files", P6Agent::listFiles);
        REGISTRY.put("read_file", P6Agent::readFile);
        REGISTRY.put("write_file", P6Agent::writeFile);

        addTool("list_files", "列出沙箱内匹配 glob 的文件。不确定文件叫什么时先用它。",
                Map.of("pattern", Map.of("type", "string", "description", "glob，默认 . 表示全部")),
                List.of());
        addTool("read_file", "读取沙箱内一个文本文件，最多 4000 字符。",
                Map.of("path", Map.of("type", "string")), List.of("path"));
        addTool("write_file", "在沙箱内写入/覆盖文件。整个文件内容一次给出，不做局部替换。",
                Map.of("path", Map.of("type", "string"),
                        "content", Map.of("type", "string")),
                List.of("path", "content"));
    }

    private static void addTool(String name, String desc, Map<String, Object> props, List<String> required) {
        ObjectNode t = Llm.json().createObjectNode();
        t.put("type", "function");
        ObjectNode fn = t.putObject("function");
        fn.put("name", name);
        fn.put("description", desc);
        ObjectNode params = fn.putObject("parameters");
        params.put("type", "object");
        params.set("properties", Llm.json().valueToTree(props));
        ArrayNode req = params.putArray("required");
        required.forEach(req::add);
        TOOLS.add(t);
    }

    private static final String SYSTEM = """
            你是一个编码代理，工作目录已给定沙箱。
            规则：
            - 动手前先看清楚现状（list_files / read_file），不要凭猜写文件。
            - 每次只做一小步，做完看结果再决定下一步。
            - 任务完成时不要再调工具，直接给出简短总结。
            - 遇到工具报错，读错误信息自己纠正，不要重复同样的失败调用。""";

    public static String run(String task, int maxIterations) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM));
        messages.add(Map.of("role", "user", "content", task));

        for (int step = 1; step <= maxIterations; step++) {
            ObjectNode msg = Llm.chat(messages, TOOLS);
            JsonNode calls = msg.path("tool_calls");
            if (!calls.isArray() || calls.isEmpty()) {
                // 模型认为完成了 —— 这就是"模型自己给的"停止条件
                System.out.println("[第" + step + "步] 模型收工");
                return Llm.say(msg);
            }
            messages.add(P0Augmented.toMap(msg));
            for (JsonNode c : calls) {
                String name = c.path("function").path("name").asText();
                Map<String, Object> args = P0Augmented.parseArgs(c.path("function").path("arguments").asText());
                String result;
                System.out.println("[第" + step + "步] " + name + "(" + args + ")");
                try {
                    ToolFn fn = REGISTRY.get(name);
                    if (fn == null) throw new IllegalArgumentException("未知工具 " + name);
                    result = fn.apply(args);
                } catch (Exception e) {
                    // 错误也回灌给模型，让它有机会自愈
                    result = "工具报错：" + e.getClass().getSimpleName() + ": " + e.getMessage();
                    System.out.println("        !! " + result);
                }
                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", c.path("id").asText());
                toolMsg.put("content", result.length() > 4000 ? result.substring(0, 4000) : result);
                messages.add(toolMsg);
            }
        }
        return "[触顶] 达到最大步数，任务未收敛";
    }

    public static void main(String[] args) {
        try { main0(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static void main0() {
        String q = "沙箱里有一个 buggy.py，读它，找出并修复其中的 bug，"
                + "把修好的版本写到 fixed.py，最后用一句话说明你改了什么。";
        System.out.println(run(q, 8));
    }
}
