package com.inscs.bot.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 流式接口的契约测试：<b>事件名字与顺序</b>就是前后端唯一的约定，所以它值得一个测试。
 *
 * <p>顺序断言比内容断言更重要：前端是「收到 route 先画徽标、收到 block 就追加」，
 * 一旦顺序反了（比如 block 先到），界面会先闪出一段没有归属的文字。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("退保：route 先到，随后是 block，最后 done")
    void streamsRouteThenBlocksThenDone() throws Exception {
        String body = body("c-stream-1", 1, "我想退保");

        MvcResult started = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        String sse = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int route = sse.indexOf("event:route");
        int block = sse.indexOf("event:block");
        int done = sse.indexOf("event:done");

        assertThat(route).as("必须有 route 事件").isGreaterThanOrEqualTo(0);
        assertThat(block).as("必须有 block 事件").isGreaterThanOrEqualTo(0);
        assertThat(done).as("必须有 done 事件").isGreaterThanOrEqualTo(0);
        assertThat(route).as("route 必须早于第一个 block").isLessThan(block);
        assertThat(block).as("done 必须在 block 之后").isLessThan(done);

        assertThat(sse).contains("policy.surrender").contains("HIT_RULE");
    }

    @Test
    @DisplayName("认不出的话：route 是 ASK_BACK，仍然有 block 与 done（降级不是错误）")
    void askBackStillCompletesNormally() throws Exception {
        String body = body("c-stream-2", 1, "今天天气不错");

        MvcResult started = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        String sse = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(sse).contains("ASK_BACK");
        assertThat(sse).contains("event:done");
        // 业务降级打的是普通事件，不该出现 error 帧
        assertThat(sse).doesNotContain("event:error");
    }

    private static String body(String conversationId, long seq, String message) {
        return """
                {"conversationId":"%s","seq":%d,"message":"%s"}
                """.formatted(conversationId, seq, message);
    }
}
