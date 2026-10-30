package com.inscs.core.ir;

import java.util.List;
import java.util.Map;

/**
 * 输出侧：这一轮我们<b>说了什么</b>。
 *
 * <p>文本不是唯一载体。结构化卡片、编号追问、转人工包、隐藏参数与正文是**平级公民**，在同一轮流里下发。
 * 这解决了两件事：
 * <ol>
 *   <li>模型输出只能是字符串的根本限制——卡片的字段随流下发，前端不用再发一次请求去查（省往返）；</li>
 *   <li>可观测性内嵌在协议里——{@link Hidden} 携带路由结论、置信度、命中的规则，
 *       不依赖日志也能事后判断「它为什么这么答」。</li>
 * </ol>
 *
 * <p>注意：这是**领域类型**，不是线格式。线格式（wire format）在 cs-bot 里显式映射，
 * 以便内部演进不破坏前端契约。
 */
public sealed interface Block {

    /** 线格式里的判别字段，与 sealed 分支一一对应。 */
    String kind();

    /** 正文。 */
    record Text(String text) implements Block {
        public String kind() {
            return "text";
        }
    }

    /**
     * 结构化卡片：订单、保单、退保试算结果等。
     *
     * @param cardType 卡片类型，前端按它选渲染组件
     * @param data     卡片数据，保持扁平，避免嵌套过深导致前端解析成本
     */
    record Card(String cardType, Map<String, Object> data) implements Block {
        public Card {
            data = data == null ? Map.of() : Map.copyOf(data);
        }

        public String kind() {
            return "card";
        }
    }

    /**
     * 编号候选——把不确定性转成一次低成本交互。
     *
     * <p>用户回「2」即可走确定性分支，不必再过一次模型。在弱网 + 按 token 计费的场景下，
     * 这是性价比最高的一种交互。
     */
    record Suggestion(List<String> options) implements Block {
        public Suggestion {
            options = options == null ? List.of() : List.copyOf(options);
        }

        public String kind() {
            return "suggestion";
        }
    }

    /** 思考过程（可展示的推理摘要，不是模型内部思维链）。 */
    record Thinking(String text) implements Block {
        public String kind() {
            return "thinking";
        }
    }

    /**
     * 转人工交接包：给坐席看的摘要，不是给用户看的。
     *
     * @param reason  为什么转
     * @param summary 已确认的事实摘要（订单号、保单号、已尝试的步骤）
     * @param queue   目标技能组
     */
    record Handoff(String reason, String summary, String queue) implements Block {
        public String kind() {
            return "handoff";
        }
    }

    /** 隐藏参数：随流下发但不展示，前端可埋点、可发起二次调用。 */
    record Hidden(Map<String, Object> data) implements Block {
        public Hidden {
            data = data == null ? Map.of() : Map.copyOf(data);
        }

        public String kind() {
            return "hidden";
        }
    }

    static Text text(String text) {
        return new Text(text);
    }
}
