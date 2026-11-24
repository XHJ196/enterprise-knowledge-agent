package com.enterprise.knowledge.knowledgebase.interfaces.rest;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import com.enterprise.knowledge.common.exception.GlobalExceptionHandler;
import com.enterprise.knowledge.common.filter.RequestIdFilter;
import com.enterprise.knowledge.knowledgebase.application.KnowledgeBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeBaseControllerTest {

    private MockMvc mockMvc;
    private KnowledgeBaseService service;

    @BeforeEach
    void setUp() {
        service = mock(KnowledgeBaseService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new KnowledgeBaseController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":null}",
            "{\"name\":\"\"}",
            "{\"name\":\"   \"}"
    })
    void shouldRejectInvalidNameBeforeCallingService(String json)
            throws Exception {

        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(result -> assertInstanceOf(
                        MethodArgumentNotValidException.class,
                        result.getResolvedException()
                ));

        verifyNoInteractions(service);
    }
    @ParameterizedTest
    @CsvSource({
            "51, 500",
            "50, 501"
    })
    void shouldRejectTooLongInputBeforeCallingService(
            int nameCodePoints,
            int descriptionCodePoints
    ) throws Exception {

        String json = JsonMapper.builder().build().writeValueAsString(
                Map.of(
                        "name", "😀".repeat(nameCodePoints),
                        "description", "😀".repeat(descriptionCodePoints)
                )
        );

        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(result -> assertInstanceOf(
                        MethodArgumentNotValidException.class,
                        result.getResolvedException()
                ));

        verifyNoInteractions(service);
    }
    @Test
    void shouldAcceptCodePointLimitsAndPassTrimmedNameToService()
            throws Exception {

        String name = "😀".repeat(50);
        String description = "😀".repeat(500);
        UUID creatorId = UUID.fromString(
                "10000000-0000-4000-8000-000000000002"
        );

        KnowledgeBase knowledgeBase = KnowledgeBase.create(
                name,
                description,
                creatorId
        );

        when(service.create(name, description, creatorId))
                .thenReturn(knowledgeBase);

        String json = JsonMapper.builder().build().writeValueAsString(
                Map.of(
                        "name", "  " + name + "  ",
                        "description", description
                )
        );

        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.description").value(description))
                .andExpect(header().string(
                        "Location",
                        "/api/v1/knowledge-bases/" + knowledgeBase.id()
                ));

        verify(service).create(name, description, creatorId);
    }
}