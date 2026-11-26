package com.enterprise.knowledge.knowledgebase.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JpaKnowledgeBaseRepository
        extends JpaRepository<KnowledgeBaseEntity, UUID> {

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM core.knowledge_bases
                WHERE lifecycle <> 'DELETED'
                  AND name_key = lower(CAST(:name AS text) COLLATE "C")
            )
            """, nativeQuery = true)
    boolean existsLiveByName(@Param("name") String name);
}