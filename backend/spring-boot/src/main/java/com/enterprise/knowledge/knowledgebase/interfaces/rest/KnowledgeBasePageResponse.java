package com.enterprise.knowledge.knowledgebase.interfaces.rest;

import com.enterprise.knowledge.knowledgebase.application.KnowledgeBasePage;

import java.util.List;

public record KnowledgeBasePageResponse(
        List<KnowledgeBaseResponse> items,
        int page,
        int pageSize,
        long total,
        boolean hasNext
) {
    public static KnowledgeBasePageResponse from(KnowledgeBasePage page) {
        return new KnowledgeBasePageResponse(
                page.items().stream()
                        .map(KnowledgeBaseResponse::from)
                        .toList(),
                page.page(),
                page.pageSize(),
                page.total(),
                page.hasNext()
        );
    }
}