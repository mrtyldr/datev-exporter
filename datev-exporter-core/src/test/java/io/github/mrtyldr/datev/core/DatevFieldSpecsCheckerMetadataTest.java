package io.github.mrtyldr.datev.core;


import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevFieldSpecsCheckerMetadataTest {

    @Test
    void exposesCompleteOrderedV13AndV12Definitions() {
        List<DatevFieldSpec> current = DatevFieldSpecs.version13();
        List<DatevFieldSpec> legacy = DatevFieldSpecs.version12();

        assertEquals(125, current.size());
        assertEquals(124, legacy.size());
        assertEquals(legacy, current.subList(0, 124));
        assertEquals(DatevFieldSpecs.headers13(),
                current.stream().map(DatevFieldSpec::canonicalKey).toList());
        for (int index = 0; index < current.size(); index++) {
            assertEquals(index + 1, current.get(index).fieldNumber());
        }
        assertEquals(125,
                current.stream().map(DatevFieldSpec::canonicalKey).collect(Collectors.toSet()).size());
    }

    @Test
    void marksExactlyTheFiveOfficialNecessaryFields() {
        Set<String> required = DatevFieldSpecs.version13().stream()
                .filter(DatevFieldSpec::required)
                .map(DatevFieldSpec::canonicalKey)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "Umsatz (ohne Soll/Haben-Kz)",
                "Soll/Haben-Kennzeichen",
                "Konto",
                "Gegenkonto (ohne BU-Schlüssel)",
                "Belegdatum"
        ), required);
    }

    @Test
    void retainsCheckerSpecificMetadataAtKnownBoundaries() {
        assertEquals(
                new DatevFieldSpec(1, "Umsatz (ohne Soll/Haben-Kz)",
                        DatevFieldType.AMOUNT, 10, 2, true),
                DatevFieldSpecs.find("Umsatz (ohne Soll/Haben-Kz)").orElseThrow()
        );
        assertEquals(12, DatevFieldSpecs.find("Belegfeld 2").orElseThrow().maxLength());
        assertEquals(2,
                DatevFieldSpecs.find("USt-Schlüssel (Anzahlungen)").orElseThrow().maxLength());
        assertEquals(
                new DatevFieldSpec(125, "Abw. Skontokonto", DatevFieldType.ACCOUNT, 8, 0, false),
                DatevFieldSpecs.find("Abw. Skontokonto").orElseThrow()
        );
        assertTrue(DatevFieldSpecs.find("unknown").isEmpty());
        assertTrue(DatevFieldSpecs.find(null).isEmpty());
    }

    @Test
    void returnedDefinitionsAreImmutable() {
        List<DatevFieldSpec> specs = DatevFieldSpecs.version13();

        assertThrows(UnsupportedOperationException.class, () -> specs.remove(0));
        assertFalse(specs.isEmpty());
    }

    @Test
    void fieldTypesExposeCheckerNames() {
        assertEquals("Text", DatevFieldType.TEXT.checkerName());
        assertEquals("Zahl", DatevFieldType.NUMBER.checkerName());
        assertEquals("Betrag", DatevFieldType.AMOUNT.checkerName());
        assertEquals("Konto", DatevFieldType.ACCOUNT.checkerName());
        assertEquals("Datum", DatevFieldType.DATE.checkerName());
    }

    @Test
    void rejectsInvalidFieldDefinitions() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatevFieldSpec(0, "A", DatevFieldType.TEXT, 1, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new DatevFieldSpec(1, " ", DatevFieldType.TEXT, 1, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new DatevFieldSpec(1, "A", DatevFieldType.TEXT, 0, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new DatevFieldSpec(1, "A", DatevFieldType.TEXT, 1, 1, false));
    }
}
