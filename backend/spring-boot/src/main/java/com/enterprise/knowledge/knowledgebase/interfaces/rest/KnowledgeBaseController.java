
package com.enterprise.knowledge.knowledgebase.interfaces.rest;

import com.enterprise.knowledge.knowledgebase.application.KnowledgeBaseService;
import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.enterprise.knowledge.common.api.ApiResponse;
import com.enterprise.knowledge.common.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }
    @PostMapping
    public ResponseEntity<ApiResponse<KnowledgeBaseResponse>> create(
            @RequestBody CreateKnowledgeBaseRequest request,
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
}
