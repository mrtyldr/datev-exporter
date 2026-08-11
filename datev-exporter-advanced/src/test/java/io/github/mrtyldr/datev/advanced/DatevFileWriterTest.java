package io.github.mrtyldr.datev.advanced;

import io.github.mrtyldr.datev.core.DatevValidationMode;

import com.univocity.parsers.csv.CsvWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevFileWriterTest {

    @Test
    void writesHeaderAndEscapedRowsWithCrLf() {
        DatevFile file = DatevFile.withHeader("A;B;C");
        file.append(List.of("a;b", "x\"y", ""));
        file.append(Map.of("A", "plain", "C", "tail"));

        assertEquals(
                "A;B;C\r\n\"a;b\";\"x\"\"y\";\r\nplain;;tail\r\n",
                file.toCsvString()
        );
    }

    @Test
    void configuredCsvWriterConsumesDatevFileDirectly() {
        DatevFile file = DatevFile.withHeader("A;B");
        file.append(new String[]{"1", "2"});
        StringWriter output = new StringWriter();
        CsvWriter writer = new CsvWriter(output, file.csvWriterSettings());

        writer.writeRows(file);
        writer.flush();

        assertEquals("A;B\r\n1;2\r\n", output.toString());
    }

    @Test
    void explicitHeaderFollowedByDirectRowsDoesNotDuplicateHeader() {
        DatevFile file = DatevFile.withHeader("A;B");
        file.append(new String[]{"1", "2"});
        StringWriter output = new StringWriter();
        CsvWriter writer = file.newCsvWriter(output);

        writer.writeHeaders();
        writer.writeRows(file);
        writer.flush();

        assertEquals("A;B\r\n1;2\r\n", output.toString());
    }

    @Test
    void directEmptyIterableNeedsExplicitHeaderButConvenienceMethodWritesIt() {
        DatevFile file = DatevFile.withHeader("A;B");
        StringWriter directOutput = new StringWriter();
        CsvWriter directWriter = file.newCsvWriter(directOutput);

        directWriter.writeRows(file);
        directWriter.flush();

        assertEquals("", directOutput.toString());
        assertEquals("A;B\r\n", file.toCsvString());
    }

    @Test
    void defaultSchemaSelectivelyQuotesDatevTextFields() {
        DatevFile file = DatevFile.withDefaults();
        Map<String, Object> row = new HashMap<>();
        row.put("Umsatz (ohne Soll/Haben-Kz)", "12,50");
        row.put("Soll/Haben-Kennzeichen", "S");
        row.put("WKZ Umsatz", "EUR");
        row.put("Konto", "1000");
        row.put("Gegenkonto (ohne BU-Schlüssel)", "8400");
        row.put("BU-Schlüssel", "1234");
        row.put("Belegdatum", "1008");
        row.put("Buchungstext", "Invoice 42");
        file.append(row);

        String[] lines = file.toCsvString().split("\\r\\n", -1);
        String[] encodedHeaders = lines[0].split(";", -1);
        String[] encodedValues = lines[1].split(";", -1);

        assertEquals("Umsatz (ohne Soll/Haben-Kz)", encodedHeaders[0]);
        assertEquals("Soll/Haben-Kennzeichen", encodedHeaders[1]);
        assertEquals("12,50", encodedValues[0]);
        assertEquals("\"S\"", encodedValues[1]);
        assertEquals("\"EUR\"", encodedValues[2]);
        assertEquals("1000", encodedValues[6]);
        assertEquals("\"1234\"", encodedValues[8]);
        assertEquals("1008", encodedValues[9]);
        assertEquals("\"Invoice 42\"", encodedValues[13]);
    }

    @Test
    void defaultSchemaQuotesMissingTextFieldsButNotMissingNumericFields() {
        DatevFile file = DatevFile.builder()
                .validationMode(DatevValidationMode.FIELD_LEVEL)
                .build();
        file.append(Map.of("Konto", "1000"));

        String[] encodedValues = file.toCsvString()
                .split("\\r\\n", -1)[1]
                .split(";", -1);

        assertEquals("", encodedValues[0]);
        assertEquals("\"\"", encodedValues[1]);
        assertEquals("\"\"", encodedValues[2]);
        assertEquals("1000", encodedValues[6]);
    }

    @Test
    void customSchemasKeepGenericCsvQuotingUnlessCallerCustomizesSettings() {
        DatevFile file = DatevFile.withHeader("Text;Number");
        file.append(new String[]{"plain", "1000"});

        assertEquals("Text;Number\r\nplain;1000\r\n", file.toCsvString());
    }

    @Test
    void callerCanSafelyCustomizeQuotingForCustomSchema() {
        DatevFile file = DatevFile.withHeader("Text;Number");
        file.append(new String[]{"plain", "1000"});
        var settings = file.csvWriterSettings();
        settings.quoteFields("Text");
        StringWriter output = new StringWriter();
        CsvWriter writer = file.newCsvWriter(output, settings);

        file.writeTo(writer);

        assertEquals("\"Text\";Number\r\n\"plain\";1000\r\n", output.toString());
    }

    @Test
    void convenienceWriterIncludesTheHeaderForAnEmptyFile() {
        DatevFile file = DatevFile.withHeader("A;B");

        assertEquals("A;B\r\n", file.toCsvString());
    }

    @Test
    void outputStreamUsesWindows1252AndRemainsOpen() throws IOException {
        DatevFile file = DatevFile.withHeader("Name");
        file.append(new String[]{"Müller"});
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();

        file.writeTo(output);
        output.write('!');

        assertFalse(output.closed);
        String csvAndMarker = output.delegate.toString(Charset.forName("windows-1252"));
        assertEquals("Name\r\nMüller\r\n!", csvAndMarker);
    }

    @Test
    void byteAndStringConveniencesProduceTheSameLogicalCsv() {
        DatevFile file = DatevFile.withHeader("A;B");
        file.append(new String[]{"ä", null});

        String decoded = new String(file.toByteArray(), file.charset());

        assertEquals(file.toCsvString(), decoded);
        assertTrue(decoded.endsWith("ä;\r\n"));
    }

    @Test
    void unmappableOutputFailsWithoutReplacementOrClosingCallerStream() throws IOException {
        DatevFile file = DatevFile.withHeader("A");
        file.append(new String[]{"🙂"});
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();

        assertThrows(RuntimeException.class, () -> file.writeTo(output));
        output.write('!');

        assertFalse(output.closed);
        String partialOutput = output.delegate.toString(StandardCharsets.US_ASCII);
        assertFalse(partialOutput.contains("?"));
        assertTrue(partialOutput.endsWith("!"));
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
}
