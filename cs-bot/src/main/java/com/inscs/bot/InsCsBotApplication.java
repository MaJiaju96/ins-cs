package com.inscs.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 客服机器人服务。
 *
 * <p>默认 profile <b>不连任何中间件</b>：直接 {@code mvn spring-boot:run} 就能起来，
 * 用内存规则 + 内置演示数据跑通「分流 → 域 Agent → 卡片/追问/trace」整条链。
 * 接 MariaDB/Redis/Nacos 是 {@code dev} profile 的事（见 docs/roadmap.md）。
 */
@SpringBootApplication
public class InsCsBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsCsBotApplication.class, args);
    }
}
