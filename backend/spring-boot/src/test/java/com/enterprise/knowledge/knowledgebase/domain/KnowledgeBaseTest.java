
package com.enterprise.knowledge.knowledgebase.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBaseTest {

    @Test
    void shouldCreateKnowledgeBaseWithValidInput() {
        UUID creatorId = UUID.randomUUID();

        KnowledgeBase kb = KnowledgeBase.create(
                "  Engineering Docs  ",
                "Technical documentation",
                creatorId
        );

        assertNotNull(kb.id());
        assertEquals("Engineering Docs", kb.name());
        assertEquals("Technical documentation", kb.description());
        assertEquals(KnowledgeBase.Lifecycle.ACTIVE, kb.lifecycle());
        assertEquals(creatorId, kb.createdBy());
        assertNotNull(kb.createdAt());
        assertNotNull(kb.updatedAt());
    }

    @Test
    void shouldRejectEmptyName() {
        UUID creatorId = UUID.randomUUID();

        assertThrows(
                KnowledgeBaseValidationException.class,
                () -> KnowledgeBase.create("   ", null, creatorId)
        );
    }

    @Test
    void shouldRejectNameLongerThan50Characters() {
        UUID creatorId = UUID.randomUUID();
        String longName = "A".repeat(51);

        assertThrows(
                KnowledgeBaseValidationException.class,
                () -> KnowledgeBase.create(longName, null, creatorId)
        );
    }

    @Test
    void shouldRejectDescriptionLongerThan500Characters() {
        UUID creatorId = UUID.randomUUID();
        String longDescription = "A".repeat(501);

        assertThrows(
                KnowledgeBaseValidationException.class,
                () -> KnowledgeBase.create("Engineering Docs", longDescription, creatorId)
        );
    }

    @Test
    void shouldRejectNullCreatorId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> KnowledgeBase.create("Engineering Docs", null, null)
        );
    }
    @Test
    void shouldAccept50CodePointNameAfterTrim() {
        String name = "😀".repeat(50);

        KnowledgeBase kb = KnowledgeBase.create(
                "  " + name + "  ",
                null,
                UUID.randomUUID()
        );

        assertEquals(name, kb.name());
    }

    @Test
    void shouldReject51CodePointName() {
        String name = "😀".repeat(51);

        assertThrows(
                KnowledgeBaseValidationException.class,
                () -> KnowledgeBase.create(name, null, UUID.randomUUID())
        );
    }

    @Test
    void shouldAccept500CodePointDescription() {
        String description = "😀".repeat(500);

        KnowledgeBase kb = KnowledgeBase.create(
                "Engineering Docs",
                description,
                UUID.randomUUID()
        );

        assertEquals(description, kb.description());
    }

    @Test
    void shouldReject501CodePointDescription() {
        String description = "😀".repeat(501);

        assertThrows(
                KnowledgeBaseValidationException.class,
                () -> KnowledgeBase.create(
                        "Engineering Docs",
                        description,
                        UUID.randomUUID()
                )
        );
    }
}
