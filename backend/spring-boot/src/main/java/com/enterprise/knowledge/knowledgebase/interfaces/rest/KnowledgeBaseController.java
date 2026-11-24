
package com.enterprise.knowledge.knowledgebase.interfaces.rest;

import com.enterprise.knowledge.knowledgebase.application.KnowledgeBasePage;
import com.enterprise.knowledge.knowledgebase.application.KnowledgeBaseService;
import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.enterprise.knowledge.common.api.ApiResponse;
import com.enterprise.knowledge.common.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import com.enterprise.knowledge.common.exception.RequestValidationException;
import com.enterprise.knowledge.common.exception.UnexpectedRequestBodyException;
import java.io.IOException;
import java.util.Set;
import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    private static final Set<String> LIST_QUERY_PARAMETERS =
            Set.of("page", "pageSize");

    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }
    @PostMapping
    public ResponseEntity<ApiResponse<KnowledgeBaseResponse>> create(
            @Valid @RequestBody CreateKnowledgeBaseRequest request,
            HttpServletRequest httpRequest
    ) {
        // 临时使用固定演示用户 ID
        // 后续由 Authentication 模块提供真实登录用户
        UUID demoUserId = UUID.fromString(
                "10000000-0000-4000-8000-000000000002"
        );

        KnowledgeBase knowledgeBase = service.create(
                request.name(),
                request.description(),
                demoUserId
        );

        KnowledgeBaseResponse response = KnowledgeBaseResponse.from(knowledgeBase);

        String requestId = (String) httpRequest.getAttribute(
                RequestIdFilter.HEADER_NAME
        );

        ApiResponse<KnowledgeBaseResponse> apiResponse =
                ApiResponse.success(response, requestId);

        URI location = URI.create(
                "/api/v1/knowledge-bases/" + knowledgeBase.id()
        );

        return ResponseEntity.created(location).body(apiResponse);
    }

    @GetMapping

    public ResponseEntity<ApiResponse<KnowledgeBasePageResponse>> list(
            @Valid @ModelAttribute KnowledgeBaseListRequest request,
            HttpServletRequest httpRequest
    ) throws IOException {
        if (httpRequest.getInputStream().read() != -1) {
            throw new UnexpectedRequestBodyException("该接口不接受请求体");
        }
        boolean hasUnknownQueryParameter =
                httpRequest.getParameterMap().keySet().stream()
                        .anyMatch(name -> !LIST_QUERY_PARAMETERS.contains(name));

        if (hasUnknownQueryParameter) {
            throw new RequestValidationException("请求包含未知查询参数");
        }
        // 当前为管理员演示范围，尚未接入认证和权限校验。
        KnowledgeBasePage page = service.findPageForAdminDemo(
                request.page(),
                request.pageSize()
        );

        String requestId = (String) httpRequest.getAttribute(
                RequestIdFilter.HEADER_NAME
        );

        KnowledgeBasePageResponse response =
                KnowledgeBasePageResponse.from(page);

        return ResponseEntity.ok(
                ApiResponse.success(response, requestId)
        );
    }
}
