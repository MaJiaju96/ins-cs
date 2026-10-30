package com.inscs.core.port;

import com.inscs.core.ir.RetrievalHit;

import java.util.List;

/**
 * 检索端口。
 *
 * <p><b>只返回 top-K 的 id 与分数，不返回整段正文</b>——正文由上层按需取。
 * 原因：弱网链路上一条 2KB 的条款片段 × 10 条就是 20KB，占用宝贵的上行预算，
 * 而真正要喂给模型的往往只是命中最好的那 2–3 条。
 *
 * <p>实现方负责把向量与索引放在本地盘（5000 chunk × 1024 维 ≈ 20MB，
 * 走穿透链路要 80 秒，绝不能远程取）。
 */
public interface RetrievalPort {

    List<RetrievalHit> search(String query, int topK);
}
