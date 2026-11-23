
package com.enterprise.knowledge.knowledgebase.application;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import com.enterprise.knowledge.knowledgebase.infrastructure.persistence.InMemoryKnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBaseServiceTest {

    private KnowledgeBaseService service;
    private UUID creatorId;

    @BeforeEach
    void setUp() {
        InMemoryKnowledgeBaseRepository repository =
                new InMemoryKnowledgeBaseRepository();

        service = new KnowledgeBaseService(repository);
        creatorId = UUID.randomUUID();
    }

    @Test
    void shouldCreateKnowledgeBase() {
        KnowledgeBase result = service.create(
                "Engineering Docs",
                "Technical documentation",
                creatorId
        );

        assertNotNull(result.id());
        assertEquals("Engineering Docs", result.name());
        assertEquals(creatorId, result.createdBy());
    }

    @Test
    void shouldTrimKnowledgeBaseName() {
        KnowledgeBase result = service.create(
                "  Engineering Docs  ",
                null,
                creatorId
        );

        assertEquals("Engineering Docs", result.name());
    }

    @Test
    void shouldRejectDuplicateNameIgnoringCase() {
        service.create("Engineering Docs", null, creatorId);

        assertThrows(
                KnowledgeBaseNameConflictException.class,
                () -> service.create(
                        "engineering docs",
                        null,
                        creatorId
                )
        );
    }

    @Test
    void shouldReturnAllKnowledgeBases() {
        service.create("Engineering Docs", null, creatorId);
        service.create("Product Docs", null, creatorId);

        List<KnowledgeBase> results = service.findAll();

        assertEquals(2, results.size());
    }
}
