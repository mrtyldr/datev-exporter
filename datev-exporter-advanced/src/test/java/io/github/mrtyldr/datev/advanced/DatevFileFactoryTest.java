package io.github.mrtyldr.datev.advanced;

import com.univocity.parsers.csv.CsvWriterSettings;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevFileFactoryTest {

    @Test
    void defaultFactoryUsesCurrentHeaderAndDatevWriterDefaults() {
        DatevFile file = DatevFile.withDefaults();
        CsvWriterSettings settings = file.csvWriterSettings();

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
    void customHeaderFactoriesAreEquivalentAndDefensive() {
        String[] array = {"A", "B", "C"};
        List<String> list = new ArrayList<>(List.of("A", "B", "C"));

        DatevFile parsed = DatevFile.withHeader("A;B;C");
        DatevFile fromArray = DatevFile.withHeader(array);
        DatevFile fromList = DatevFile.withHeader(list);
        array[0] = "changed";
        list.set(0, "changed");

        assertEquals(List.of("A", "B", "C"), parsed.headers());
        assertEquals(parsed.header(), fromArray.header());
        assertEquals(parsed.header(), fromList.header());
    }

    @Test
    void builderRenamesReordersAndChangesCharsetWithoutSharingRows() {
        DatevHeader header = DatevHeader.of(List.of("A", "B", "C"));
        DatevFile.Builder builder = DatevFile.builder(header)
                .renameHeader("A", "Amount")
                .headerOrder("C", "A", "B")
                .charset(StandardCharsets.UTF_8);

        DatevFile first = builder.build();
        DatevFile second = builder.build();
        first.append(Map.of("A", "1"));

        assertEquals(List.of("C", "Amount", "B"), first.headers());
        assertEquals(StandardCharsets.UTF_8, first.charset());
        assertEquals(1, first.rowCount());
        assertEquals(0, second.rowCount());
    }

    @Test
    void eachSettingsCallReturnsAnIndependentConfiguration() {
        DatevFile file = DatevFile.withHeader("A;B");
        CsvWriterSettings first = file.csvWriterSettings();
        CsvWriterSettings second = file.csvWriterSettings();

        first.getFormat().setDelimiter(',');
        first.setHeaders("changed");

        assertNotSame(first, second);
        assertEquals(';', second.getFormat().getDelimiter());
        assertArrayEquals(new String[]{"A", "B"}, second.getHeaders());
        assertEquals(List.of("A", "B"), file.headers());
    }

    @Test
    void publicSnapshotsCannotMutateFileState() {
        DatevFile file = DatevFile.withHeader("A;B");
        file.append(new String[]{"1", "2"});

        assertThrows(UnsupportedOperationException.class, () -> file.headers().add("C"));
        assertThrows(UnsupportedOperationException.class, () -> file.rows().add(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> file.rows().get(0).set(0, "x"));

        assertEquals(List.of(List.of("1", "2")), file.rows());
    }
}
