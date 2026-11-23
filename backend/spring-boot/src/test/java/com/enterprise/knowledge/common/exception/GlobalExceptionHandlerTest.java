
package com.enterprise.knowledge.common.exception;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBaseValidationException;
import com.enterprise.knowledge.knowledgebase.application.KnowledgeBaseNameConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.enterprise.knowledge.common.filter.RequestIdFilter;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturn409WhenKnowledgeBaseNameConflicts() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("KNOWLEDGE_BASE_NAME_CONFLICT"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldReturn500WhenUnexpectedExceptionOccurs() throws Exception {
        mockMvc.perform(get("/test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message")
                        .value(not(containsString("database"))))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
    @Test
    void shouldReturn422WhenKnowledgeBaseInputIsInvalid() throws Exception {
        mockMvc.perform(get("/test/validation"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("知识库名称必须包含1到50个字符"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldReturn500ForUnexpectedIllegalArgument() throws Exception {
        mockMvc.perform(get("/test/internal-argument"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/conflict")
        public void conflict() {
            throw new KnowledgeBaseNameConflictException("Duplicate name");
        }

        @GetMapping("/test/error")
        public void error() {
            throw new RuntimeException("Internal database details");
        }
        @GetMapping("/test/validation")
        public void validation() {
            throw new KnowledgeBaseValidationException(
                    "知识库名称必须包含1到50个字符"
            );
        }

        @GetMapping("/test/internal-argument")
        public void internalArgument() {
            throw new IllegalArgumentException("Internal configuration error");
        }
    }
}
