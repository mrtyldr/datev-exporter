package io.github.mrtyldr.datev.core;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevSchemaTest {

    @Test
    void exposesOnlyTheTwoFixedOfficialSchemas() {
        assertEquals(2, DatevSchema.values().length);
        assertSame(DatevSchema.CURRENT_V13, DatevSchema.current());
        assertEquals(13, DatevSchema.CURRENT_V13.formatVersion());
        assertEquals(12, DatevSchema.LEGACY_V12.formatVersion());
        assertEquals(125, DatevSchema.CURRENT_V13.columnCount());
        assertEquals(124, DatevSchema.LEGACY_V12.columnCount());
    }

    @Test
    void v12IsTheExactPrefixOfV13() {
        assertEquals(
                DatevSchema.LEGACY_V12.headers(),
                DatevSchema.CURRENT_V13.headers().subList(0, 124)
        );
        assertEquals(
                "Umsatz (ohne Soll/Haben-Kz)",
                DatevSchema.CURRENT_V13.headers().get(0)
        );
        assertEquals(
                "EU-Steuersatz (Ursprung)",
                DatevSchema.CURRENT_V13.headers().get(123)
        );
        assertEquals("Abw. Skontokonto", DatevSchema.CURRENT_V13.headers().get(124));
    }

    @Test
    void headingListsAreImmutable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> DatevSchema.CURRENT_V13.headers().set(0, "changed")
        );
    }

    @Test
    void identifiesDatevTextColumnsAndChecksBounds() {
        assertFalse(DatevSchema.CURRENT_V13.isTextColumn(0));
        assertTrue(DatevSchema.CURRENT_V13.isTextColumn(1));
        assertTrue(DatevSchema.CURRENT_V13.isTextColumn(13));
        assertFalse(DatevSchema.CURRENT_V13.isTextColumn(124));

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> DatevSchema.CURRENT_V13.isTextColumn(-1)
        );
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> DatevSchema.LEGACY_V12.isTextColumn(124)
        );
    }
}
