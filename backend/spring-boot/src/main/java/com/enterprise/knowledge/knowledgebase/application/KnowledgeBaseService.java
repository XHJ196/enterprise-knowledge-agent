
package com.enterprise.knowledge.knowledgebase.application;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.Comparator;

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
    public List<KnowledgeBase> findAllForAdminDemo() {
        return repository.findAll().stream()
                .filter(kb ->
                        kb.lifecycle() != KnowledgeBase.Lifecycle.DELETED
                )
                .sorted(
                        Comparator.comparing(KnowledgeBase::createdAt)
                                .reversed()
                                .thenComparing(kb -> kb.id().toString())
                )
                .toList();
    }
    public KnowledgeBasePage findPageForAdminDemo(
            int page,
            int pageSize
    ) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid pagination arguments");
        }

        List<KnowledgeBase> visible = findAllForAdminDemo();
        long total = visible.size();
        long offset = (page - 1L) * pageSize;

        List<KnowledgeBase> items = visible.stream()
                .skip(offset)
                .limit(pageSize)
                .toList();

        boolean hasNext = offset + items.size() < total;

        return new KnowledgeBasePage(
                items,
                page,
                pageSize,
                total,
                hasNext
        );
    }
}
