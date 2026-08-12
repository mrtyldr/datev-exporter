package io.github.mrtyldr.datev.plain;

import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevSchema;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevStreamWriterTest {

    @Test
    void writesTheSameV13BytesAsBufferedFileAcrossEveryAppendShape() {
        DatevFile buffered = DatevFile.withDefaults();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DatevStreamWriter streaming = DatevStreamWriter.withDefaults(output);

        String flat = "1,00;S" + ";".repeat(DatevSchema.CURRENT_V13.columnCount() - 2);
        String[] array = emptyRow(DatevSchema.CURRENT_V13);
        array[6] = "1000";
        List<String> collection = new ArrayList<>(Arrays.asList(emptyRow(DatevSchema.CURRENT_V13)));
        collection.set(13, "Invoice; \"42\"");
        Object[] objects = emptyRow(DatevSchema.CURRENT_V13);
        objects[2] = "EUR";

        buffered.append(flat);
        streaming.append(flat);
        buffered.append(array);
        streaming.append(array);
        buffered.append(collection);
        streaming.append(collection);
        buffered.appendValues(objects);
        streaming.appendValues(objects);
        buffered.append(Map.of("Buchungstext", "Müller €"));
        streaming.append(Map.of("Buchungstext", "Müller €"));
        buffered.append(DatevColumn.of("Belegfeld 1", "A-1"));
        streaming.append(DatevColumn.of("Belegfeld 1", "A-1"));

        Iterable<DatevColumn<?>> iterable = List.of(DatevColumn.of("Konto", "1200"));
        buffered.append(iterable);
        streaming.append(iterable);
        Collection<DatevColumn<?>> columns = List.of(
                DatevColumn.of("Soll/Haben-Kennzeichen", "H")
        );
        buffered.appendColumns(columns);
        streaming.appendColumns(columns);
        streaming.close();

        assertArrayEquals(buffered.toByteArray(), output.toByteArray());
        assertEquals(8, streaming.rowCount());
        assertFalse(streaming.isEmpty());
    }

    @Test
    void v12WriterUsesTheExactLegacyHeaderAndRows() {
        DatevFile buffered = DatevFile.legacyV12();
        StringWriter output = new StringWriter();
        DatevStreamWriter streaming = DatevStreamWriter.legacyV12(output);

        buffered.append(Map.of("Konto", "1000", "Buchungstext", "legacy"));
        streaming.append(Map.of("Konto", "1000", "Buchungstext", "legacy"));
        streaming.close();

        assertEquals(buffered.toCsvString(), output.toString());
        assertEquals(DatevSchema.LEGACY_V12, streaming.schema());
        assertEquals(DatevSchema.LEGACY_V12.headers(), streaming.headers());
        assertEquals(DatevFile.DEFAULT_CHARSET, streaming.charset());
    }

    @Test
    void factoryWritesOnlyTheHeaderUntilRowsAreAppended() {
        StringWriter output = new StringWriter();

        DatevStreamWriter streaming = DatevStreamWriter.withDefaults(output);

        assertEquals(
                String.join(";", DatevSchema.CURRENT_V13.headers()) + "\r\n",
                output.toString()
        );
        assertEquals(0, streaming.rowCount());
        assertTrue(streaming.isEmpty());
        streaming.close();
    }

    @Test
    void validatorSeesAnImmutableAlignedRowBeforeOutputAndRejectionIsRecoverable() {
        StringWriter output = new StringWriter();
        AtomicInteger calls = new AtomicInteger();
        List<String> headerOnly = new ArrayList<>();
        BiConsumer<Integer, List<String>> validator = (version, row) -> {
            assertEquals(13, version);
            assertEquals(DatevSchema.CURRENT_V13.columnCount(), row.size());
            assertThrows(UnsupportedOperationException.class, () -> row.set(0, "changed"));
            calls.incrementAndGet();
            if ("reject".equals(row.get(13))) {
                throw new IllegalArgumentException("rejected");
            }
        };
        DatevStreamWriter streaming = DatevStreamWriter.withDefaults(output, validator);
        headerOnly.add(output.toString());

        IllegalArgumentException rejection = assertThrows(
                IllegalArgumentException.class,
                () -> streaming.append(Map.of("Buchungstext", "reject"))
        );

        assertEquals("rejected", rejection.getMessage());
        assertEquals(headerOnly.get(0), output.toString());
        assertEquals(0, streaming.rowCount());
        assertSame(validator, streaming.validator().orElseThrow());

        streaming.append(Map.of("Buchungstext", "accept"));
        assertEquals(1, streaming.rowCount());
        assertEquals(2, calls.get());
        streaming.close();
    }

    @Test
    void structuralFormattingAndEncodingFailuresDoNotTouchOutput() {
        StringWriter output = new StringWriter();
        DatevStreamWriter streaming = DatevStreamWriter.withDefaults(output);
        String header = output.toString();

        assertUnchanged(streaming, output, header, () -> streaming.append(new String[]{"short"}));
        assertUnchanged(
                streaming,
                output,
                header,
                () -> streaming.append("\"unterminated" + ";".repeat(124))
        );
        assertUnchanged(
                streaming,
                output,
                header,
                () -> streaming.append(Map.of("not a heading", "x"))
        );
        assertUnchanged(
                streaming,
                output,
                header,
                () -> streaming.append(
                        DatevColumn.of("Konto", "1000"),
                        DatevColumn.of("Konto", "2000")
                )
        );
        assertUnchanged(
                streaming,
                output,
                header,
                () -> streaming.append(DatevColumn.formatted("Konto", 1, ignored -> {
                    throw new IllegalStateException("formatter failed");
                }))
        );
        assertUnchanged(
                streaming,
                output,
                header,
                () -> streaming.append(Map.of("Buchungstext", "line\nfeed"))
        );
        assertUnchanged(
                streaming,
                output,
                header,
                () -> streaming.append(Map.of("Buchungstext", "🙂"))
        );

        streaming.append(Map.of("Konto", "1000"));
        assertEquals(1, streaming.rowCount());
        streaming.close();
    }

    @Test
    void closeFlushesWithoutClosingCallerSinkAndIsIdempotent() throws IOException {
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();
        DatevStreamWriter streaming = DatevStreamWriter.withDefaults(output);
        streaming.append(Map.of("Konto", "1000"));

        streaming.close();
        streaming.close();
        output.write('!');

        assertTrue(output.flushed);
        assertFalse(output.closed);
        assertThrows(IllegalStateException.class, () -> streaming.append(Map.of()));
        assertThrows(IllegalStateException.class, streaming::flush);
        assertTrue(output.delegate.size() > 1);
    }

    @Test
    void rowIoFailurePoisonsWriterWithoutCountingThePartialRow() {
        FailsDuringSecondWrite output = new FailsDuringSecondWrite();
        DatevStreamWriter streaming = DatevStreamWriter.withDefaults(output);

        assertThrows(
                UncheckedIOException.class,
                () -> streaming.append(Map.of("Konto", "1000"))
        );

        int charactersAfterFailure = output.delegate.getBuffer().length();
        assertEquals(0, streaming.rowCount());
        assertThrows(IllegalStateException.class, () -> streaming.append(Map.of("Konto", "2000")));
        assertThrows(IllegalStateException.class, streaming::flush);
        assertEquals(charactersAfterFailure, output.delegate.getBuffer().length());

        streaming.close();
        assertEquals(charactersAfterFailure, output.delegate.getBuffer().length());
        assertEquals(0, output.flushes);
        assertFalse(output.closed);
    }

    @Test
    void headingAndFlushFailuresAreUncheckedAndTerminal() {
        Writer headingFailure = new AlwaysFailingWriter(false);
        assertThrows(
                UncheckedIOException.class,
                () -> DatevStreamWriter.withDefaults(headingFailure)
        );

        AlwaysFailingWriter flushFailure = new AlwaysFailingWriter(true);
        DatevStreamWriter streaming = DatevStreamWriter.withDefaults(flushFailure);
        assertThrows(UncheckedIOException.class, streaming::flush);
        assertThrows(IllegalStateException.class, () -> streaming.append(Map.of()));
        streaming.close();
    }

    @Test
    void reentrantAppendFromValidatorIsRejectedBeforeAnyRowIsWritten() {
        StringWriter output = new StringWriter();
        AtomicBoolean recurse = new AtomicBoolean(true);
        DatevStreamWriter[] reference = new DatevStreamWriter[1];
        BiConsumer<Integer, List<String>> validator = (version, row) -> {
            if (recurse.getAndSet(false)) {
                reference[0].append(Map.of("Konto", "2000"));
            }
        };
        reference[0] = DatevStreamWriter.withDefaults(output, validator);
        String header = output.toString();

        assertThrows(
                IllegalStateException.class,
                () -> reference[0].append(Map.of("Konto", "1000"))
        );
        assertEquals(header, output.toString());
        assertEquals(0, reference[0].rowCount());

        reference[0].append(Map.of("Konto", "3000"));
        assertEquals(1, reference[0].rowCount());
        reference[0].close();
    }

    @Test
    void enforcesMaximumRowsBeforeFormattingOrValidation() {
        AtomicInteger validations = new AtomicInteger();
        DatevStreamWriter streaming = DatevStreamWriter.withDefaults(
                Writer.nullWriter(),
                (version, row) -> validations.incrementAndGet()
        );

        for (int row = 0; row < DatevFile.MAX_DATA_ROWS; row++) {
            streaming.append(Map.of());
        }
        AtomicInteger formats = new AtomicInteger();

        assertThrows(
                IllegalStateException.class,
                () -> streaming.append(DatevColumn.formatted("Konto", 1, value -> {
                    formats.incrementAndGet();
                    return value.toString();
                }))
        );
        assertEquals(DatevFile.MAX_DATA_ROWS, streaming.rowCount());
        assertEquals(DatevFile.MAX_DATA_ROWS, validations.get());
        assertEquals(0, formats.get());
        streaming.close();
    }

    private static String[] emptyRow(DatevSchema schema) {
        return new String[schema.columnCount()];
    }

    private static void assertUnchanged(
            DatevStreamWriter streaming,
            StringWriter output,
            String expected,
            Runnable append
    ) {
        assertThrows(RuntimeException.class, append::run);
        assertEquals(expected, output.toString());
        assertEquals(0, streaming.rowCount());
    }

    private static final class CloseTrackingOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private boolean flushed;
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
        public void flush() {
            flushed = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FailsDuringSecondWrite extends Writer {
        private final StringWriter delegate = new StringWriter();
        private int writes;
        private int flushes;
        private boolean closed;

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            writes++;
            if (writes == 1) {
                delegate.write(buffer, offset, length);
                return;
            }
            int accepted = Math.min(5, length);
            delegate.write(buffer, offset, accepted);
            throw new IOException("partial row");
        }

        @Override
        public void flush() {
            flushes++;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class AlwaysFailingWriter extends Writer {
        private final boolean failOnFlushOnly;

        private AlwaysFailingWriter(boolean failOnFlushOnly) {
            this.failOnFlushOnly = failOnFlushOnly;
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            if (!failOnFlushOnly) {
                throw new IOException("write failed");
            }
        }

        @Override
        public void flush() throws IOException {
            if (failOnFlushOnly) {
                throw new IOException("flush failed");
            }
        }

        @Override
        public void close() {
        }
    }
}
