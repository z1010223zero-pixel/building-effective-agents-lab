package lab.aea;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.function.Function;

/** 一个工具 = 给模型看的说明书 + 本地真实实现。对应 Python 里 TOOLS + REGISTRY 的合并写法。 */
public record Tool(String name, String description, ObjectNode parameters,
                   Function<Map<String, Object>, String> impl) {

    public String run(Map<String, Object> args) {
        return impl.apply(args);
    }
}
