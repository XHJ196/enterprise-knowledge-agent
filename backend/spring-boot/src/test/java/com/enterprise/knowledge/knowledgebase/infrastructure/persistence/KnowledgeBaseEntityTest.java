package com.enterprise.knowledge.knowledgebase.infrastructure.persistence;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeBaseEntityTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"  企业制度简介，保留原文  "})
    void shouldPreserveBusinessFieldsDuringConversion(String description) {
        KnowledgeBase original = fixture(
                KnowledgeBase.Lifecycle.ACTIVE,
                description
        );

        KnowledgeBaseEntity entity = KnowledgeBaseEntity.fromNew(original);
        KnowledgeBase restored = entity.toDomain();

        assertEquals(original, restored);
    }

    @ParameterizedTest
    @EnumSource(
            value = KnowledgeBase.Lifecycle.class,
            names = {"DELETING", "DELETE_FAILED", "DELETED"}
    )
    void shouldRejectNonActiveStateForNewEntity(
            KnowledgeBase.Lifecycle lifecycle
    ) {
        KnowledgeBase knowledgeBase = fixture(lifecycle, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> KnowledgeBaseEntity.fromNew(knowledgeBase)
        );
    }

    private KnowledgeBase fixture(
            KnowledgeBase.Lifecycle lifecycle,
            String description
    ) {
        return new KnowledgeBase(
                UUID.fromString("20000000-0000-4000-8000-000000000001"),
                lifecycle == KnowledgeBase.Lifecycle.DELETED
                        ? null : "HR Policy",
                description,
                lifecycle,
                UUID.fromString("10000000-0000-4000-8000-000000000002"),
                Instant.parse("2026-11-24T10:00:00.123456Z"),
                Instant.parse("2026-11-26T11:00:00.654321Z")
        );
    }
}