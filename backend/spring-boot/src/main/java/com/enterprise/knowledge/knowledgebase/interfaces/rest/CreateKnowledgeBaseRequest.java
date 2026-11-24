package com.enterprise.knowledge.knowledgebase.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.CodePointLength;

public record CreateKnowledgeBaseRequest(
        @NotBlank(message = "知识库名称不能为空")
        @CodePointLength(
                max = 50,
                message = "知识库名称不能超过50个字符"
        )
        String name,

        @CodePointLength(
                max = 500,
                message = "简介不能超过500个字符"
        )
        String description
) {
        public CreateKnowledgeBaseRequest {
                name = name == null ? null : name.trim();
        }
}