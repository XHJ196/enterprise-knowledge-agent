package com.enterprise.knowledge.knowledgebase.infrastructure.persistence;

import com.enterprise.knowledge.knowledgebase.application.KnowledgeBaseNameConflictException;
import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBaseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PostgresKnowledgeBaseRepositoryTest {

    @Autowired
    private KnowledgeBaseRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID creatorId;

    @BeforeEach
    void prepareCreator() {
        creatorId = UUID.randomUUID();
        String username = "day13_" + creatorId.toString()
                .replace("-", "")
                .substring(0, 16);

        jdbcTemplate.update(
                """
                INSERT INTO core.users (id, username, password_hash, role)
                VALUES (?, ?, ?, 'ADMIN')
                """,
                creatorId,
                username,
                "!day13-adapter-test-only"
        );
    }

    @Test
    void shouldSaveAndQueryThroughDomainRepository() {
        KnowledgeBase original = fixture(
                uniqueName("Day13 Adapter"),
                creatorId
        );

        KnowledgeBase saved = repository.save(original);
        assertEquals(original, saved);

        entityManager.clear();

        assertTrue(repository.findAll().contains(original));

        assertTrue(repository.existsByNameIgnoreCase(
                "  " + original.name().toLowerCase(Locale.ROOT) + "  "
        ));
    }

    @Test
    void shouldTranslateDatabaseNameConflict() {
        String name = uniqueName("Day13 Conflict");

        repository.save(fixture(name, creatorId));

        KnowledgeBase duplicate = fixture(
                name.toLowerCase(Locale.ROOT),
                creatorId
        );

        assertThrows(
                KnowledgeBaseNameConflictException.class,
                () -> repository.save(duplicate)
        );
    }

    @Test
    void shouldKeepForeignKeyFailureAsDataIntegrityError() {
        KnowledgeBase knowledgeBase = fixture(
                uniqueName("Day13 Missing"),
                UUID.randomUUID()
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.save(knowledgeBase)
        );
    }

    private KnowledgeBase fixture(String name, UUID createdBy) {
        return new KnowledgeBase(
                UUID.randomUUID(),
                name,
                "  保留简介原文  ",
                KnowledgeBase.Lifecycle.ACTIVE,
                createdBy,
                Instant.parse("2026-11-24T10:00:00.123456Z"),
                Instant.parse("2026-11-26T11:00:00.654321Z")
        );
    }

    private String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID()
                .toString()
                .substring(0, 8);
    }
}