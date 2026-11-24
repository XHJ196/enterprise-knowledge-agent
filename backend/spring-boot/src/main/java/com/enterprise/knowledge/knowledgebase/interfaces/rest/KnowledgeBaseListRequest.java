package com.enterprise.knowledge.knowledgebase.interfaces.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record KnowledgeBaseListRequest(
        @Min(value = 1, message = "页码必须大于等于1")
        Integer page,

        @Min(value = 1, message = "每页数量必须大于等于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize
) {
    public KnowledgeBaseListRequest {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}