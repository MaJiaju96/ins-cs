package com.inscs.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 评测台（cs-lab）—— <b>P2 才开始长肉</b>，现在只占位。
 *
 * <p>它存在的意义在骨架期就很明确：cs-core 的 IR 有第二个消费者。
 * 只要这个坐标系在，IR 的字段设计就不会退化成「只够前端渲染」。
 *
 * <p>P2 的第一个功能不是跑分，而是 <b>badcase 收集箱</b>：
 * 订阅 {@code cs.turn.completed} / {@code cs.badcase.marked}，把人工标记的坏例存下来。
 * 评测集是从坏例里长出来的，不是先编 60 条黄金集再去找理由。
 */
@SpringBootApplication
@RestController
public class LabApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabApplication.class, args);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "cs-lab", "stage", "skeleton-only");
    }
}
