
package com.enterprise.knowledge.common.api;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId
) {

    public static <T> ApiResponse<T> success(
            T data,
            String requestId
    ) {
        return new ApiResponse<>(
                "SUCCESS",
                "Request successful",
                data,
                requestId
        );
    }

    public static ApiResponse<Void> error(
            String code,
            String message,
            String requestId
    ) {
        return new ApiResponse<>(
                code,
                message,
                null,
                requestId
        );
    }
}
