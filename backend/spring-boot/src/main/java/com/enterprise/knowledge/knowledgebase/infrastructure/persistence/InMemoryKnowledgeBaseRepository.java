
package com.enterprise.knowledge.knowledgebase.infrastructure.persistence;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;
import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBaseRepository;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryKnowledgeBaseRepository
        implements KnowledgeBaseRepository {
    private final ConcurrentHashMap<String, KnowledgeBase> storage =
            new ConcurrentHashMap<>();

    @Override
    public KnowledgeBase save(KnowledgeBase knowledgeBase) {
        Objects.requireNonNull(knowledgeBase, "Knowledge base cannot be null");

        storage.put(knowledgeBase.id().toString(), knowledgeBase);

        return knowledgeBase;
    }

    @Override
    public List<KnowledgeBase> findAll() {
        return List.copyOf(storage.values());
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return storage.values().stream()
                .anyMatch(kb -> kb.name().equalsIgnoreCase(name.trim()));
    }
}
