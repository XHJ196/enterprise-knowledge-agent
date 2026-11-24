package com.enterprise.knowledge.knowledgebase.application;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBase;

import java.util.List;

public record KnowledgeBasePage(
        List<KnowledgeBase> items,
        int page,
        int pageSize,
        long total,
        boolean hasNext
) {
    public KnowledgeBasePage {
        items = List.copyOf(items);
    }
}