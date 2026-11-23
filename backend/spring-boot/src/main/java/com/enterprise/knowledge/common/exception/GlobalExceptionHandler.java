package com.enterprise.knowledge.common.exception;

import com.enterprise.knowledge.knowledgebase.domain.KnowledgeBaseValidationException;
import com.enterprise.knowledge.common.api.ApiResponse;
import com.enterprise.knowledge.knowledgebase.application.KnowledgeBaseNameConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(KnowledgeBaseNameConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleNameConflict(
            KnowledgeBaseNameConflictException exception
    ) {
        ApiResponse<Void> body = ApiResponse.error(
                "KNOWLEDGE_BASE_NAME_CONFLICT",
                "Knowledge base name already exists",
                getRequestId()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(body);
    }
    @ExceptionHandler(KnowledgeBaseValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleKnowledgeBaseValidation(
            KnowledgeBaseValidationException exception
    ) {
        ApiResponse<Void> body = ApiResponse.error(
                "VALIDATION_ERROR",
                exception.getMessage(),
                getRequestId()
        );

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(body);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception
    ) {
        ApiResponse<Void> body = ApiResponse.error(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                getRequestId()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }
    private String getRequestId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return UUID.randomUUID().toString();
        }

        HttpServletRequest request = attributes.getRequest();

        return (String) request.getAttribute("X-Request-Id");
    }
}