package com.inscs.core.agent;

import com.inscs.core.ir.Domain;
import com.inscs.core.ir.TurnContext;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子域 Agent 注册表。
 *
 * <p>刻意做得很薄：一个 Map，没有工厂、没有责任链、没有优先级打分。
 * 参考系统有 {@code TargetBridgeFactory} 注册表是因为它有 7 种目标 Agent 类型
 * （本地 Oxy / JoyAgent / A2A / Xinhe / Python / Spring Bean / 静态数据）。
 * 我们只有「本地 Bean」一种，建注册表工厂就是空转。
 *
 * <p>等真的出现第二种目标类型（例如远程 Agent 服务）时再抽接口——那时才有第二个实现来验证抽象。
 */
public final class SubAgentRegistry {

    private final Map<Domain, SubAgent> agents = new ConcurrentHashMap<>(new EnumMap<>(Domain.class));

    public SubAgentRegistry register(SubAgent agent) {
        SubAgent previous = agents.putIfAbsent(agent.domain(), agent);
        if (previous != null) {
            throw new IllegalStateException("duplicate SubAgent for domain " + agent.domain().code()
                    + ": " + previous.getClass().getName() + " vs " + agent.getClass().getName());
        }
        return this;
    }

    public Optional<SubAgent> find(Domain domain) {
        return Optional.ofNullable(agents.get(domain));
    }

    /**
     * 找出能接管本轮的 Agent。
     *
     * <p>路由已经判了域，但该域可能未实现（例如 P1 的延保），此时返回 empty，
     * 由调用方走「优雅降级 + 记 route.miss」，而不是抛异常。
     */
    public Optional<SubAgent> resolve(TurnContext ctx) {
        if (ctx == null || ctx.route() == null) {
            return Optional.empty();
        }
        return find(ctx.route().domain()).filter(a -> a.canHandle(ctx));
    }

    public boolean isEmpty() {
        return agents.isEmpty();
    }

    public java.util.Set<Domain> registeredDomains() {
        return java.util.Set.copyOf(agents.keySet());
    }
}
