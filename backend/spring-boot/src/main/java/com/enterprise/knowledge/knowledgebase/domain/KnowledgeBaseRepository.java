
package com.enterprise.knowledge.knowledgebase.domain;

import java.util.List;

public interface KnowledgeBaseRepository {

    KnowledgeBase save(KnowledgeBase knowledgeBase);

    List<KnowledgeBase> findAll();

    boolean existsByNameIgnoreCase(String name);
}
