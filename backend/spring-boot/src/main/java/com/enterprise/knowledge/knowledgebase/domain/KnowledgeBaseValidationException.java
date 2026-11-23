package com.enterprise.knowledge.knowledgebase.domain;

public class KnowledgeBaseValidationException
        extends IllegalArgumentException {

    public KnowledgeBaseValidationException(String message) {
        super(message);
    }
}