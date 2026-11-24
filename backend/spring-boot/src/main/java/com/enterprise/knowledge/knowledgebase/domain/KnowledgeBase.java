package com.enterprise.knowledge.knowledgebase.domain;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeBase(
        UUID id,
        String name,
        String description,
        Lifecycle lifecycle,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public enum Lifecycle {
        ACTIVE,
        DELETING,
        DELETE_FAILED,
        DELETED
    }

    public static KnowledgeBase create(
            String name,
            String description,
            UUID createdBy
    ) {
        String normalizedName = name == null ? "" : name.trim();

        int nameLength = normalizedName.codePointCount(
                0,
                normalizedName.length()
        );

        if (nameLength < 1 || nameLength > 50) {
            throw new KnowledgeBaseValidationException(
                    "知识库名称必须包含1到50个字符"
            );
        }

        if (description != null
                && description.codePointCount(0, description.length()) > 500) {
            throw new KnowledgeBaseValidationException(
                    "简介不能超过500个字符"
            );
        }

        if (createdBy == null) {
            throw new IllegalArgumentException(
                    "创建者的ID不能为空"
            );
        }

        Instant now = Instant.now();

        return new KnowledgeBase(
                UUID.randomUUID(),
                normalizedName,
                description,
                Lifecycle.ACTIVE,
                createdBy,
                now,
                now
        );
    }
}