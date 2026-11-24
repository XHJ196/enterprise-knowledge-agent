
package com.enterprise.knowledge.knowledgebase.application;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import com.enterprise.knowledge.knowledgebase.infrastructure.persistence.InMemoryKnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBaseRepository;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
class KnowledgeBaseServiceTest {

    private KnowledgeBaseService service;
    private UUID creatorId;
    private KnowledgeBase queryFixture(
            String id,
            KnowledgeBase.Lifecycle lifecycle,
            Instant createdAt
    ) {
        return new KnowledgeBase(
                UUID.fromString(id),
                "Fixture " + id,
                null,
                lifecycle,
                creatorId,
                createdAt,
                createdAt
        );
    }
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
    @Test
    void shouldFilterDeletedAndSortAdminDemoResults() {
        KnowledgeBaseRepository repository =
                mock(KnowledgeBaseRepository.class);
        KnowledgeBaseService queryService =
                new KnowledgeBaseService(repository);

        Instant olderTime = Instant.parse("2026-11-20T10:00:00Z");
        Instant newerTime = Instant.parse("2026-11-21T10:00:00Z");

        KnowledgeBase older = queryFixture(
                "90000000-0000-4000-8000-000000000001",
                KnowledgeBase.Lifecycle.DELETING,
                olderTime
        );

        KnowledgeBase newerLowId = queryFixture(
                "70000000-0000-4000-8000-000000000001",
                KnowledgeBase.Lifecycle.ACTIVE,
                newerTime
        );

        KnowledgeBase newerHighId = queryFixture(
                "80000000-0000-4000-8000-000000000001",
                KnowledgeBase.Lifecycle.DELETE_FAILED,
                newerTime
        );

        KnowledgeBase deleted = queryFixture(
                "60000000-0000-4000-8000-000000000001",
                KnowledgeBase.Lifecycle.DELETED,
                newerTime.plusSeconds(60)
        );

        when(repository.findAll()).thenReturn(
                List.of(deleted, newerHighId, older, newerLowId)
        );

        assertEquals(
                List.of(newerLowId, newerHighId, older),
                queryService.findAllForAdminDemo()
        );
    }

    @Test
    void shouldReturnEmptyAdminDemoListWhenRepositoryIsEmpty() {
        assertTrue(service.findAllForAdminDemo().isEmpty());
    }
    @ParameterizedTest
    @CsvSource({
            "1, 2, 0, 2, true",
            "2, 2, 2, 3, false",
            "3, 2, 3, 3, false",
            "1, 3, 0, 3, false",
            "2147483647, 100, 3, 3, false"
    })
    void shouldPaginateVisibleAdminDemoResults(
            int page,
            int pageSize,
            int expectedFrom,
            int expectedTo,
            boolean expectedHasNext
    ) {
        KnowledgeBaseRepository repository =
                mock(KnowledgeBaseRepository.class);
        KnowledgeBaseService queryService =
                new KnowledgeBaseService(repository);

        Instant newerTime = Instant.parse("2026-11-21T10:00:00Z");

        List<KnowledgeBase> expectedOrder = List.of(
                queryFixture(
                        "70000000-0000-4000-8000-000000000001",
                        KnowledgeBase.Lifecycle.ACTIVE,
                        newerTime
                ),
                queryFixture(
                        "80000000-0000-4000-8000-000000000001",
                        KnowledgeBase.Lifecycle.DELETE_FAILED,
                        newerTime
                ),
                queryFixture(
                        "90000000-0000-4000-8000-000000000001",
                        KnowledgeBase.Lifecycle.DELETING,
                        newerTime.minusSeconds(60)
                )
        );

        KnowledgeBase deleted = queryFixture(
                "60000000-0000-4000-8000-000000000001",
                KnowledgeBase.Lifecycle.DELETED,
                newerTime.plusSeconds(60)
        );

        when(repository.findAll()).thenReturn(List.of(
                expectedOrder.get(2),
                deleted,
                expectedOrder.get(1),
                expectedOrder.get(0)
        ));

        KnowledgeBasePage result =
                queryService.findPageForAdminDemo(page, pageSize);

        assertEquals(
                expectedOrder.subList(expectedFrom, expectedTo),
                result.items()
        );
        assertEquals(page, result.page());
        assertEquals(pageSize, result.pageSize());
        assertEquals(3L, result.total());
        assertEquals(expectedHasNext, result.hasNext());
    }

    @Test
    void shouldReturnEmptyPageWhenNoKnowledgeBasesExist() {
        KnowledgeBasePage result = service.findPageForAdminDemo(1, 20);

        assertTrue(result.items().isEmpty());
        assertEquals(1, result.page());
        assertEquals(20, result.pageSize());
        assertEquals(0L, result.total());
        assertFalse(result.hasNext());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 20",
            "1, 0",
            "1, 101"
    })
    void shouldRejectInvalidPaginationInService(int page, int pageSize) {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findPageForAdminDemo(page, pageSize)
        );
    }
}
