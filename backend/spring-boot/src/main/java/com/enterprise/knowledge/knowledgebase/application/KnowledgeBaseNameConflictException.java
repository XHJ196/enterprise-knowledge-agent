package com.enterprise.knowledge.knowledgebase.application;

public class KnowledgeBaseNameConflictException extends RuntimeException {

    public KnowledgeBaseNameConflictException(String message) {
        super(message);
    }
}