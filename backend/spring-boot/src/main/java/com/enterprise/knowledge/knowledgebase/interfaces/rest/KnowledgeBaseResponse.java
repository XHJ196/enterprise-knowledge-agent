package com.enterprise.knowledge.knowledgebase.interfaces.rest;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeBaseResponse(
        UUID id,
        String name,
        String description,
        String lifecycle,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static KnowledgeBaseResponse from(KnowledgeBase kb) {
        return new KnowledgeBaseResponse(
                kb.id(),
                kb.name(),
                kb.description(),
                kb.lifecycle().name(),
                kb.createdBy(),
                kb.createdAt(),
                kb.updatedAt()
        );
    }
}