package io.github.mrtyldr.datev.plain;

import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevCsv;
import io.github.mrtyldr.datev.core.DatevMetadata;
import io.github.mrtyldr.datev.core.DatevSchema;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Writes a fixed DATEV column-heading record and booking rows without retaining completed rows.
 *
 * <p>Attach a {@link DatevMetadata} through {@link #builder()} to emit the mandatory EXTF
 * management record before the heading and produce a complete, importable Buchungsstapel file.
 * Both records are written when the writer is constructed.
 *
 * <p>Each append is fully aligned, formatted, structurally checked, optionally validated and
 * serialized in row-local memory before output begins. Every completed record is then handed to
 * the destination immediately; this writer does not batch records or retain serialization buffers
 * between appends. Successfully written rows are discarded, so library-managed working memory is
 * proportional to one row instead of the total row count. Byte-stream output uses one temporary
 * encoded byte array per record. Callers decide whether the destination itself should buffer those
 * writes by supplying, for example, a {@code BufferedOutputStream}.
 * The supplied output remains caller-owned: during normal completion {@link #close()} flushes it
 * but does not close it. After a destination failure, close does not retry a flush. Byte-stream
 * factories emit Windows-1252 directly; callers supplying a character {@link Writer} remain
 * responsible for its eventual byte encoding.
 *
 * <p>Validation and formatting failures leave the output unchanged and the writer reusable. An I/O
 * failure can occur after a destination has accepted part of a record, so physical rollback cannot
 * be guaranteed; the writer becomes terminal after any destination failure. Instances are mutable,
 * forward-only and not thread-safe.
 */
public final class DatevStreamWriter implements AutoCloseable {

    private final DatevSchema schema;
    private final DatevRowAssembler rowAssembler;
    private final RowSink sink;
    private State state = State.OPEN;
    private Throwable failure;
    private int rowCount;

    private final DatevMetadata metadata;

    private DatevStreamWriter(
            DatevSchema schema,
            RowSink sink,
            BiConsumer<Integer, List<String>> validator,
            DatevMetadata metadata
    ) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.rowAssembler = new DatevRowAssembler(schema, validator);
        this.metadata = metadata;
        writeHeader();
    }

    /**
     * Creates a builder for the current v13 schema.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder(DatevSchema.current());
    }

    /**
     * Creates a builder for an explicit fixed schema.
     *
     * @param schema the fixed schema to use
     * @return a new builder
     */
    public static Builder builder(DatevSchema schema) {
        return new Builder(Objects.requireNonNull(schema, "schema"));
    }

    /**
     * Starts an unvalidated current-v13 writer on a caller-owned byte stream.
     *
     * @param output destination receiving strict Windows-1252 bytes
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter withDefaults(OutputStream output) {
        return forSchema(DatevSchema.CURRENT_V13, output);
    }

    /**
     * Starts a validated current-v13 writer on a caller-owned byte stream.
     *
     * @param output destination receiving strict Windows-1252 bytes
     * @param validator validator invoked before each row write
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter withDefaults(
            OutputStream output,
            BiConsumer<Integer, List<String>> validator
    ) {
        return forSchema(DatevSchema.CURRENT_V13, output, validator);
    }

    /**
     * Starts an unvalidated current-v13 writer on a caller-owned character writer.
     *
     * @param output destination character writer
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter withDefaults(Writer output) {
        return forSchema(DatevSchema.CURRENT_V13, output);
    }

    /**
     * Starts a validated current-v13 writer on a caller-owned character writer.
     *
     * @param output destination character writer
     * @param validator validator invoked before each row write
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter withDefaults(
            Writer output,
            BiConsumer<Integer, List<String>> validator
    ) {
        return forSchema(DatevSchema.CURRENT_V13, output, validator);
    }

    /**
     * Starts an unvalidated legacy-v12 writer on a caller-owned byte stream.
     *
     * @param output destination receiving strict Windows-1252 bytes
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter legacyV12(OutputStream output) {
        return forSchema(DatevSchema.LEGACY_V12, output);
    }

    /**
     * Starts a validated legacy-v12 writer on a caller-owned byte stream.
     *
     * @param output destination receiving strict Windows-1252 bytes
     * @param validator validator invoked before each row write
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter legacyV12(
            OutputStream output,
            BiConsumer<Integer, List<String>> validator
    ) {
        return forSchema(DatevSchema.LEGACY_V12, output, validator);
    }

    /**
     * Starts an unvalidated legacy-v12 writer on a caller-owned character writer.
     *
     * @param output destination character writer
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter legacyV12(Writer output) {
        return forSchema(DatevSchema.LEGACY_V12, output);
    }

    /**
     * Starts a validated legacy-v12 writer on a caller-owned character writer.
     *
     * @param output destination character writer
     * @param validator validator invoked before each row write
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter legacyV12(
            Writer output,
            BiConsumer<Integer, List<String>> validator
    ) {
        return forSchema(DatevSchema.LEGACY_V12, output, validator);
    }

    /**
     * Starts an unvalidated writer for one fixed schema on a caller-owned byte stream.
     *
     * @param schema fixed DATEV schema
     * @param output destination receiving strict Windows-1252 bytes
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter forSchema(DatevSchema schema, OutputStream output) {
        return new DatevStreamWriter(schema, byteSink(output), null, null);
    }

    /**
     * Starts a validated writer for one fixed schema on a caller-owned byte stream.
     *
     * @param schema fixed DATEV schema
     * @param output destination receiving strict Windows-1252 bytes
     * @param validator validator invoked before each row write
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter forSchema(
            DatevSchema schema,
            OutputStream output,
            BiConsumer<Integer, List<String>> validator
    ) {
        return new DatevStreamWriter(schema,
                byteSink(output),
                Objects.requireNonNull(validator, "validator"), null);
    }

    /**
     * Starts an unvalidated writer for one fixed schema on a caller-owned character writer.
     *
     * @param schema fixed DATEV schema
     * @param output destination character writer
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter forSchema(DatevSchema schema, Writer output) {
        return new DatevStreamWriter(schema, characterSink(output), null, null);
    }

    /**
     * Starts a validated writer for one fixed schema on a caller-owned character writer.
     *
     * @param schema fixed DATEV schema
     * @param output destination character writer
     * @param validator validator invoked before each row write
     * @return a new forward-only writer whose column heading has been written
     */
    public static DatevStreamWriter forSchema(
            DatevSchema schema,
            Writer output,
            BiConsumer<Integer, List<String>> validator
    ) {
        return new DatevStreamWriter(schema,
                characterSink(output),
                Objects.requireNonNull(validator, "validator"), null);
    }

    /**
     * Returns the fixed schema selected for this output.
     *
     * @return selected v12 or v13 schema
     */
    public DatevSchema schema() {
        return schema;
    }

    /**
     * Returns the immutable official headings in output order.
     *
     * @return fixed ordered headings
     */
    public List<String> headers() {
        return schema.headers();
    }

    /**
     * Returns the byte-output charset.
     *
     * @return Windows-1252
     */
    public Charset charset() {
        return DatevFile.DEFAULT_CHARSET;
    }

    /**
     * Returns the configured optional validator.
     *
     * @return configured format-version/row consumer, if any
     */
    public Optional<BiConsumer<Integer, List<String>>> validator() {
        return Optional.ofNullable(rowAssembler.validator());
    }

    /**
     * Returns the EXTF management record written before the heading, if any.
     *
     * @return the configured metadata
     */
    public Optional<DatevMetadata> metadata() {
        return Optional.ofNullable(metadata);
    }

    /**
     * Returns whether the output contains the mandatory EXTF management record.
     *
     * @return {@code true} if metadata was configured
     */
    public boolean isCompleteExtf() {
        return metadata != null;
    }

    /**
     * Returns the number of booking rows successfully handed to the destination.
     *
     * @return committed row count, excluding the heading
     */
    public int rowCount() {
        return rowCount;
    }

    /**
     * Returns whether no booking row has been successfully written.
     *
     * @return {@code true} until the first successful row write
     */
    public boolean isEmpty() {
        return rowCount == 0;
    }

    /**
     * Parses and writes exactly one strict semicolon-delimited CSV record.
     *
     * @param semicolonSeparatedRow exactly one complete CSV row
     */
    public void append(String semicolonSeparatedRow) {
        appendPrepared(() -> rowAssembler.fromCsv(semicolonSeparatedRow));
    }

    /**
     * Writes a complete positional row from a defensively copied array.
     *
     * @param orderedValues values in fixed heading order
     */
    public void append(String[] orderedValues) {
        appendPrepared(() -> rowAssembler.fromArray(orderedValues));
    }

    /**
     * Writes a complete positional row from a defensively copied collection.
     *
     * @param orderedValues values in fixed heading order
     */
    public void append(Collection<String> orderedValues) {
        appendPrepared(() -> rowAssembler.fromCollection(orderedValues));
    }

    /**
     * Writes a complete positional row after converting non-null values with
     * {@link String#valueOf(Object)}.
     *
     * @param orderedValues values in fixed heading order
     */
    public void appendValues(Object... orderedValues) {
        appendPrepared(() -> rowAssembler.fromValues(orderedValues));
    }

    /**
     * Writes a sparse row addressed by exact official heading names.
     *
     * @param valuesByHeader values addressed by exact official headings
     */
    public void append(Map<String, ?> valuesByHeader) {
        appendPrepared(() -> rowAssembler.fromMap(valuesByHeader));
    }

    /**
     * Writes a sparse formatted row addressed by exact official heading names.
     *
     * @param columns columns to align by heading
     */
    public void append(DatevColumn<?>... columns) {
        appendPrepared(() -> {
            Objects.requireNonNull(columns, "columns");
            return rowAssembler.fromColumns(Arrays.asList(columns.clone()));
        });
    }

    /**
     * Writes formatted columns from an iterable.
     *
     * @param columns columns to align by heading
     */
    public void append(Iterable<? extends DatevColumn<?>> columns) {
        appendPrepared(() -> {
            Objects.requireNonNull(columns, "columns");
            List<DatevColumn<?>> snapshot = new ArrayList<>();
            for (DatevColumn<?> column : columns) {
                snapshot.add(column);
            }
            return rowAssembler.fromColumns(snapshot);
        });
    }

    /**
     * Writes a sparse formatted row from a collection.
     *
     * @param columns columns to align by heading
     */
    public void appendColumns(Collection<? extends DatevColumn<?>> columns) {
        appendPrepared(() -> rowAssembler.fromColumns(columns));
    }

    /**
     * Flushes completed records without closing the caller-owned destination.
     *
     * @throws UncheckedIOException if the destination rejects the flush
     */
    public void flush() {
        ensureOpen("flush");
        try {
            sink.flush();
        } catch (IOException exception) {
            UncheckedIOException wrapped = new UncheckedIOException(
                    "Could not flush DATEV CSV output.",
                    exception
            );
            fail(wrapped);
            throw wrapped;
        } catch (RuntimeException | Error exception) {
            fail(exception);
            throw exception;
        }
    }

    /**
     * Flushes completed records and makes this writer terminal without closing the caller-owned
     * destination.
     *
     * <p>Repeated calls have no effect. If a previous destination operation failed, this method
     * does not retry flushing potentially partial output, and it does not close the destination
     * either.
     *
     * @throws UncheckedIOException if the destination rejects the flush
     */
    @Override
    public void close() {
        if (state == State.CLOSED || state == State.FAILED) {
            return;
        }
        ensureOpen("close");
        flush();
        state = State.CLOSED;
    }

    private void appendPrepared(RowSupplier supplier) {
        ensureOpen("append");
        DatevFile.ensureCanAppendRow(rowCount);
        state = State.APPENDING;
        boolean sinkTouched = false;
        try {
            List<String> row = supplier.get();
            String record = DatevCsv.encodeRecord(row, schema::isTextColumn);
            sinkTouched = true;
            sink.write(record);
            rowCount++;
            state = State.OPEN;
        } catch (IOException exception) {
            UncheckedIOException wrapped = new UncheckedIOException(
                    "Could not write DATEV CSV row.",
                    exception
            );
            fail(wrapped);
            throw wrapped;
        } catch (RuntimeException | Error exception) {
            if (sinkTouched) {
                fail(exception);
            } else {
                state = State.OPEN;
            }
            throw exception;
        }
    }

    private void writeHeader() {
        try {
            if (metadata != null) {
                sink.write(metadata.toCsvLine() + DatevCsv.LINE_SEPARATOR);
            }
            sink.write(DatevCsv.encodeRecord(schema.headers(), DatevCsv.QUOTE_NONE));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write DATEV CSV heading.", exception);
        }
    }

    /**
     * Configures a streaming writer, including the optional EXTF management record.
     *
     * <p>The static factories cover the common cases; this builder exists so schema, validator and
     * metadata can be combined without a factory per combination. Nothing is written until one of
     * the {@code build} methods is called. Instances are not thread-safe.
     */
    public static final class Builder {
        private final DatevSchema schema;
        private BiConsumer<Integer, List<String>> validator;
        private DatevMetadata metadata;

        private Builder(DatevSchema schema) {
            this.schema = schema;
        }

        /**
         * Sets the optional row validator.
         *
         * @param validator receives the format version and each immutable aligned row
         * @return this builder
         */
        public Builder validator(BiConsumer<Integer, List<String>> validator) {
            this.validator = Objects.requireNonNull(validator, "validator");
            return this;
        }

        /**
         * Sets the EXTF management record written before the heading.
         *
         * @param metadata the management record
         * @return this builder
         * @throws IllegalArgumentException if the metadata's format version differs from the schema
         */
        public Builder metadata(DatevMetadata metadata) {
            Objects.requireNonNull(metadata, "metadata");
            if (metadata.formatVersion() != schema.formatVersion()) {
                throw new IllegalArgumentException("DATEV metadata declares format version "
                        + metadata.formatVersion() + " but the schema is version "
                        + schema.formatVersion() + '.');
            }
            this.metadata = metadata;
            return this;
        }

        /**
         * Starts a writer on a caller-owned byte stream, emitting Windows-1252 directly.
         *
         * @param output the caller-owned destination stream
         * @return a new writer that has already written its leading records
         */
        public DatevStreamWriter build(OutputStream output) {
            return new DatevStreamWriter(schema, byteSink(output), validator, metadata);
        }

        /**
         * Starts a writer on a caller-owned character writer.
         *
         * @param output the caller-owned destination writer
         * @return a new writer that has already written its leading records
         */
        public DatevStreamWriter build(Writer output) {
            return new DatevStreamWriter(schema, characterSink(output), validator, metadata);
        }
    }

    private void ensureOpen(String operation) {
        if (state == State.OPEN) {
            return;
        }
        String message = switch (state) {
            case APPENDING -> "Cannot " + operation + " reentrantly while a row is being appended.";
            case FAILED -> "Cannot " + operation + " after a DATEV output failure.";
            case CLOSED -> "Cannot " + operation + " after the DATEV stream writer was closed.";
            case OPEN -> throw new AssertionError("Unexpected open state.");
        };
        IllegalStateException exception = new IllegalStateException(message);
        if (failure != null) {
            exception.initCause(failure);
        }
        throw exception;
    }

    private void fail(Throwable cause) {
        failure = cause;
        state = State.FAILED;
    }

    private static RowSink byteSink(OutputStream output) {
        OutputStream destination = Objects.requireNonNull(output, "output");
        return new RowSink() {
            @Override
            public void write(String value) throws IOException {
                destination.write(value.getBytes(DatevFile.DEFAULT_CHARSET));
            }

            @Override
            public void flush() throws IOException {
                destination.flush();
            }
        };
    }

    private static RowSink characterSink(Writer output) {
        Writer destination = Objects.requireNonNull(output, "output");
        return new RowSink() {
            @Override
            public void write(String value) throws IOException {
                destination.write(value);
            }

            @Override
            public void flush() throws IOException {
                destination.flush();
            }
        };
    }

    private enum State {
        OPEN,
        APPENDING,
        FAILED,
        CLOSED
    }

    @FunctionalInterface
    private interface RowSupplier {
        List<String> get();
    }

    private interface RowSink {
        void write(String value) throws IOException;

        void flush() throws IOException;
    }
}
