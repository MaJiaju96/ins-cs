package com.inscs.bot.config;

import com.inscs.bot.stub.AskBackRouter;
import com.inscs.bot.stub.InMemoryRouteRuleProvider;
import com.inscs.bot.stub.LoggingEventPublisher;
import com.inscs.bot.stub.LoggingTraceSink;
import com.inscs.core.agent.SubAgent;
import com.inscs.core.agent.SubAgentRegistry;
import com.inscs.core.port.EventPublisher;
import com.inscs.core.port.RouteRuleProvider;
import com.inscs.core.port.RouterPort;
import com.inscs.core.port.TraceSink;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * P0 桩装配。P1 逐个替换成 MariaDB / Redis / MQ 实现时，<b>只改这个类</b>。
 *
 * <p>注意 {@link #subAgentRegistry(List)}：所有 {@link SubAgent} 的 Spring Bean 自动注册进注册表。
 * 新增一个业务域 = 写一个 @Component 实现 SubAgent + route_rule 加一行，没有第三处要改。
 */
@Configuration
public class BotConfig {

    @Bean
    RouterPort routerPort() {
        // P0：不做模型调用，分流兜底一律反问。接 LLM 后换实现。
        return new AskBackRouter();
    }

    @Bean
    RouteRuleProvider routeRuleProvider() {
        return new InMemoryRouteRuleProvider();
    }

    @Bean
    TraceSink traceSink() {
        return new LoggingTraceSink();
    }

    @Bean
    EventPublisher eventPublisher() {
        return new LoggingEventPublisher();
    }

    @Bean
    SubAgentRegistry subAgentRegistry(List<SubAgent> agents) {
        SubAgentRegistry registry = new SubAgentRegistry();
        agents.forEach(registry::register);
        return registry;
    }
}
