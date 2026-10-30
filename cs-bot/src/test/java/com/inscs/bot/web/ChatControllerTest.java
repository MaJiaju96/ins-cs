package com.inscs.bot.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端冒烟：一次 HTTP 进来，能不能拿到「规则命中 + 文本 + 选项 + trace」。
 *
 * <p>这条测试的价值不在覆盖率，而在<b>它就是验收标准</b>：
 * 哪天有人把 Block 换成 Map、把 route 从响应里拿掉、或让 span 消失，这里会先红。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("退保提问 → 规则命中经纪域 → 返回正文+编号候选+可见 trace")
    void surrenderTurn() throws Exception {
        String body = """
                {"conversationId":"c-1","seq":1,"userId":"u-1","channel":"web","message":"我想退保"}
                """;

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("c-1"))
                .andExpect(jsonPath("$.seq").value(1))
                .andExpect(jsonPath("$.route.domain").value("broker"))
                .andExpect(jsonPath("$.route.state").value("HIT_RULE"))
                .andExpect(jsonPath("$.route.intent").value("policy.surrender"))
                .andExpect(jsonPath("$.blocks[0].kind").value("text"))
                .andExpect(jsonPath("$.blocks[*].kind").value(org.hamcrest.Matchers.hasItem("suggestion")))
                .andExpect(jsonPath("$.spans[0].type").value("ROUTE"));
    }

    @Test
    @DisplayName("订单号正则命中 → 返回订单卡片")
    void orderCardTurn() throws Exception {
        String body = """
                {"conversationId":"c-2","seq":1,"message":"订单 A1024 现在什么状态"}
                """;

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.intent").value("order.query"))
                .andExpect(jsonPath("$.blocks[*].kind").value(org.hamcrest.Matchers.hasItem("card")))
                .andExpect(jsonPath("$.blocks[1].cardType").value("order"))
                .andExpect(jsonPath("$.blocks[1].data.orderNo").value("A1024"));
    }

    @Test
    @DisplayName("没命中任何规则 → ASK_BACK 反问，不失败")
    void askBackTurn() throws Exception {
        String body = """
                {"conversationId":"c-3","seq":1,"message":"你好呀"}
                """;

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.state").value("ASK_BACK"))
                .andExpect(jsonPath("$.blocks[0].kind").value("text"));
    }
}
