package io.github.mrtyldr.datev.plain;

import io.github.mrtyldr.datev.core.DatevMetadata;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the write-through serialization contract of {@link DatevStreamWriter}.
 *
 * <p>Every completed record must reach the destination on the append that produced it. Large and
 * repeated rows must stay byte-for-byte compatible with the buffered exporter, while buffering and
 * destination ownership remain decisions of the caller.
 */
class DatevStreamWriterWriteThroughTest {

    private static final int LARGE_ROW_CHARS = 128 * 1024 + 17;

    @Test
    void manyRowsProduceTheBufferedExportersBytes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DatevFile buffered = DatevFile.withDefaults();

        try (DatevStreamWriter streaming = DatevStreamWriter.withDefaults(output)) {
            for (int row = 0; row < 500; row++) {
                Map<String, String> values = Map.of(
                        "Konto", String.valueOf(1000 + row),
                        "Buchungstext", "Müller € \"" + row + "\"; ä"
                );
                streaming.append(values);
                buffered.append(values);
            }
        }

        assertArrayEquals(buffered.toByteArray(), output.toByteArray());
    }

    @Test
    void largeRowsReachBothDestinationsImmediatelyAndKeepExactParity() {
        StringWriter characterOutput = new StringWriter();
        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        DatevFile buffered = DatevFile.withDefaults();
        String oversized = "x".repeat(LARGE_ROW_CHARS);

        try (DatevStreamWriter characterWriter = DatevStreamWriter.withDefaults(characterOutput);
             DatevStreamWriter byteWriter = DatevStreamWriter.withDefaults(byteOutput)) {
            for (Map<String, String> values : List.of(
                    Map.of("Konto", "1000"),
                    Map.of("Buchungstext", oversized),
                    Map.of("Konto", "2000"))) {
                characterWriter.append(values);
                byteWriter.append(values);
                buffered.append(values);

                assertEquals(expectedText(buffered), characterOutput.toString());
                assertArrayEquals(buffered.toByteArray(), byteOutput.toByteArray());
            }

            assertEquals(3, characterWriter.rowCount());
            assertEquals(3, byteWriter.rowCount());
        }
    }

    @Test
    void everyCharacterRecordIsOneWriteAndNothingIsHeldBetweenAppends() {
        CountingWriter output = new CountingWriter();
        DatevFile buffered = DatevFile.withDefaults();
        DatevStreamWriter streaming = DatevStreamWriter.withDefaults(output);

        assertEquals(1, output.writes());

        for (int row = 0; row < 100; row++) {
            Map<String, String> values = Map.of("Konto", String.valueOf(1000 + row));
            streaming.append(values);
            buffered.append(values);

            assertEquals(row + 2, output.writes());
            assertEquals(expectedText(buffered), output.delegate.toString());
        }

        streaming.close();
        assertEquals(101, output.writes());
        assertEquals(expectedText(buffered), output.delegate.toString());
    }

    @Test
    void wholeArrayOutputStreamGetsOneBulkCallPerRecordWithoutPerByteFallback() {
        WholeArrayOnlyOutputStream output = new WholeArrayOnlyOutputStream();
        DatevFile buffered = DatevFile.withDefaults();
        DatevStreamWriter streaming = DatevStreamWriter.withDefaults(output);

        assertEquals(1, output.bulkWrites);
        assertEquals(0, output.singleByteWrites);

        Map<String, String> values = Map.of(
                "Konto", "1000",
                "Buchungstext", "Müller €"
        );
        streaming.append(values);
        buffered.append(values);

        assertEquals(2, output.bulkWrites);
        assertEquals(0, output.singleByteWrites);
        assertArrayEquals(buffered.toByteArray(), output.delegate.toByteArray());

        streaming.close();
        assertEquals(2, output.bulkWrites);
        assertEquals(0, output.singleByteWrites);
    }

    @Test
    void callerOwnedBufferedOutputStreamBatchesRecordsUntilWriterClose() throws IOException {
        TrackingOutputStream destination = new TrackingOutputStream();
        BufferedOutputStream bufferedOutput = new BufferedOutputStream(destination, 64 * 1024);
        DatevStreamWriter streaming = DatevStreamWriter.withDefaults(bufferedOutput);

        streaming.append(Map.of("Konto", "1000"));
        streaming.append(Map.of("Konto", "2000"));

        assertEquals(0, destination.writes());
        assertEquals(0, destination.flushes);

        streaming.close();

        assertEquals(1, destination.writes());
        assertEquals(1, destination.flushes);
        assertFalse(destination.closed);
        assertTrue(destination.delegate.size() > 0);

        bufferedOutput.write('!');
        bufferedOutput.flush();
        assertEquals(2, destination.writes());
        assertEquals(2, destination.flushes);
        assertFalse(destination.closed);
    }

    @Test
    void windows1252MetadataAndRowsMatchTheBufferedExporterBytes() {
        DatevMetadata metadata = metadata("Müller €");
        DatevFile buffered = DatevFile.builder()
                .metadata(metadata)
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (DatevStreamWriter streaming = DatevStreamWriter.builder()
                .metadata(metadata)
                .build(output)) {
            Map<String, String> values = Map.of(
                    "Konto", "1000",
                    "Buchungstext", "Übertrag €"
            );
            streaming.append(values);
            buffered.append(values);
        }

        assertArrayEquals(buffered.toByteArray(), output.toByteArray());
    }

    private static DatevMetadata metadata(String applicationInformation) {
        return DatevMetadata.bookingBatchV13()
                .createdAt(LocalDateTime.of(2026, 8, 11, 9, 30))
                .origin("RE")
                .exportedBy("test")
                .advisorNumber(1001)
                .clientNumber(1)
                .fiscalYearStart(LocalDate.of(2026, 1, 1))
                .accountLength(4)
                .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .applicationInformation(applicationInformation)
                .build();
    }

    private static String expectedText(DatevFile buffered) {
        StringWriter expected = new StringWriter();
        buffered.writeTo(expected);
        return expected.toString();
    }

    /**
     * Deliberately overrides only the whole-array bulk overload. If production uses the ranged
     * overload, {@link OutputStream}'s default implementation falls back to {@link #write(int)} and
     * this test observes one call per byte.
     */
    private static final class WholeArrayOnlyOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private int bulkWrites;
        private int singleByteWrites;

        @Override
        public void write(int value) {
            singleByteWrites++;
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes) {
            bulkWrites++;
            delegate.write(bytes, 0, bytes.length);
        }
    }

    private static final class TrackingOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private int singleByteWrites;
        private int rangedWrites;
        private int flushes;
        private boolean closed;

        @Override
        public void write(int value) {
            singleByteWrites++;
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            rangedWrites++;
            delegate.write(bytes, offset, length);
        }

        @Override
        public void flush() {
            flushes++;
        }

        @Override
        public void close() {
            closed = true;
        }

        private int writes() {
            return singleByteWrites + rangedWrites;
        }
    }

    private static final class CountingWriter extends Writer {
        private final StringWriter delegate = new StringWriter();
        private int characterArrayWrites;
        private int stringWrites;

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            characterArrayWrites++;
            delegate.write(buffer, offset, length);
        }

        @Override
        public void write(String value) {
            stringWrites++;
            delegate.write(value);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        private int writes() {
            return characterArrayWrites + stringWrites;
        }
    }
}
