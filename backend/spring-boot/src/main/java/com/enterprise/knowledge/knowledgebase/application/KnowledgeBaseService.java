
package com.enterprise.knowledge.knowledgebase.application;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseService(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    public KnowledgeBase create(
            String name,
            String description,
            UUID createdBy
    ) {
        String normalizedName = name == null ? "" : name.trim();

        if (repository.existsByNameIgnoreCase(normalizedName)) {
            throw new KnowledgeBaseNameConflictException(
                    "数据库名已存在"
            );
        }

        KnowledgeBase knowledgeBase = KnowledgeBase.create(
                normalizedName,
                description,
                createdBy
        );

        return repository.save(knowledgeBase);
    }

    public List<KnowledgeBase> findAll() {
        return repository.findAll();
    }
}
