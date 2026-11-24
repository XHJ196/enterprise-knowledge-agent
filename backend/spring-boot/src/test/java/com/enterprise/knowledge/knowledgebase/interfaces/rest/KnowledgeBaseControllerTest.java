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
import com.enterprise.knowledge.knowledgebase.application.KnowledgeBasePage;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @Test
    void shouldListWithDefaultPagination() throws Exception {
        KnowledgeBasePage page = new KnowledgeBasePage(
                List.of(), 1, 20, 0, false
        );

        when(service.findPageForAdminDemo(1, 20))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verify(service).findPageForAdminDemo(1, 20);
    }
    @Test
    void shouldListWithExplicitPaginationAndMapItems() throws Exception {
        UUID creatorId = UUID.fromString(
                "10000000-0000-4000-8000-000000000002"
        );

        KnowledgeBase knowledgeBase = KnowledgeBase.create(
                "HR Policy",
                "企业制度演示库",
                creatorId
        );

        KnowledgeBasePage page = new KnowledgeBasePage(
                List.of(knowledgeBase), 2, 2, 3, false
        );

        when(service.findPageForAdminDemo(2, 2))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/knowledge-bases")
                        .param("page", "2")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id")
                        .value(knowledgeBase.id().toString()))
                .andExpect(jsonPath("$.data.items[0].name")
                        .value("HR Policy"))
                .andExpect(jsonPath("$.data.items[0].description")
                        .value("企业制度演示库"))
                .andExpect(jsonPath("$.data.items[0].lifecycle")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].createdBy")
                        .value(creatorId.toString()))
                .andExpect(jsonPath("$.data.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verify(service).findPageForAdminDemo(2, 2);
    }
    @ParameterizedTest
    @CsvSource({
            "0, 20",
            "-1, 20",
            "1, 0",
            "1, -1",
            "1, 101",
            "abc, 20",
            "1, abc",
            "2147483648, 20",
            "1, 2147483648"
    })
    void shouldRejectInvalidPaginationBeforeCallingService(
            String page,
            String pageSize
    ) throws Exception {
        mockMvc.perform(get("/api/v1/knowledge-bases")
                        .param("page", page)
                        .param("pageSize", pageSize))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verifyNoInteractions(service);
    }
    @ParameterizedTest
    @ValueSource(strings = {"size", "sort", "lifecycle"})
    void shouldRejectUnknownListQueryParameter(String parameter)
            throws Exception {

        mockMvc.perform(get("/api/v1/knowledge-bases")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param(parameter, "unexpected"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("请求包含未知查询参数"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verifyNoInteractions(service);
    }
    @ParameterizedTest
    @ValueSource(strings = {"{}", "plain text", " "})
    void shouldRejectNonEmptyListRequestBody(String body) throws Exception {
        mockMvc.perform(get("/api/v1/knowledge-bases")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("该接口不接受请求体"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verifyNoInteractions(service);
    }
}