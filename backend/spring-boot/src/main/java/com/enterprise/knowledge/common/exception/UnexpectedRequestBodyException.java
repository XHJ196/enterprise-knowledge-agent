package com.enterprise.knowledge.common.exception;

public class UnexpectedRequestBodyException extends RuntimeException {

    public UnexpectedRequestBodyException(String message) {
        super(message);
    }
}