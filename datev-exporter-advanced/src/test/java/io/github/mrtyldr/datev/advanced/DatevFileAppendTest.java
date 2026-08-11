package io.github.mrtyldr.datev.advanced;

import io.github.mrtyldr.datev.core.DatevColumn;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevFileAppendTest {

    @Test
    void appendsArrayAndCollectionRowsInOrderUsingDefensiveCopies() {
        DatevFile file = DatevFile.withHeader("A;B;C");
        String[] array = {"1", null, "3"};
        List<String> collection = new ArrayList<>(List.of("4", "5", "6"));

        file.append(array);
        file.append(collection);
        array[0] = "changed";
        collection.set(0, "changed");

        assertEquals(Arrays.asList("1", null, "3"), file.rows().get(0));
        assertEquals(List.of("4", "5", "6"), file.rows().get(1));
    }

    @Test
    void rejectsPositionalRowsWithTheWrongWidthWithoutChangingState() {
        DatevFile file = DatevFile.withHeader("A;B");

        IllegalArgumentException shortRow = assertThrows(
                IllegalArgumentException.class,
                () -> file.append(List.of("1"))
        );
        assertThrows(IllegalArgumentException.class, () -> file.append(new String[]{"1", "2", "3"}));

        assertTrue(shortRow.getMessage().contains("header contains 2"));
        assertEquals(0, file.rowCount());
    }

    @Test
    void parsesQuotedCsvRowsAndPreservesTrailingEmptyCells() {
        DatevFile file = DatevFile.withHeader("A;B;C;D");

        file.append("one;\"two;inner\";\"a\"\"b\";");

        assertEquals(List.of("one", "two;inner", "a\"b", ""), file.rows().get(0));
    }

    @Test
    void leadingCommentMarkerIsPreservedAsData() {
        DatevFile file = DatevFile.withHeader("A");

        file.append("#value");

        assertEquals(List.of("#value"), file.rows().get(0));
    }

    @Test
    void stringRowsSupportCustomHeadersBeyondUnivocityDefaults() {
        int columnCount = 513;
        String[] headers = new String[columnCount];
        String[] values = new String[columnCount];
        for (int index = 0; index < columnCount; index++) {
            headers[index] = "H" + index;
            values[index] = "V" + index;
        }
        DatevFile wide = DatevFile.withHeader(headers);
        DatevFile longValue = DatevFile.withHeader("A");
        String value = "x".repeat(4097);

        wide.append(String.join(";", values));
        longValue.append(value);

        assertEquals(columnCount, wide.rows().get(0).size());
        assertEquals("V512", wide.rows().get(0).get(512));
        assertEquals(value, longValue.rows().get(0).get(0));
    }

    @Test
    void rejectsMalformedOrMultilineCsvRowsAtomically() {
        DatevFile file = DatevFile.withHeader("A;B");

        assertThrows(IllegalArgumentException.class, () -> file.append("a;\"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> file.append("a;\"b\"\""));
        assertThrows(IllegalArgumentException.class, () -> file.append("a;b\"c"));
        assertThrows(IllegalArgumentException.class, () -> file.append("a;\"b\"suffix"));
        assertThrows(IllegalArgumentException.class, () -> file.append("a;b\n"));

        assertEquals(0, file.rowCount());
    }

    @Test
    void mapRowsAreSparseAndIndependentOfMapIterationOrder() {
        DatevFile file = DatevFile.withHeader("A;B;C");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("C", 3);
        values.put("A", "1");
        values.put("B", null);

        file.append(values);

        assertEquals("1", file.rows().get(0).get(0));
        assertNull(file.rows().get(0).get(1));
        assertEquals("3", file.rows().get(0).get(2));
    }

    @Test
    void namedRowsAcceptCanonicalAndRenamedHeadersAndRejectDoubleAssignment() {
        DatevFile file = DatevFile.builder(DatevHeader.of(List.of("A", "B")))
                .renameHeader("A", "Amount")
                .build();

        file.append(Map.of("Amount", "1", "B", "2"));
        assertEquals(List.of("1", "2"), file.rows().get(0));

        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(Map.of("A", "1", "Amount", "2"))
        );
        assertThrows(IllegalArgumentException.class, () -> file.append(Map.of("unknown", "x")));
        assertEquals(1, file.rowCount());
    }

    @Test
    void formattedColumnsAreAlignedAfterRenameAndReorder() {
        DatevFile file = DatevFile.builder(DatevHeader.of(List.of("Amount", "Side", "Account")))
                .renameHeader("Account", "Konto")
                .headerOrder(List.of("Account", "Amount", "Side"))
                .build();
        DecimalFormat format = new DecimalFormat(
                "0.00",
                DecimalFormatSymbols.getInstance(Locale.GERMANY)
        );

        file.append(
                DatevColumn.of("Konto", 1000),
                DatevColumn.formatted("Amount", new BigDecimal("12.5"), format),
                DatevColumn.of("Side", "S")
        );

        assertEquals(List.of("1000", "12,50", "S"), file.rows().get(0));
    }

    @Test
    void collectionOfColumnsUsesNamedMethodBecauseOfJavaErasure() {
        DatevFile file = DatevFile.withHeader("A;B");

        file.appendColumns(List.of(DatevColumn.of("B", "2"), DatevColumn.of("A", "1")));

        assertEquals(List.of("1", "2"), file.rows().get(0));
    }

    @Test
    void collectionOfColumnsCanAlsoBePassedDirectlyThroughIterableOverload() {
        DatevFile file = DatevFile.withHeader("A;B");
        List<DatevColumn<?>> columns = List.of(
                DatevColumn.of("B", "2"),
                DatevColumn.of("A", "1")
        );

        file.append(columns);

        assertEquals(List.of("1", "2"), file.rows().get(0));
    }

    @Test
    void failingColumnAppendIsAtomic() {
        DatevFile file = DatevFile.withHeader("A;B");
        DatevColumn<Integer> broken = DatevColumn.formatted("B", 2, value -> {
            throw new IllegalStateException("broken");
        });

        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(DatevColumn.of("A", 1), broken)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(DatevColumn.of("A", 1), DatevColumn.of("A", 2))
        );
        assertEquals(0, file.rowCount());
    }

    @Test
    void structuralColumnErrorsAreDetectedBeforeAnyFormatterRuns() {
        DatevFile file = DatevFile.withHeader("A;B");
        AtomicInteger invocations = new AtomicInteger();
        DatevColumn<Integer> first = DatevColumn.formatted("A", 1, value -> {
            invocations.incrementAndGet();
            return value.toString();
        });

        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(first, DatevColumn.of("A", 2))
        );

        assertEquals(0, invocations.get());
        assertEquals(0, file.rowCount());
    }

    @Test
    void formatterRunsExactlyOnceAtAppendTimeAndNeverDuringWriting() {
        DatevFile file = DatevFile.withHeader("A");
        AtomicInteger invocations = new AtomicInteger();

        file.append(DatevColumn.formatted("A", 1, value -> {
            invocations.incrementAndGet();
            return value.toString();
        }));
        file.toCsvString();
        file.toCsvString();

        assertEquals(1, invocations.get());
    }

    @Test
    void rejectsControlCharactersInEveryAppendShape() {
        DatevFile file = DatevFile.withHeader("A");

        assertThrows(IllegalArgumentException.class, () -> file.append(new String[]{"a\tb"}));
        assertThrows(IllegalArgumentException.class, () -> file.append(Map.of("A", "a\rb")));
        assertThrows(IllegalArgumentException.class, () -> file.append(DatevColumn.of("A", "a\nb")));
        assertThrows(IllegalArgumentException.class, () -> file.append(new String[]{"a\u2028b"}));
        assertThrows(IllegalArgumentException.class, () -> file.append(Map.of("A", "a\u2029b")));
        assertEquals(0, file.rowCount());
    }
}
