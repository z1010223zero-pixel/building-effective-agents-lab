package lab.aea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第 3 层：Parallelization（并行化），两种用法。
 *
 * <ul>
 *   <li><b>Sectioning</b>：任务切成互不依赖的子块，一起跑，最后代码里拼。快，且每块注意力更集中。</li>
 *   <li><b>Voting</b>：同一任务跑多次，代码里投票。提高置信度，代价是多花钱。</li>
 * </ul>
 *
 * <p>这里用 {@link CompletableFuture} 做<b>真并发</b>，不是假装并行。
 */
public final class P3Parallel {

    // ---------- A. Sectioning ----------
    private static final Map<String, String> SEC = new LinkedHashMap<>();

    static {
        SEC.put("安全", "只从安全角度审查这段代码，列出最严重的问题，每条一行，没发现就写 无。");
        SEC.put("性能", "只从性能角度审查这段代码，列出最严重的问题，每条一行，没发现就写 无。");
        SEC.put("可读性", "只从可读性角度审查这段代码，列出最严重的问题，每条一行，没发现就写 无。");
    }

    private static final String CODE = """
            def get_user(id):
                import sqlite3
                conn = sqlite3.connect("app.db")
                cur = conn.execute(f"SELECT * FROM users WHERE id = {id}")
                return cur.fetchone()
            """;

    private static Map.Entry<String, String> section(Map.Entry<String, String> rule) {
        String out = Llm.text(rule.getValue(), CODE);
        return Map.entry(rule.getKey(), out);
    }

    static void runSectioning() {
        System.out.println("=== A. Sectioning（三路并行审查） ===");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();
            for (var rule : SEC.entrySet()) {
                futures.add(CompletableFuture.supplyAsync(() -> section(rule), pool));
            }
            for (var f : futures) {
                Map.Entry<String, String> r = f.join();
                System.out.println("\n[" + r.getKey() + "]\n" + r.getValue());
            }
        }
    }

    // ---------- B. Voting ----------
    private static final String VOTE_PROMPT = """
            判断下面这条商品评价是否属于"虚假好评"。
            只输出 YES 或 NO。
            评价：%s""";

    private static final String[] REVIEWS = {
            "好评！好评！好评！五星好评，物流很快，老板人很好，下次还来，好评好评好评好评。",
            "键盘手感偏硬，用了两周后空格键开始有异响，联系客服换了一个。",
    };

    private static final Pattern YES_NO = Pattern.compile("YES|NO");

    private static String oneVote(String review, double temp) {
        String raw = Llm.text(String.format(VOTE_PROMPT, review), temp, 4);
        Matcher m = YES_NO.matcher(raw.toUpperCase());
        return m.find() ? m.group() : "?";
    }

    static void runVoting(int n) {
        System.out.println("\n=== B. Voting（每条评 " + n + " 票，不同温度制造多样性） ===");
        double[] temps = {0.0, 0.7, 1.0};
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (String r : REVIEWS) {
                for (double t : temps) {
                    futures.add(CompletableFuture.supplyAsync(() -> oneVote(r, t), pool));
                }
            }
            for (int i = 0; i < REVIEWS.length; i++) {
                List<String> votes = new ArrayList<>();
                for (int j = 0; j < n; j++) votes.add(futures.get(i * n + j).join());
                long yes = votes.stream().filter("YES"::equals).count();
                String verdict = yes > n / 2 ? "YES(虚假)" : "NO";
                String head = REVIEWS[i].length() > 30 ? REVIEWS[i].substring(0, 30) : REVIEWS[i];
                System.out.println("\n" + votes + " -> 票数 YES=" + yes + "/" + n + " 结论=" + verdict
                        + "\n  评价：" + head + "...");
            }
        }
    }

    public static void main(String[] args) {
        try { main0(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static void main0() {
        runSectioning();
        runVoting(3);
    }
}
