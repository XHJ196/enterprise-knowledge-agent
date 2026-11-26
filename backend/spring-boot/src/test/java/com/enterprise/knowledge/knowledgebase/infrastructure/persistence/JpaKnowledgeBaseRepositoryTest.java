package com.enterprise.knowledge.knowledgebase.infrastructure.persistence;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.Timestamp;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class JpaKnowledgeBaseRepositoryTest {

    @Autowired
    private JpaKnowledgeBaseRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"  PostgreSQL 真实读回测试  "})
    void shouldSaveAndReloadKnowledgeBase(String description) {
        UUID creatorId = UUID.randomUUID();
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
                "!day13-jpa-test-only"
        );

        UUID knowledgeBaseId = UUID.randomUUID();

        KnowledgeBase original = new KnowledgeBase(
                knowledgeBaseId,
                "Day13 JPA " + knowledgeBaseId,
                description,
                KnowledgeBase.Lifecycle.ACTIVE,
                creatorId,
                Instant.parse("2026-11-24T10:00:00.123456Z"),
                Instant.parse("2026-11-26T11:00:00.654321Z")
        );

        repository.saveAndFlush(
                KnowledgeBaseEntity.fromNew(original)
        );

        entityManager.clear();

        KnowledgeBase restored = repository
                .findById(knowledgeBaseId)
                .orElseThrow()
                .toDomain();

        assertEquals(original, restored);

        String nameKey = jdbcTemplate.queryForObject(
                """
                SELECT name_key
                FROM core.knowledge_bases
                WHERE id = ?
                """,
                String.class,
                knowledgeBaseId
        );

        assertEquals("day13 jpa " + knowledgeBaseId, nameKey);
    }
    @ParameterizedTest
    @EnumSource(KnowledgeBase.Lifecycle.class)
    void shouldCheckNameWithinLiveKnowledgeBases(
            KnowledgeBase.Lifecycle lifecycle
    ) {
        UUID creatorId = UUID.randomUUID();
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
                "!day13-jpa-test-only"
        );

        UUID knowledgeBaseId = UUID.randomUUID();
        String name = "Day13 Lookup " + knowledgeBaseId;
        boolean deleted = lifecycle == KnowledgeBase.Lifecycle.DELETED;

        jdbcTemplate.update(
                """
                INSERT INTO core.knowledge_bases
                    (id, name, created_by, lifecycle, deleted_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                knowledgeBaseId,
                deleted ? null : name,
                creatorId,
                lifecycle.name(),
                deleted ? Timestamp.from(Instant.now()) : null
        );

        assertEquals(
                !deleted,
                repository.existsLiveByName(name.toLowerCase(Locale.ROOT))
        );

        assertEquals(
                !deleted,
                repository.existsLiveByName(name.toUpperCase(Locale.ROOT))
        );

        assertFalse(
                repository.existsLiveByName(
                        "Missing " + UUID.randomUUID()
                )
        );
    }
}
