package com.datn.mcq.enums;

import com.datn.mcq.config.WebConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DbEnumBindingTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    enum PlainEnum {
        ALPHA,
        BETA
    }

    record Payload(Strategy strategy, ReviewDecision decision, PlainEnum plain) {}

    @Configuration
    @EnableWebMvc
    @Import({WebConfig.class, EchoController.class})
    static class TestWebConfig {}

    @RestController
    static class EchoController {
        @PostMapping("/echo")
        Payload echo(
                @RequestParam Strategy paramStrategy,
                @RequestParam PlainEnum paramPlain,
                @RequestBody Payload body) {
            assertEquals(paramStrategy, body.strategy());
            assertEquals(paramPlain, body.plain());
            return body;
        }
    }

    @BeforeEach
    void setUp() {
        this.context = new AnnotationConfigWebApplicationContext();
        this.context.register(TestWebConfig.class);
        this.context.refresh();
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    @AfterEach
    void tearDown() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    void webConfigShouldBindDbEnumByDbValueAndKeepStandardEnumWorking() throws Exception {
        mockMvc.perform(post("/echo")
                        .param("paramStrategy", "few-shot-structured")
                        .param("paramPlain", "ALPHA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\":\"few-shot-structured\",\"decision\":\"approved_as_is\",\"plain\":\"ALPHA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("few-shot-structured"))
                .andExpect(jsonPath("$.decision").value("approved_as_is"))
                .andExpect(jsonPath("$.plain").value("ALPHA"));
    }

    @Test
    void invalidDbValueShouldThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> DbEnum.fromDbValue(Strategy.class, "invalid-strategy"));
    }
}
