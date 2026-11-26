package com.enterprise.knowledge.knowledgebase.infrastructure.persistence;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_bases", schema = "core")
public class KnowledgeBaseEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", length = 50)
    private String name;

    @Column(
            name = "name_key",
            columnDefinition = "text",
            insertable = false,
            updatable = false
    )
    private String nameKey;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle", nullable = false, length = 20)
    private KnowledgeBase.Lifecycle lifecycle;

    @Column(name = "delete_error_code", columnDefinition = "text")
    private String deleteErrorCode;

    @Column(name = "delete_error_detail", columnDefinition = "text")
    private String deleteErrorDetail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected KnowledgeBaseEntity() {
    }
    public static KnowledgeBaseEntity fromNew(KnowledgeBase knowledgeBase) {
        if (knowledgeBase.lifecycle() != KnowledgeBase.Lifecycle.ACTIVE) {
            throw new IllegalArgumentException(
                    "新建知识库必须处于 ACTIVE 状态"
            );
        }

        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.id = knowledgeBase.id();
        entity.name = knowledgeBase.name();
        entity.description = knowledgeBase.description();
        entity.createdBy = knowledgeBase.createdBy();
        entity.lifecycle = knowledgeBase.lifecycle();
        entity.createdAt = knowledgeBase.createdAt();
        entity.updatedAt = knowledgeBase.updatedAt();

        return entity;
    }

    public KnowledgeBase toDomain() {
        return new KnowledgeBase(
                id,
                name,
                description,
                lifecycle,
                createdBy,
                createdAt,
                updatedAt
        );
    }
}