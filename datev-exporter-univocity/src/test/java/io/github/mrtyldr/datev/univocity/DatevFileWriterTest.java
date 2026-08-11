package io.github.mrtyldr.datev.univocity;

import io.github.mrtyldr.datev.core.DatevSchema;

import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevFileWriterTest {
    @Test
    void convenienceOutputUsesUnquotedFixedHeaderAndExactDatevQuoting() {
        DatevFile file = DatevFile.withDefaults();
        List<String> row = emptyRow(file.schema());
        row.set(0, "12;50");
        row.set(1, "S");
        row.set(2, "EUR");
        row.set(6, "1000");
        row.set(8, "1234");
        row.set(9, "1008");
        row.set(13, "Invoice \"42\"");
        file.append(row);

        String expectedHeader = String.join(";", file.headers()) + "\r\n";
        String expectedRow = encodeExpectedRow(file.schema(), row) + "\r\n";

        assertEquals(expectedHeader + expectedRow, file.toCsvString());
        assertTrue(expectedRow.startsWith("\"12;50\";\"S\";\"EUR\";"));
        assertTrue(expectedRow.contains(";1000;;\"1234\";1008;"));
        assertTrue(expectedRow.contains(";\"Invoice \"\"42\"\"\";"));
    }

    @Test
    void missingTextFieldsAreQuotedButMissingNumericFieldsAreEmpty() {
        DatevFile file = DatevFile.withDefaults();
        file.append(Map.of("Konto", "1000"));

        String dataLine = file.toCsvString().substring(
                String.join(";", file.headers()).length() + 2
        );
        String expected = encodeExpectedRow(file.schema(), file.rows().get(0)) + "\r\n";

        assertEquals(expected, dataLine);
        assertTrue(dataLine.startsWith(";\"\";\"\";"));
    }

    @Test
    void leadingHashInFirstCellIsDataRatherThanAWriterComment() {
        DatevFile file = DatevFile.withDefaults();
        file.append(Map.of("Umsatz (ohne Soll/Haben-Kz)", "#value"));

        String dataLine = file.toCsvString().substring(
                String.join(";", file.headers()).length() + 2
        );

        assertTrue(dataLine.startsWith("#value;\"\";\"\";"));
        assertEquals('\0', file.csvWriterSettings().getFormat().getComment());
    }

    @Test
    void settingsAreFreshStrictAndConfiguredForDirectIterableWriting() {
        DatevFile file = DatevFile.legacyV12();
        CsvWriterSettings first = file.csvWriterSettings();
        CsvWriterSettings second = file.csvWriterSettings();

        first.getFormat().setDelimiter(',');
        first.setHeaders("changed");

        assertNotSame(first, second);
        assertEquals(';', second.getFormat().getDelimiter());
        assertEquals("\r\n", new String(second.getFormat().getLineSeparator()));
        assertEquals("", second.getNullValue());
        assertEquals("", second.getEmptyValue());
        assertTrue(second.isQuoteEscapingEnabled());
        assertFalse(second.isHeaderWritingEnabled());
        assertFalse(second.getSkipEmptyLines());
        assertFalse(second.getIgnoreLeadingWhitespaces());
        assertFalse(second.getIgnoreTrailingWhitespaces());
        assertArrayEquals(file.headers().toArray(String[]::new), second.getHeaders());
    }

    @Test
    void configuredCsvWriterConsumesDatevFileAsExactDataRowsWithoutHeading() {
        DatevFile file = DatevFile.withDefaults();
        file.append(Map.of("Konto", "1000"));
        StringWriter output = new StringWriter();
        CsvWriter writer = file.newCsvWriter(output);

        writer.writeRows(file);
        writer.flush();

        String expected = encodeExpectedRow(file.schema(), file.rows().get(0)) + "\r\n";
        assertEquals(expected, output.toString());
        assertFalse(output.toString().contains("Soll/Haben-Kennzeichen"));
    }

    @Test
    void writeDataToCallerProvidedCsvWriterWritesOnlyRowsAndFlushes() {
        DatevFile file = DatevFile.legacyV12();
        file.append(Map.of("Konto", "1000"));
        StringWriter output = new StringWriter();
        CsvWriter writer = file.newCsvWriter(output);

        file.writeDataTo(writer);

        assertEquals(
                encodeExpectedRow(file.schema(), file.rows().get(0)) + "\r\n",
                output.toString()
        );
        assertFalse(output.toString().contains("Umsatz (ohne Soll/Haben-Kz)"));
    }

    @Test
    void customWriterSettingsAreSnapshottedAndForcedToDataOnly() {
        DatevFile file = DatevFile.withDefaults();
        file.append(Map.of("Konto", "1000"));
        CsvWriterSettings callerSettings = file.csvWriterSettings();
        callerSettings.setHeaderWritingEnabled(true);
        StringWriter output = new StringWriter();

        CsvWriter writer = file.newCsvWriter(output, callerSettings);
        writer.writeRows(file);
        writer.flush();

        assertTrue(callerSettings.isHeaderWritingEnabled());
        assertEquals(
                encodeExpectedRow(file.schema(), file.rows().get(0)) + "\r\n",
                output.toString()
        );
    }

    @Test
    void emptyConvenienceOutputStillContainsCanonicalHeader() {
        DatevFile file = DatevFile.legacyV12();

        assertEquals(String.join(";", file.headers()) + "\r\n", file.toCsvString());
    }

    @Test
    void byteOutputUsesWindows1252AndLeavesCallerStreamOpen() throws IOException {
        DatevFile file = DatevFile.withDefaults();
        file.append(Map.of("Buchungstext", "Müller"));
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();

        file.writeTo(output);
        output.write('!');

        assertFalse(output.closed);
        String decoded = output.delegate.toString(Charset.forName("windows-1252"));
        assertTrue(decoded.contains("\"Müller\""));
        assertTrue(decoded.endsWith("!"));
        assertEquals(file.toCsvString(), new String(file.toByteArray(), file.charset()));
    }

    @Test
    void characterWriterAliasesDoNotCloseCallerWriter() throws IOException {
        DatevFile file = DatevFile.withDefaults();
        CloseTrackingWriter output = new CloseTrackingWriter();

        file.write(output);
        output.write('!');

        assertFalse(output.closed);
        assertTrue(output.delegate.toString().endsWith("\r\n!"));
    }

    @Test
    void unmappableValuesFailAtomicallyDuringAppendInsteadOfAtWriteTime() {
        DatevFile file = DatevFile.withDefaults();

        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(Map.of("Buchungstext", "🙂"))
        );

        assertEquals(0, file.rowCount());
        assertEquals(String.join(";", file.headers()) + "\r\n", file.toCsvString());
    }

    private static List<String> emptyRow(DatevSchema schema) {
        return new ArrayList<>(Collections.nCopies(schema.columnCount(), null));
    }

    private static String encodeExpectedRow(DatevSchema schema, List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(';');
            }
            String value = values.get(index);
            if (value == null) {
                if (schema.isTextColumn(index)) {
                    result.append("\"\"");
                }
                continue;
            }
            boolean quote = schema.isTextColumn(index)
                    || value.indexOf(';') >= 0
                    || value.indexOf('"') >= 0;
            if (quote) {
                result.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else {
                result.append(value);
            }
        }
        return result.toString();
    }

    private static final class CloseTrackingOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private boolean closed;

        @Override
        public void write(int value) {
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            delegate.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class CloseTrackingWriter extends Writer {
        private final StringWriter delegate = new StringWriter();
        private boolean closed;

        @Override
        public void write(char[] chars, int offset, int length) {
            delegate.write(chars, offset, length);
        }

        @Override
        public void flush() {
            delegate.flush();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
