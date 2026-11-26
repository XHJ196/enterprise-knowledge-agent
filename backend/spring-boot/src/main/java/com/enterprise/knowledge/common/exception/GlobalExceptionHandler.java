package com.enterprise.knowledge.common.exception;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.databind.exc.MismatchedInputException;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
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
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequestValidation(
            MethodArgumentNotValidException exception
    ) {
        ApiResponse<Void> body = ApiResponse.error(
                "VALIDATION_ERROR",
                "请求参数校验失败",
                getRequestId()
        );

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(body);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequestBody(
            HttpMessageNotReadableException exception
    ) {
        boolean fieldMismatch =
                exception.getCause() instanceof MismatchedInputException;

        ApiResponse<Void> body = ApiResponse.error(
                fieldMismatch ? "VALIDATION_ERROR" : "INVALID_JSON",
                fieldMismatch
                        ? "请求参数校验失败"
                        : "请求体缺失或 JSON 格式不正确",
                getRequestId()
        );

        return ResponseEntity
                .status(fieldMismatch
                        ? HttpStatus.UNPROCESSABLE_CONTENT
                        : HttpStatus.BAD_REQUEST)
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
    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequestBoundaryValidation(
            RequestValidationException exception
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
    @ExceptionHandler(UnexpectedRequestBodyException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedRequestBody(
            UnexpectedRequestBodyException exception
    ) {
        ApiResponse<Void> body = ApiResponse.error(
                "INVALID_REQUEST_BODY",
                exception.getMessage(),
                getRequestId()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(body);
    }
}