package io.github.mrtyldr.datev.plain;

import io.github.mrtyldr.datev.core.DatevMetadata;
import io.github.mrtyldr.datev.core.DatevRowSamples;
import io.github.mrtyldr.datev.core.DatevSchema;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevPlainMetadataTest {

    @Test
    void writesTheManagementRecordBeforeTheHeading() {
        DatevFile file = DatevFile.builder().metadata(metadata()).build();
        file.append(DatevRowSamples.requiredFieldsRow());

        List<String> records = List.of(file.toCsvString().split("\r\n"));

        assertTrue(file.isCompleteExtf());
        assertEquals(metadata().toCsvLine(), records.get(0));
        assertEquals(DatevSchema.CURRENT_V13.headers().get(0),
                records.get(1).split(";")[0]);
        assertEquals(3, records.size());
    }

    @Test
    void byteOutputAndCharacterOutputAgree() {
        DatevFile file = DatevFile.builder().metadata(metadata()).build();
        file.append(DatevRowSamples.requiredFieldsRow());

        assertEquals(file.toCsvString(),
                new String(file.toByteArray(), file.charset()));
        assertFalse(new String(file.toByteArray(), StandardCharsets.ISO_8859_1).isEmpty());
    }

    @Test
    void omittingMetadataKeepsTheHeadingAndRowsOnlyOutput() {
        DatevFile file = DatevFile.withDefaults();

        assertFalse(file.isCompleteExtf());
        assertTrue(file.metadata().isEmpty());
        assertFalse(file.toCsvString().startsWith("\"EXTF\""));
        assertEquals(DatevFile.builder().build().toCsvString(), file.toCsvString());
    }

    @Test
    void streamWriterEmitsTheManagementRecordAtConstruction() {
        StringWriter output = new StringWriter();

        try (DatevStreamWriter writer = DatevStreamWriter.builder()
                .metadata(metadata())
                .build(output)) {
            assertTrue(writer.isCompleteExtf());
            assertTrue(output.toString().startsWith(metadata().toCsvLine() + "\r\n"));
            writer.append(DatevRowSamples.requiredFieldsRow());
        }

        DatevFile buffered = DatevFile.builder().metadata(metadata()).build();
        buffered.append(DatevRowSamples.requiredFieldsRow());
        assertEquals(buffered.toCsvString(), output.toString());
    }

    @Test
    void rejectsMetadataThatContradictsTheSchema() {
        assertThrows(IllegalArgumentException.class,
                () -> DatevFile.builder(DatevSchema.LEGACY_V12).metadata(metadata()));
        assertThrows(IllegalArgumentException.class,
                () -> DatevStreamWriter.builder(DatevSchema.LEGACY_V12).metadata(metadata()));
        assertThrows(IllegalArgumentException.class,
                () -> DatevFile.builder().metadata(legacyMetadata()));
        assertThrows(NullPointerException.class, () -> DatevFile.builder().metadata(null));
        assertThrows(NullPointerException.class, () -> DatevFile.builder(null));
    }

    @Test
    void writesALegacyV12ManagementRecordAlongsideTheLegacySchema() {
        DatevFile file = DatevFile.builder(DatevSchema.LEGACY_V12)
                .metadata(legacyMetadata())
                .build();
        file.append(DatevRowSamples.requiredFieldsRow());

        List<String> records = List.of(file.toCsvString().split("\r\n"));

        assertTrue(file.isCompleteExtf());
        assertEquals(12, file.metadata().orElseThrow().formatVersion());
        assertEquals(legacyMetadata().toCsvLine(), records.get(0));
        // Field 5 of the management record is the Buchungsstapel format version.
        assertEquals("12", records.get(0).split(";")[4]);
        assertEquals(124, records.get(1).split(";", -1).length);
    }

    @Test
    void theTwoVersionsProduceDifferentManagementRecords() {
        assertNotEquals(metadata(), legacyMetadata());
        assertEquals("13", metadata().toCsvLine().split(";")[4]);
        assertEquals("12", legacyMetadata().toCsvLine().split(";")[4]);
    }

    @Test
    void streamWriterAcceptsLegacyMetadata() {
        StringWriter output = new StringWriter();

        try (DatevStreamWriter writer = DatevStreamWriter.builder(DatevSchema.LEGACY_V12)
                .metadata(legacyMetadata())
                .build(output)) {
            writer.append(DatevRowSamples.requiredFieldsRow());
        }

        assertTrue(output.toString().startsWith(legacyMetadata().toCsvLine() + "\r\n"));
    }

    private static DatevMetadata legacyMetadata() {
        return DatevMetadata.bookingBatchV12()
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

    @Test
    void builderCombinesSchemaValidatorAndMetadata() {
        var seen = new java.util.ArrayList<Integer>();
        DatevMetadata metadata = metadata();
        DatevFile file = DatevFile.builder()
                .validator((version, row) -> seen.add(version))
                .metadata(metadata)
                .build();

        file.append(DatevRowSamples.requiredFieldsRow());

        assertEquals(List.of(13), seen);
        assertTrue(file.validator().isPresent());
        assertSame(metadata, file.metadata().orElseThrow());
        assertEquals(metadata, file.metadata().orElseThrow());
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
}
