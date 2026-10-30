package com.inscs.bot.config;

import com.inscs.core.ConversationService;
import com.inscs.core.agent.SubAgentRegistry;
import com.inscs.core.port.EventPublisher;
import com.inscs.core.port.RouteRuleProvider;
import com.inscs.core.port.RouterPort;
import com.inscs.core.port.TraceSink;
import com.inscs.core.route.RouteMatcher;
import com.inscs.core.route.SimpleRouteMatcher;
import com.inscs.core.route.TriageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内核装配。整个 Spring 适配层就这一个类——内核（cs-core）不认识 Spring，
 * 这里负责把契约的桩换成正经实现。
 *
 * <p>P0 用桩实现打通链路；P1 逐个换成 MariaDB / Redis / MQ 实现，<b>内核代码一行不用改</b>。
 * 这就是「内核零依赖」换来的东西。
 */
@Configuration
public class CoreConfig {

    @Bean
    RouteMatcher routeMatcher() {
        return new SimpleRouteMatcher();
    }

    @Bean
    TriageService triageService(RouteMatcher routeMatcher, RouterPort routerPort) {
        return new TriageService(routeMatcher, routerPort);
    }

    @Bean
    ConversationService conversationService(TriageService triageService,
                                            RouteRuleProvider routeRuleProvider,
                                            SubAgentRegistry registry,
                                            TraceSink traceSink,
                                            EventPublisher events) {
        return new ConversationService(triageService, routeRuleProvider, registry, traceSink, events);
    }
}
