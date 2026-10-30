package com.inscs.core.ir;

/**
 * 一条检索命中。
 *
 * <p>{@link #chunkId} 必须稳定且可复现——评测台的检索断言靠它：
 * <ul>
 *   <li>必含断言：期望命中的 chunk 在 topK 内；</li>
 *   <li>禁含断言：不该被召回的 chunk（例如相邻产品的条款）不得出现在 topK；</li>
 *   <li>recall@k：用 id 集合比对，不用文本相似度。</li>
 * </ul>
 *
 * @param snippet 仅供展示与人工排查，不进断言
 */
public record RetrievalHit(
        String chunkId,
        String docId,
        double score,
        String snippet
) {
    public RetrievalHit {
        if (chunkId == null || chunkId.isBlank()) {
            throw new IllegalArgumentException("chunkId must not be blank");
        }
    }
}
