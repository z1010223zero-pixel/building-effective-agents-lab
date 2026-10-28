package lab.aea;

import java.util.LinkedHashMap;
import java.util.Map;

/** 命令行入口：java -jar target/...-jar-with-dependencies.jar [p0..p6|all] */
public final class Main {

    interface ThrowingRunnable { void run() throws Exception; }

    private static final Map<String, ThrowingRunnable> RUNNERS = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        String which = args.length > 0 ? args[0].toLowerCase() : "all";

        RUNNERS.put("p0", P0Augmented::main0);
        RUNNERS.put("p1", P1Chain::main0);
        RUNNERS.put("p2", P2Routing::main0);
        RUNNERS.put("p3", P3Parallel::main0);
        RUNNERS.put("p4", P4Orchestrator::main0);
        RUNNERS.put("p5", P5Evaluator::main0);
        RUNNERS.put("p6", P6Agent::main0);

        if (!which.equals("all")) {
            ThrowingRunnable r = RUNNERS.get(which);
            if (r == null) {
                System.out.println("用法: java -jar <jar> [all|p0|p1|p2|p3|p4|p5|p6]");
                return;
            }
            r.run();
            return;
        }
        for (var e : RUNNERS.entrySet()) {
            System.out.println();
            System.out.println("############ " + e.getKey() + " ############");
            e.getValue().run();
        }
    }
}
