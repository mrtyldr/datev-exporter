package io.github.mrtyldr.datev.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevInfoBlockTest {

    @Test
    void fillsSlotsInInsertionOrder() {
        List<DatevColumn<String>> columns = DatevInfoBlock.additionalInfo()
                .put("Auftragsnr", "A-4711")
                .put("Kostenträger", "KT-9")
                .toColumns();

        assertEquals(4, columns.size());
        assertEquals("Zusatzinformation - Art 1", columns.get(0).header());
        assertEquals("Auftragsnr", columns.get(0).value());
        assertEquals("Zusatzinformation- Inhalt 1", columns.get(1).header());
        assertEquals("A-4711", columns.get(1).value());
        assertEquals("Zusatzinformation - Art 2", columns.get(2).header());
        assertEquals("Zusatzinformation- Inhalt 2", columns.get(3).header());
        assertEquals("KT-9", columns.get(3).value());
    }

    @Test
    void fillsDocumentInfoSlots() {
        List<DatevColumn<String>> columns = DatevInfoBlock.documentInfo()
                .put("Rechnung", "R-1")
                .toColumns();

        assertEquals(List.of("Beleginfo - Art 1", "Beleginfo - Inhalt 1"),
                columns.stream().map(DatevColumn::header).toList());
        assertEquals(8, DatevInfoBlock.documentInfo().capacity());
        assertEquals(20, DatevInfoBlock.additionalInfo().capacity());
    }

    @Test
    void tracksOccupancy() {
        DatevInfoBlock block = DatevInfoBlock.additionalInfo();

        assertTrue(block.isEmpty());
        assertEquals(20, block.remaining());

        block.put("Erste", "1").put("Zweite", "2");

        assertFalse(block.isEmpty());
        assertEquals(2, block.size());
        assertEquals(18, block.remaining());
        assertTrue(block.contains("Erste"));
        assertFalse(block.contains("Dritte"));
        assertFalse(block.contains(null));
        assertEquals(DatevField.Group.ADDITIONAL_INFO, block.group());
    }

    @Test
    void exposesEntriesAsAnIndependentOrderedSnapshot() {
        DatevInfoBlock block = DatevInfoBlock.documentInfo().put("A", "1").put("B", "2");

        Map<String, String> snapshot = block.entries();

        assertEquals(List.of("A", "B"), List.copyOf(snapshot.keySet()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("C", "3"));

        block.put("C", "3");

        assertEquals(2, snapshot.size());
        assertEquals(3, block.size());
    }

    @Test
    void producesAnIndependentColumnSnapshot() {
        DatevInfoBlock block = DatevInfoBlock.documentInfo().put("A", "1");
        List<DatevColumn<String>> first = block.toColumns();

        block.put("B", "2");

        assertEquals(2, first.size());
        assertEquals(4, block.toColumns().size());
        assertThrows(UnsupportedOperationException.class, () -> first.add(null));
    }

    @Test
    void addsEveryEntryOfAMapInOrder() {
        var values = new LinkedHashMap<String, String>();
        values.put("Erste", "1");
        values.put("Zweite", "2");

        DatevInfoBlock block = DatevInfoBlock.additionalInfo().putAll(values);

        assertEquals(List.of("Erste", "Zweite"), List.copyOf(block.entries().keySet()));
        assertThrows(NullPointerException.class, () -> block.putAll(null));
    }

    @Test
    void rejectsInvalidEntries() {
        DatevInfoBlock block = DatevInfoBlock.additionalInfo().put("Auftrag", "A-1");

        assertThrows(NullPointerException.class, () -> block.put(null, "x"));
        assertThrows(NullPointerException.class, () -> block.put("x", null));
        assertThrows(IllegalArgumentException.class, () -> block.put("  ", "x"));
        assertThrows(IllegalArgumentException.class, () -> block.put("Auftrag", "A-2"));
        assertThrows(NullPointerException.class, () -> DatevInfoBlock.of(null));
    }

    @Test
    void enforcesTheOfficialFieldLengths() {
        DatevInfoBlock block = DatevInfoBlock.additionalInfo();

        assertThrows(IllegalArgumentException.class, () -> block.put("x".repeat(21), "content"));
        assertThrows(IllegalArgumentException.class, () -> block.put("label", "y".repeat(211)));

        block.put("x".repeat(20), "y".repeat(210));

        assertEquals(1, block.size());
    }

    @Test
    void allowsEmptyContent() {
        List<DatevColumn<String>> columns = DatevInfoBlock.documentInfo()
                .put("Marker", "")
                .toColumns();

        assertEquals("", columns.get(1).value());
    }

    @Test
    void rejectsOverflowingTheGroup() {
        DatevInfoBlock block = DatevInfoBlock.documentInfo();
        for (int slot = 1; slot <= 8; slot++) {
            block.put("Art" + slot, "Inhalt" + slot);
        }

        assertEquals(0, block.remaining());
        assertEquals(16, block.toColumns().size());
        assertThrows(IllegalStateException.class, () -> block.put("Art9", "Inhalt9"));
    }

    @Test
    void describesItself() {
        assertEquals("DatevInfoBlock[DOCUMENT_INFO 1/8]",
                DatevInfoBlock.documentInfo().put("A", "1").toString());
    }
}
