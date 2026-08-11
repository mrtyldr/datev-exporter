package io.github.mrtyldr.datev.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevFieldTest {

    @Test
    void declaresEveryOfficialHeadingInOfficialOrder() {
        List<String> headings = Arrays.stream(DatevField.values()).map(DatevField::heading).toList();

        assertEquals(125, headings.size());
        assertEquals(DatevFieldSpecs.headers13(), headings);
        assertEquals(125, headings.stream().distinct().count());
        assertEquals(125, Arrays.stream(DatevField.values()).map(Enum::name).distinct().count());
    }

    @Test
    void exposesFieldNumberAndSpecMatchingTheOfficialDefinition() {
        for (DatevField field : DatevField.values()) {
            DatevFieldSpec spec = field.spec();
            assertEquals(field.ordinal() + 1, field.fieldNumber());
            assertEquals(field.fieldNumber(), spec.fieldNumber());
            assertEquals(field.heading(), spec.canonicalKey());
        }
    }

    @Test
    void reportsSchemaMembershipForTheVersion13OnlyField() {
        assertTrue(DatevField.ACCOUNT.isPresentIn(DatevSchema.LEGACY_V12));
        assertTrue(DatevField.EU_TAX_RATE_ORIGIN.isPresentIn(DatevSchema.LEGACY_V12));
        assertTrue(DatevField.DIFFERING_CASH_DISCOUNT_ACCOUNT.isPresentIn(DatevSchema.CURRENT_V13));
        assertFalse(DatevField.DIFFERING_CASH_DISCOUNT_ACCOUNT.isPresentIn(DatevSchema.LEGACY_V12));
        assertThrows(NullPointerException.class, () -> DatevField.ACCOUNT.isPresentIn(null));
    }

    @Test
    void resolvesFieldsFromTheirOfficialHeading() {
        for (DatevField field : DatevField.values()) {
            assertEquals(field, DatevField.fromHeading(field.heading()).orElseThrow());
        }
        assertEquals(DatevField.ADDITIONAL_INFO_CONTENT_1,
                DatevField.fromHeading("Zusatzinformation- Inhalt 1").orElseThrow());
        assertTrue(DatevField.fromHeading("Konto ").isEmpty());
        assertTrue(DatevField.fromHeading("Unbekannt").isEmpty());
        assertTrue(DatevField.fromHeading(null).isEmpty());
    }

    @Test
    void producesColumnsIdenticalToTheStringOverloads() {
        LocalDate date = LocalDate.of(2026, 3, 17);

        assertEquals(DatevColumn.of("Buchungstext", "Miete"),
                DatevColumn.of(DatevField.POSTING_TEXT, "Miete"));
        assertEquals(DatevColumn.account("Konto", 1200L).formattedValue(),
                DatevColumn.account(DatevField.ACCOUNT, 1200L).formattedValue());
        assertEquals(DatevColumn.amount("Umsatz (ohne Soll/Haben-Kz)", new BigDecimal("12.50"))
                        .formattedValue(),
                DatevColumn.amount(DatevField.AMOUNT, new BigDecimal("12.50")).formattedValue());
        assertEquals(DatevColumn.date("Leistungsdatum", date).formattedValue(),
                DatevColumn.date(DatevField.SERVICE_DATE, date).formattedValue());
        assertEquals("Fälligkeit", DatevColumn.date(DatevField.DUE_DATE, date).header());
        assertEquals("Zusatzinformation - Art 1",
                DatevColumn.of(DatevField.ADDITIONAL_INFO_TYPE_1, "Auftrag").header());
        assertEquals("17032026", DatevColumn.date(DatevField.SERVICE_DATE, date).formattedValue());
    }

    @Test
    void reportsSlotPositionsForRepeatingGroups() {
        assertTrue(DatevField.ACCOUNT.slot().isEmpty());
        assertFalse(DatevField.ACCOUNT.isRepeating());
        assertTrue(DatevField.ADDITIONAL_INFO_CONTENT_3.isRepeating());

        assertEquals(new DatevField.Slot(DatevField.Group.DOCUMENT_INFO, 1, DatevField.Part.TYPE),
                DatevField.DOCUMENT_INFO_TYPE_1.slot().orElseThrow());
        assertEquals(new DatevField.Slot(DatevField.Group.DOCUMENT_INFO, 8,
                        DatevField.Part.CONTENT),
                DatevField.DOCUMENT_INFO_CONTENT_8.slot().orElseThrow());
        assertEquals(new DatevField.Slot(DatevField.Group.ADDITIONAL_INFO, 20,
                        DatevField.Part.CONTENT),
                DatevField.ADDITIONAL_INFO_CONTENT_20.slot().orElseThrow());

        assertEquals(56, Arrays.stream(DatevField.values()).filter(DatevField::isRepeating).count());
        for (DatevField field : DatevField.values()) {
            field.slot().ifPresent(slot -> assertEquals(field, slot.field()));
        }
    }

    @Test
    void resolvesGroupMembersBySlotNumber() {
        DatevField.Group additional = DatevField.Group.ADDITIONAL_INFO;

        assertEquals(20, additional.slotCount());
        assertEquals(8, DatevField.Group.DOCUMENT_INFO.slotCount());
        assertEquals(DatevField.ADDITIONAL_INFO_CONTENT_3,
                additional.field(3, DatevField.Part.CONTENT));
        assertEquals(DatevField.ADDITIONAL_INFO_TYPE_1, additional.field(1, DatevField.Part.TYPE));
        assertEquals(40, additional.fields().size());
        assertEquals(DatevField.Group.DOCUMENT_INFO.fields(),
                Arrays.stream(DatevField.values())
                        .filter(field -> field.slot()
                                .filter(slot -> slot.group() == DatevField.Group.DOCUMENT_INFO)
                                .isPresent())
                        .toList());

        assertThrows(IllegalArgumentException.class,
                () -> additional.field(0, DatevField.Part.TYPE));
        assertThrows(IllegalArgumentException.class,
                () -> additional.field(21, DatevField.Part.TYPE));
        assertThrows(NullPointerException.class, () -> additional.field(1, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DatevField.Slot(DatevField.Group.DOCUMENT_INFO, 9,
                        DatevField.Part.TYPE));
    }

    @Test
    void rejectsANullField() {
        assertThrows(NullPointerException.class, () -> DatevColumn.of((DatevField) null, "x"));
        assertThrows(NullPointerException.class,
                () -> DatevColumn.account((DatevField) null, 1200L));
        assertThrows(NullPointerException.class,
                () -> DatevColumn.amount((DatevField) null, new BigDecimal("1.00")));
        assertThrows(NullPointerException.class,
                () -> DatevColumn.date((DatevField) null, LocalDate.of(2026, 1, 1)));
    }
}
