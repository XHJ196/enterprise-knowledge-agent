package com.enterprise.knowledge.knowledgebase.interfaces.rest;
import tools.jackson.databind.exc.MismatchedInputException;
import com.enterprise.knowledge.knowledgebase.application.KnowledgeBaseService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeBaseJsonIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeBaseService service;

    @ParameterizedTest
    @ValueSource(strings = {"role", "createdBy", "lifecycle"})
    void shouldRejectUnknownJsonFieldBeforeCallingService(String field)
            throws Exception {
        String json = """
                {
                  "name": "Day14 Unknown Field",
                  "description": "未知字段验收",
                  "%s": "unexpected"
                }
                """.formatted(field);

        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(result -> assertInstanceOf(
                        UnrecognizedPropertyException.class,
                        result.getResolvedException().getCause()
                ));

        verifyNoInteractions(service);
    }
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":123}",
            "{\"name\":1.5}",
            "{\"name\":true}",
            "{\"name\":\"Day14 Strict JSON\",\"description\":123}",
            "{\"name\":\"Day14 Strict JSON\",\"description\":1.5}",
            "{\"name\":\"Day14 Strict JSON\",\"description\":false}"
    })
    void shouldRejectNonStringJsonFields(String json) throws Exception {
        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(result -> assertInstanceOf(
                        MismatchedInputException.class,
                        result.getResolvedException().getCause()
                ));

        verifyNoInteractions(service);
    }
}