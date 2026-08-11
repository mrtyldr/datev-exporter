package io.github.mrtyldr.datev.advanced.univocity;

import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import io.github.mrtyldr.datev.advanced.DatevFile;
import io.github.mrtyldr.datev.core.DatevHeader;
import io.github.mrtyldr.datev.core.DatevMetadata;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevUnivocityWritersTest {

    @Test
    void settingsCarryTheDatevWriterDefaults() {
        DatevFile file = DatevFile.withDefaults();
        CsvWriterSettings settings = DatevUnivocityWriters.csvWriterSettings(file);

        assertEquals(DatevHeader.current(), file.header());
        assertEquals(Charset.forName("windows-1252"), file.charset());
        assertEquals(';', settings.getFormat().getDelimiter());
        assertEquals("\r\n", new String(settings.getFormat().getLineSeparator()));
        assertEquals("", settings.getNullValue());
        assertEquals("", settings.getEmptyValue());
        assertTrue(settings.isQuoteEscapingEnabled());
        assertTrue(settings.isHeaderWritingEnabled());
        assertFalse(settings.getSkipEmptyLines());
        assertFalse(settings.getIgnoreLeadingWhitespaces());
        assertFalse(settings.getIgnoreTrailingWhitespaces());
        assertArrayEquals(file.headers().toArray(String[]::new), settings.getHeaders());
    }

    @Test
    void eachSettingsCallReturnsAnIndependentConfiguration() {
        DatevFile file = DatevFile.withHeader("A;B");
        CsvWriterSettings first = DatevUnivocityWriters.csvWriterSettings(file);
        CsvWriterSettings second = DatevUnivocityWriters.csvWriterSettings(file);

        first.getFormat().setDelimiter(',');
        first.setHeaders("changed");

        assertNotSame(first, second);
        assertEquals(';', second.getFormat().getDelimiter());
        assertArrayEquals(new String[]{"A", "B"}, second.getHeaders());
        assertEquals(List.of("A", "B"), file.headers());
    }

    @Test
    void configuredCsvWriterConsumesDatevFileDirectly() {
        DatevFile file = DatevFile.withHeader("A;B");
        file.append(new String[]{"1", "2"});
        StringWriter output = new StringWriter();
        CsvWriter writer = new CsvWriter(output, DatevUnivocityWriters.csvWriterSettings(file));

        writer.writeRows(file);
        writer.flush();

        assertEquals("A;B\r\n1;2\r\n", output.toString());
    }

    @Test
    void explicitHeaderFollowedByDirectRowsDoesNotDuplicateHeader() {
        DatevFile file = DatevFile.withHeader("A;B");
        file.append(new String[]{"1", "2"});
        StringWriter output = new StringWriter();
        CsvWriter writer = DatevUnivocityWriters.newCsvWriter(file, output);

        writer.writeHeaders();
        writer.writeRows(file);
        writer.flush();

        assertEquals("A;B\r\n1;2\r\n", output.toString());
    }

    @Test
    void directEmptyIterableNeedsExplicitHeaderButConvenienceMethodWritesIt() {
        DatevFile file = DatevFile.withHeader("A;B");
        StringWriter directOutput = new StringWriter();
        CsvWriter directWriter = DatevUnivocityWriters.newCsvWriter(file, directOutput);

        directWriter.writeRows(file);
        directWriter.flush();

        assertEquals("", directOutput.toString());
        assertEquals("A;B\r\n", file.toCsvString());
    }

    @Test
    void callerCanSafelyCustomizeQuotingForCustomSchema() {
        DatevFile file = DatevFile.withHeader("Text;Number");
        file.append(new String[]{"plain", "1000"});
        CsvWriterSettings settings = DatevUnivocityWriters.csvWriterSettings(file);
        settings.quoteFields("Text");
        StringWriter output = new StringWriter();
        CsvWriter writer = DatevUnivocityWriters.newCsvWriter(file, output, settings);

        DatevUnivocityWriters.writeTo(file, writer);

        assertEquals("\"Text\";Number\r\n\"plain\";1000\r\n", output.toString());
    }

    @Test
    void bookingRowsMatchTheBuiltInWriterButTheHeadingIsQuoted() {
        DatevFile viaUnivocity = DatevFile.withDefaults();
        DatevFile builtIn = DatevFile.withDefaults();
        viaUnivocity.append(validBooking());
        builtIn.append(validBooking());

        StringWriter output = new StringWriter();
        DatevUnivocityWriters.writeTo(
                viaUnivocity, DatevUnivocityWriters.newCsvWriter(viaUnivocity, output));

        String[] univocityRecords = output.toString().split("\r\n", 2);
        String[] builtInRecords = builtIn.toCsvString().split("\r\n", 2);

        // The booking rows are byte-identical.
        assertEquals(builtInRecords[1], univocityRecords[1]);
        // A single CsvWriter cannot quote rows without also quoting the heading, so the heading
        // differs. DatevFile.writeTo(OutputStream/Writer) stays the canonical unquoted-heading path.
        assertFalse(builtInRecords[0].contains("\"Soll/Haben-Kennzeichen\""));
        assertTrue(univocityRecords[0].contains("\"Soll/Haben-Kennzeichen\""));
    }

    @Test
    void arbitraryCsvWriterCannotSilentlyDropMetadata() {
        DatevFile file = DatevFile.withDefaults(metadata());
        file.append(validBooking());
        StringWriter completeOutput = new StringWriter();
        CsvWriter completeWriter = DatevUnivocityWriters.newCsvWriter(file, completeOutput);

        assertThrows(IllegalStateException.class,
                () -> DatevUnivocityWriters.writeTo(file, completeWriter));
        assertEquals("", completeOutput.toString());

        StringWriter dataOutput = new StringWriter();
        DatevUnivocityWriters.writeDataTo(
                file, DatevUnivocityWriters.newCsvWriter(file, dataOutput));

        assertFalse(dataOutput.toString().startsWith("\"EXTF\""));
        assertTrue(dataOutput.toString().contains("12,50;\"S\""));
    }

    @Test
    void rejectsNullArguments() {
        DatevFile file = DatevFile.withHeader("A;B");

        assertThrows(NullPointerException.class,
                () -> DatevUnivocityWriters.csvWriterSettings(null));
        assertThrows(NullPointerException.class,
                () -> DatevUnivocityWriters.newCsvWriter(file, (StringWriter) null));
        assertThrows(NullPointerException.class,
                () -> DatevUnivocityWriters.writeDataTo(file, null));
    }

    private static DatevMetadata metadata() {
        return DatevMetadata.bookingBatchV13()
                .createdAt(LocalDateTime.of(2026, 8, 11, 9, 30))
                .origin("RE")
                .exportedBy("test")
                .advisorNumber(1001)
                .clientNumber(1)
                .fiscalYearStart(LocalDate.of(2026, 1, 1))
                .accountLength(4)
                .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .build();
    }

    private static Map<String, String> validBooking() {
        return Map.of(
                "Umsatz (ohne Soll/Haben-Kz)", "12,50",
                "Soll/Haben-Kennzeichen", "S",
                "Konto", "1000",
                "Gegenkonto (ohne BU-Schlüssel)", "8400",
                "Belegdatum", "1008"
        );
    }
}
