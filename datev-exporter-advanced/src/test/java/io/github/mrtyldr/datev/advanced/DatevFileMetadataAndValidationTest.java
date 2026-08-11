package io.github.mrtyldr.datev.advanced;

import io.github.mrtyldr.datev.core.DatevHeader;
import io.github.mrtyldr.datev.core.DatevMetadata;
import io.github.mrtyldr.datev.core.DatevValidationError;
import io.github.mrtyldr.datev.core.DatevValidationException;
import io.github.mrtyldr.datev.core.DatevValidationMode;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevFileMetadataAndValidationTest {

    @Test
    void writesCompleteExtfInMetadataHeadingRowOrder() {
        DatevMetadata metadata = metadata();
        DatevFile file = DatevFile.withDefaults(metadata);
        file.append(validBooking());

        String csv = file.toCsvString();
        String[] records = csv.substring(0, csv.length() - 2).split("\\r\\n", -1);

        assertEquals(3, records.length);
        assertEquals(metadata.toCsvLine(), records[0]);
        assertEquals(String.join(";", DatevHeader.current().names()), records[1]);
        assertFalse(records[1].contains("\"Soll/Haben-Kennzeichen\""));

        String[] values = records[2].split(";", -1);
        assertEquals(125, values.length);
        assertEquals("12,50", values[0]);
        assertEquals("\"S\"", values[1]);
        assertEquals("1000", values[6]);
        assertEquals("8400", values[7]);
        assertEquals("1008", values[9]);
        assertTrue(csv.endsWith("\r\n"));
        assertTrue(file.isCompleteExtf());
        assertEquals(metadata, file.metadata().orElseThrow());
    }

    @Test
    void completeExtfWithoutBookingsStillWritesMetadataAndHeading() {
        DatevFile file = DatevFile.withDefaults(metadata());

        String[] records = file.toCsvString().split("\\r\\n", -1);

        assertEquals(3, records.length);
        assertEquals(file.metadata().orElseThrow().toCsvLine(), records[0]);
        assertEquals(String.join(";", file.headers()), records[1]);
        assertEquals("", records[2]);
    }

    @Test
    void metadataCannotBeCombinedWithAnInconsistentOutputContract() {
        DatevMetadata metadata = metadata();

        assertThrows(IllegalStateException.class, () -> DatevFile.builder()
                .renameHeader("Konto", "Sachkonto")
                .metadata(metadata)
                .build());
        assertThrows(IllegalStateException.class, () -> DatevFile.builder(DatevHeader.legacyV12())
                .metadata(metadata)
                .build());
        assertThrows(IllegalStateException.class, () -> DatevFile.builder()
                .validationMode(DatevValidationMode.FIELD_LEVEL)
                .metadata(metadata)
                .build());
        assertThrows(IllegalStateException.class, () -> DatevFile.builder()
                .charset(StandardCharsets.UTF_8)
                .metadata(metadata)
                .build());
    }


    @Test
    void officialSchemasDefaultToStrictAndRejectRowsAtomically() {
        DatevFile file = DatevFile.withDefaults();

        DatevValidationException exception = assertThrows(
                DatevValidationException.class,
                () -> file.append(Map.of("Konto", "not-an-account"))
        );

        assertEquals(DatevValidationMode.STRICT, file.validationMode());
        assertTrue(exception.errors().stream()
                .anyMatch(error -> error.code() == DatevValidationError.Code.REQUIRED_FIELD));
        assertTrue(exception.errors().stream()
                .anyMatch(error -> error.canonicalKey().equals("Konto")));
        assertEquals(0, file.rowCount());
    }

    @Test
    void fieldLevelModeAllowsSparseRowsButStillChecksSuppliedValues() {
        DatevFile file = DatevFile.builder()
                .validationMode(DatevValidationMode.FIELD_LEVEL)
                .build();

        file.append(Map.of("Konto", "1000"));
        assertThrows(DatevValidationException.class, () -> file.append(Map.of("Konto", "ABC")));

        assertEquals(1, file.rowCount());
    }

    @Test
    void customSchemasDefaultToNoDatevSemantics() {
        DatevFile file = DatevFile.withHeader("Amount;Account;Date");

        file.append(List.of("not-an-amount", "ABC", "3102"));

        assertEquals(DatevValidationMode.NONE, file.validationMode());
        assertEquals(1, file.rowCount());
        assertFalse(file.isCompleteExtf());
        assertTrue(file.metadata().isEmpty());
    }

    @Test
    void legacyMetadataRequiresTheLegacyHeaderAndViceVersa() {
        DatevMetadata legacy = DatevMetadata.bookingBatchV12()
                .createdAt(LocalDateTime.of(2026, 8, 11, 9, 30))
                .origin("RE")
                .exportedBy("test")
                .advisorNumber(1001)
                .clientNumber(1)
                .fiscalYearStart(LocalDate.of(2026, 1, 1))
                .accountLength(4)
                .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .build();

        DatevFile file = DatevFile.builder(DatevHeader.legacyV12()).metadata(legacy).build();

        assertTrue(file.isCompleteExtf());
        assertEquals(12, file.metadata().orElseThrow().formatVersion());
        assertEquals("12", file.toCsvString().split(";")[4]);

        assertThrows(IllegalStateException.class,
                () -> DatevFile.builder().metadata(legacy).build());
        assertThrows(IllegalStateException.class,
                () -> DatevFile.builder(DatevHeader.legacyV12()).metadata(metadata()).build());
        assertThrows(IllegalStateException.class,
                () -> DatevFile.builder(DatevHeader.parse("A;B")).metadata(legacy).build());
    }

    @Test
    void renameAndReorderRetainOfficialFieldSemantics() {
        List<String> reversed = new ArrayList<>(DatevHeader.current().keys());
        java.util.Collections.reverse(reversed);
        DatevFile file = DatevFile.builder()
                .renameHeader("Konto", "Sachkonto")
                .headerOrder(reversed)
                .build();

        file.append(Map.of(
                "Umsatz (ohne Soll/Haben-Kz)", "12,50",
                "Soll/Haben-Kennzeichen", "H",
                "Sachkonto", "1000",
                "Gegenkonto (ohne BU-Schlüssel)", "8400",
                "Belegdatum", "1008"
        ));
        assertThrows(DatevValidationException.class, () -> file.append(Map.of(
                "Umsatz (ohne Soll/Haben-Kz)", "12,50",
                "Soll/Haben-Kennzeichen", "H",
                "Sachkonto", "invalid",
                "Gegenkonto (ohne BU-Schlüssel)", "8400",
                "Belegdatum", "1008"
        )));

        assertEquals("1000", file.rows().get(0).get(file.header().indexOf("Sachkonto")));
        assertEquals(1, file.rowCount());
    }

    @Test
    void officialRowLimitIsCheckedAtTheDocumentedBoundary() {
        DatevFile.ensureCanAppendRow(DatevHeader.current(), DatevFile.MAX_DATA_ROWS - 1);

        assertThrows(
                IllegalStateException.class,
                () -> DatevFile.ensureCanAppendRow(DatevHeader.current(), DatevFile.MAX_DATA_ROWS)
        );
        DatevFile.ensureCanAppendRow(DatevHeader.of(List.of("A")), DatevFile.MAX_DATA_ROWS);
    }

    private static DatevMetadata metadata() {
        return DatevMetadata.bookingBatchV13()
                .createdAt(LocalDateTime.of(2024, 8, 10, 12, 30, 45, 123_000_000))
                .origin("RE")
                .advisorNumber(1001)
                .clientNumber(1)
                .fiscalYearStart(LocalDate.of(2024, 1, 1))
                .accountLength(4)
                .period(LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31))
                .description("August 2024")
                .applicationInformation("datev-exporter")
                .build();
    }

    private static Map<String, String> validBooking() {
        return Map.of(
                "Umsatz (ohne Soll/Haben-Kz)", "12,50",
                "Soll/Haben-Kennzeichen", "S",
                "Konto", "1000",
                "Gegenkonto (ohne BU-Schlüssel)", "8400",
                "Belegdatum", "1008",
                "Buchungstext", "Invoice 42"
        );
    }
}
