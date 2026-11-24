package com.enterprise.knowledge.knowledgebase.interfaces.rest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateKnowledgeBaseRequestTest {

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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void shouldRejectMissingOrBlankName(String name) {
        CreateKnowledgeBaseRequest request =
                new CreateKnowledgeBaseRequest(name, null);

        var violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals(
                "name",
                violations.iterator().next().getPropertyPath().toString()
        );
    }

    @Test
    void shouldAcceptValidNameAndNullDescription() {
        CreateKnowledgeBaseRequest request =
                new CreateKnowledgeBaseRequest("Engineering Docs", null);

        assertTrue(validator.validate(request).isEmpty());
    }
    @ParameterizedTest
    @ValueSource(ints = {50, 51})
    void shouldValidateNameCodePointLimitAfterTrim(int codePoints) {
        String name = "😀".repeat(codePoints);

        CreateKnowledgeBaseRequest request =
                new CreateKnowledgeBaseRequest(
                        "  " + name + "  ",
                        null
                );

        assertEquals(name, request.name());
        assertEquals(
                codePoints <= 50,
                validator.validate(request).isEmpty()
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 501})
    void shouldValidateDescriptionCodePointLimit(int codePoints) {
        String description = "😀".repeat(codePoints);

        CreateKnowledgeBaseRequest request =
                new CreateKnowledgeBaseRequest(
                        "Engineering Docs",
                        description
                );

        assertEquals(
                codePoints <= 500,
                validator.validate(request).isEmpty()
        );
    }
}