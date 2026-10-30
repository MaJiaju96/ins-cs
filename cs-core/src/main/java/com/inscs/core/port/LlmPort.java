package com.inscs.core.port;

/**
 * 模型端口。
 *
 * <p>只认一个 OpenAI 兼容端点——这是为了让内核零外网依赖、可全离线替换。
 * 换模型（本地 4B / 云端 DeepSeek）只换实现，不改调用方。
 *
 * <p>骨架阶段只有阻塞式 {@link #complete}。P1 加流式时<b>新增</b>方法（例如
 * {@code stream(Request, Consumer<String>) }），不要改本方法的语义，
 * 否则引用它做确定性断言的测试会一起塌。
 */
public interface LlmPort {

    /**
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户侧拼装后的上下文
     * @param temperature  温度；意图/抽取类调用一律给低值（0.1），别让它发挥
     */
    record Request(String systemPrompt, String userPrompt, double temperature) {
    }

    /** 返回文本与用量。用量必须真实上报，成本指标按它算，不按字符估算。 */
    record Completion(String text, int promptTokens, int completionTokens) {
    }

    Completion complete(Request request);
}
