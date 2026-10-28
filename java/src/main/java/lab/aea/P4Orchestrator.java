package lab.aea;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 第 4 层：Orchestrator-Workers（编排者-工作者）。
 *
 * <p>和 Parallelization 的区别（原文原话）：子任务<b>不是预先定义的</b>，
 * 而是由编排者按具体输入现场切分。所以子任务数量和内容每次都不同。
 *
 * <p>这是「写稿 / 改代码 / 调研」类 agent 最常见的骨架。
 */
public final class P4Orchestrator {

    private static final String ORCH_PROMPT = """
            你是任务编排者。把用户目标拆成 2-4 个可独立完成的子任务，供并行执行。
            规则：
            - 每条子任务必须能独立完成，不需要等其他子任务的结果
            - 不要写"汇总""整合"，那是你最后自己做的
            只输出 JSON，格式：
            {"subtasks": [{"id": "t1", "task": "..."}]}
            用户目标：%s""";

    public record Sub(String id, String task) {}

    static List<Sub> orchestrate(String goal) throws Exception {
        String raw = Llm.text(String.format(ORCH_PROMPT, goal), 0.0, 800);
        JsonNode root = Llm.json().readTree(Llm.extractJson(raw));
        List<Sub> subs = new ArrayList<>();
        for (JsonNode s : root.path("subtasks")) {
            subs.add(new Sub(s.path("id").asText("t" + (subs.size() + 1)), s.path("task").asText()));
        }
        return subs;
    }

    record Result(String id, String task, String output) {}

    static Result worker(Sub sub) {
        String out = Llm.text("只完成这一个子任务，不要扩展：" + sub.task(), 0.3, 800);
        return new Result(sub.id(), sub.task(), out);
    }

    private static final String GOAL =
            "帮我调研并给出结论：小团队自建 LLM 网关，用 LiteLLM 还是自己写 FastAPI 代理？";

    public static String run(String goal) throws Exception {
        List<Sub> subs = orchestrate(goal);
        System.out.println("编排出 " + subs.size() + " 个子任务：");
        subs.forEach(s -> System.out.println("  " + s.id() + ": " + s.task()));

        List<Result> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Result>> fs = subs.stream()
                    .map(s -> CompletableFuture.supplyAsync(() -> worker(s), pool))
                    .toList();
            for (var f : fs) results.add(f.join());   // 动态子任务 → 并行
        }

        StringBuilder blocks = new StringBuilder();
        for (Result r : results) {
            blocks.append("[").append(r.id()).append("] ").append(r.task())
                    .append("\n").append(r.output()).append("\n\n");
        }
        System.out.println("\n=== 汇总 ===");
        String prompt = """
                用户目标：%s

                各子任务产出：

                %s

                整合成一份结构清晰的最终答案，直接给结论和理由，不要提‘子任务’。""".formatted(goal, blocks);
        return Llm.text(prompt, 0.3, 1200);
    }

    public static void main(String[] args) throws Exception {
        try { main0(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static void main0() throws Exception {
        System.out.println(run(GOAL));
    }
}
