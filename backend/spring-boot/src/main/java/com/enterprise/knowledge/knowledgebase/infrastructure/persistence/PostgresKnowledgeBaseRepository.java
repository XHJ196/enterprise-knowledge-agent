package com.enterprise.knowledge.knowledgebase.infrastructure.persistence;

import com.enterprise.knowledge.knowledgebase.application.KnowledgeBaseNameConflictException;
import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBaseRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional(readOnly = true)
public class PostgresKnowledgeBaseRepository
        implements KnowledgeBaseRepository {

    private final JpaKnowledgeBaseRepository jpaRepository;

    public PostgresKnowledgeBaseRepository(
            JpaKnowledgeBaseRepository jpaRepository
    ) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public KnowledgeBase save(KnowledgeBase knowledgeBase) {
        KnowledgeBaseEntity entity =
                KnowledgeBaseEntity.fromNew(knowledgeBase);

        try {
            return jpaRepository.saveAndFlush(entity).toDomain();
        } catch (DataIntegrityViolationException exception) {
            if (isNameConflict(exception)) {
                throw new KnowledgeBaseNameConflictException(
                        "知识库名称已存在"
                );
            }

            throw exception;
        }
    }

    @Override
    public List<KnowledgeBase> findAll() {
        return jpaRepository.findAll().stream()
                .map(KnowledgeBaseEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return jpaRepository.existsLiveByName(name.trim());
    }

    private boolean isNameConflict(Throwable exception) {
        for (Throwable cause = exception;
             cause != null;
             cause = cause.getCause()) {

            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "uq_live_kb_name".equals(
                    violation.getConstraintName()
            )) {
                return true;
            }
        }

        return false;
    }
}