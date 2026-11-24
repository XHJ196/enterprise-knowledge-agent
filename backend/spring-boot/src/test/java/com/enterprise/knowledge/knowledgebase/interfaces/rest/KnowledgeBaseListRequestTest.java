package com.enterprise.knowledge.knowledgebase.interfaces.rest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseListRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void shouldUseDefaultsWhenParametersAreMissing() {
        KnowledgeBaseListRequest request =
                new KnowledgeBaseListRequest(null, null);

        assertEquals(1, request.page());
        assertEquals(20, request.pageSize());
        assertTrue(validator.validate(request).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1",
            "2, 20",
            "1, 100"
    })
    void shouldAcceptValidPagination(int page, int pageSize) {
        KnowledgeBaseListRequest request =
                new KnowledgeBaseListRequest(page, pageSize);

        assertTrue(validator.validate(request).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 20, page",
            "-1, 20, page",
            "1, 0, pageSize",
            "1, -1, pageSize",
            "1, 101, pageSize"
    })
    void shouldRejectInvalidPagination(
            int page,
            int pageSize,
            String invalidField
    ) {
        KnowledgeBaseListRequest request =
                new KnowledgeBaseListRequest(page, pageSize);

        var violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals(
                invalidField,
                violations.iterator().next().getPropertyPath().toString()
        );
    }
}