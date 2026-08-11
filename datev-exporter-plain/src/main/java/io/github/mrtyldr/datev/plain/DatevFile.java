package io.github.mrtyldr.datev.plain;

import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevCsv;
import io.github.mrtyldr.datev.core.DatevMetadata;
import io.github.mrtyldr.datev.core.DatevSchema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Builds and writes rows for one of the fixed DATEV Buchungsstapel schemas without dependencies.
 *
 * <p>The implementation uses a strict single-record CSV parser and a {@link StringBuilder}-based
 * serializer. It does not perform DATEV field-semantic validation unless a
 * {@link BiConsumer} validator is explicitly supplied. The validator receives the DATEV format
 * version and an immutable, aligned row without linking either side to an implementation-specific
 * validator type. Instances are mutable and not thread-safe. Use {@link DatevStreamWriter} when
 * booking rows should be written once without being retained.
 *
 * <p>Attach a {@link DatevMetadata} through {@link #builder()} to emit the mandatory EXTF
 * management record and produce a complete, importable Buchungsstapel file.
 */
public final class DatevFile implements Iterable<List<String>> {

    /** Separator required by DATEV CSV files. */
    public static final char DEFAULT_DELIMITER = ';';

    /** Record separator required by DATEV CSV files. */
    public static final String DEFAULT_LINE_SEPARATOR = "\r\n";

    /** Default encoding used by DATEV accounting imports. */
    public static final Charset DEFAULT_CHARSET = Charset.forName("windows-1252");

    /** Maximum number of booking records in a DATEV Buchungsstapel file. */
    public static final int MAX_DATA_ROWS = 99_999;

    private final DatevSchema schema;
    private final DatevRowAssembler rowAssembler;
    private final DatevMetadata metadata;
    private final List<List<String>> rows = new ArrayList<>();
    private boolean appending;

    private DatevFile(
            DatevSchema schema,
            BiConsumer<Integer, List<String>> validator,
            DatevMetadata metadata
    ) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.rowAssembler = new DatevRowAssembler(schema, validator);
        this.metadata = metadata;
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
     * Creates an unvalidated file using the current v13 schema.
     *
     * @return a new v13 file
     */
    public static DatevFile withDefaults() {
        return forSchema(DatevSchema.CURRENT_V13);
    }

    /**
     * Creates a current v13 file using an explicitly supplied validator.
     *
     * @param validator validator invoked for each append
     * @return a new validated v13 file
     */
    public static DatevFile withDefaults(BiConsumer<Integer, List<String>> validator) {
        return forSchema(DatevSchema.CURRENT_V13, validator);
    }

    /**
     * Creates an unvalidated file using the legacy v12 schema.
     *
     * @return a new v12 file
     */
    public static DatevFile legacyV12() {
        return forSchema(DatevSchema.LEGACY_V12);
    }

    /**
     * Creates a legacy v12 file using an explicitly supplied validator.
     *
     * @param validator validator invoked for each append
     * @return a new validated v12 file
     */
    public static DatevFile legacyV12(BiConsumer<Integer, List<String>> validator) {
        return forSchema(DatevSchema.LEGACY_V12, validator);
    }

    /**
     * Creates an unvalidated file using one of the fixed schemas.
     *
     * @param schema fixed schema to use
     * @return a new file
     */
    public static DatevFile forSchema(DatevSchema schema) {
        return new DatevFile(schema, null, null);
    }

    /**
     * Creates a file using one fixed schema and an explicitly supplied validator.
     *
     * @param schema fixed schema to use
     * @param validator validator invoked for each append
     * @return a new validated file
     */
    public static DatevFile forSchema(
            DatevSchema schema,
            BiConsumer<Integer, List<String>> validator
    ) {
        return new DatevFile(schema, Objects.requireNonNull(validator, "validator"), null);
    }

    /**
     * Returns the fixed schema selected for this file.
     *
     * @return the selected schema
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
     * Returns the output encoding.
     *
     * @return Windows-1252
     */
    public Charset charset() {
        return DEFAULT_CHARSET;
    }

    /**
     * Returns the explicitly configured validator, if any.
     *
     * @return the configured validator
     */
    public Optional<BiConsumer<Integer, List<String>>> validator() {
        return Optional.ofNullable(rowAssembler.validator());
    }

    /**
     * Returns the configured EXTF management record, if any.
     *
     * @return the configured metadata
     */
    public Optional<DatevMetadata> metadata() {
        return Optional.ofNullable(metadata);
    }

    /**
     * Returns whether output contains the mandatory EXTF management record.
     *
     * <p>Without metadata this file emits only the heading and booking records, which is not a
     * complete DATEV-Format file.
     *
     * @return {@code true} if metadata is configured
     */
    public boolean isCompleteExtf() {
        return metadata != null;
    }

    /**
     * Returns the number of appended data rows.
     *
     * @return row count
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * Returns whether no data rows have been appended.
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * Returns an immutable snapshot of immutable header-aligned rows.
     *
     * @return immutable row snapshot
     */
    public List<List<String>> rows() {
        return List.copyOf(rows);
    }

    /** Returns an iterator over an immutable snapshot of the current rows. */
    @Override
    public Iterator<List<String>> iterator() {
        return rows().iterator();
    }

    /**
     * Parses and appends exactly one strict semicolon-delimited CSV record.
     *
     * <p>Quoted values, doubled quote escaping and trailing empty cells are supported. Newlines,
     * malformed quotes and a number of cells different from the fixed schema are rejected.
     *
     * @param semicolonSeparatedRow exactly one complete CSV row
     */
    public void append(String semicolonSeparatedRow) {
        Objects.requireNonNull(semicolonSeparatedRow, "semicolonSeparatedRow");
        appendPrepared(() -> rowAssembler.fromCsv(semicolonSeparatedRow));
    }

    /**
     * Appends a complete positional row from a defensively copied array.
     *
     * @param orderedValues values in fixed heading order
     */
    public void append(String[] orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        appendPrepared(() -> rowAssembler.fromArray(orderedValues));
    }

    /**
     * Appends a complete positional row from a defensively copied collection.
     *
     * @param orderedValues values in fixed heading order
     */
    public void append(Collection<String> orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        appendPrepared(() -> rowAssembler.fromCollection(orderedValues));
    }

    /**
     * Appends a complete positional row, converting non-null values with
     * {@link String#valueOf(Object)}.
     *
     * @param orderedValues values in fixed heading order
     */
    public void appendValues(Object... orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        appendPrepared(() -> rowAssembler.fromValues(orderedValues));
    }

    /**
     * Appends a sparse row addressed by exact official heading names.
     *
     * <p>Missing headings become empty cells. Unknown and null headings are rejected.
     *
     * @param valuesByHeader values addressed by exact official headings
     */
    public void append(Map<String, ?> valuesByHeader) {
        Objects.requireNonNull(valuesByHeader, "valuesByHeader");
        appendPrepared(() -> rowAssembler.fromMap(valuesByHeader));
    }

    /**
     * Appends a sparse formatted row addressed by exact official heading names.
     *
     * @param columns columns to align by heading
     */
    public void append(DatevColumn<?>... columns) {
        Objects.requireNonNull(columns, "columns");
        appendPrepared(() -> rowAssembler.fromColumns(Arrays.asList(columns.clone())));
    }

    /**
     * Appends formatted columns from an iterable.
     *
     * <p>The different raw parameter type avoids erasure conflict with
     * {@link #append(Collection)}.
     *
     * @param columns columns to align by heading
     */
    public void append(Iterable<? extends DatevColumn<?>> columns) {
        Objects.requireNonNull(columns, "columns");
        appendPrepared(() -> {
            List<DatevColumn<?>> snapshot = new ArrayList<>();
            for (DatevColumn<?> column : columns) {
                snapshot.add(column);
            }
            return rowAssembler.fromColumns(snapshot);
        });
    }

    /**
     * Appends a sparse formatted row from a collection.
     *
     * <p>All headings are resolved before any formatter runs. Formatting and validation finish
     * before the row is committed.
     *
     * @param columns columns to align by heading
     */
    public void appendColumns(Collection<? extends DatevColumn<?>> columns) {
        Objects.requireNonNull(columns, "columns");
        appendPrepared(() -> rowAssembler.fromColumns(columns));
    }

    /**
     * Writes the header and all current rows without closing the caller-owned stream.
     *
     * @param output destination stream
     */
    public void writeTo(OutputStream output) {
        Objects.requireNonNull(output, "output");
        var encoder = DEFAULT_CHARSET.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        writeTo(new OutputStreamWriter(output, encoder));
    }

    /**
     * Alias for {@link #writeTo(OutputStream)}.
     *
     * @param output destination stream
     */
    public void write(OutputStream output) {
        writeTo(output);
    }

    /**
     * Writes the header and all current rows one record at a time without closing the caller-owned
     * writer. Appended rows remain retained by this file.
     *
     * @param output destination writer
     */
    public void writeTo(Writer output) {
        Objects.requireNonNull(output, "output");
        try {
            if (metadata != null) {
                output.write(metadata.toCsvLine());
                output.write(DatevCsv.LINE_SEPARATOR);
            }
            output.write(DatevCsv.encodeRecord(schema.headers(), DatevCsv.QUOTE_NONE));
            for (List<String> row : rows) {
                output.write(DatevCsv.encodeRecord(row, schema::isTextColumn));
            }
            output.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write DATEV CSV.", exception);
        }
    }

    /**
     * Alias for {@link #writeTo(Writer)}.
     *
     * @param output destination writer
     */
    public void write(Writer output) {
        writeTo(output);
    }

    /**
     * Returns the complete fixed-header CSV encoded as Windows-1252 bytes.
     *
     * @return encoded CSV
     */
    public byte[] toByteArray() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeTo(output);
        return output.toByteArray();
    }

    /**
     * Returns the complete fixed-header CSV as characters.
     *
     * @return CSV text
     */
    public String toCsvString() {
        StringBuilder csv = new StringBuilder(estimateOutputCapacity());
        if (metadata != null) {
            csv.append(metadata.toCsvLine()).append(DatevCsv.LINE_SEPARATOR);
        }
        DatevCsv.appendRecord(csv, schema.headers(), DatevCsv.QUOTE_NONE);
        for (List<String> row : rows) {
            DatevCsv.appendRecord(csv, row, schema::isTextColumn);
        }
        return csv.toString();
    }

    private void appendPrepared(RowSupplier supplier) {
        if (appending) {
            throw new IllegalStateException("Cannot append reentrantly while a row is being appended.");
        }
        ensureCanAppendRow(rows.size());
        appending = true;
        try {
            rows.add(supplier.get());
        } finally {
            appending = false;
        }
    }

    static void ensureCanAppendRow(int currentRowCount) {
        if (currentRowCount < 0) {
            throw new IllegalArgumentException("Current row count must not be negative.");
        }
        if (currentRowCount >= MAX_DATA_ROWS) {
            throw new IllegalStateException(
                    "A DATEV Buchungsstapel file can contain at most " + MAX_DATA_ROWS
                            + " booking rows."
            );
        }
    }

    private int estimateOutputCapacity() {
        return Math.max(256, schema.columnCount() * (rows.size() + 1) * 4);
    }

    /**
     * Configures a fixed-schema file, including the optional EXTF management record.
     *
     * <p>The static factories cover the common cases; this builder exists so schema, validator and
     * metadata can be combined without a factory per combination. Instances are not thread-safe.
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
         * <p>The metadata's format version must match this builder's schema, so a complete file
         * cannot silently declare a version it does not contain.
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
         * Creates the configured file.
         *
         * @return a new file
         */
        public DatevFile build() {
            return new DatevFile(schema, validator, metadata);
        }
    }

    @FunctionalInterface
    private interface RowSupplier {
        List<String> get();
    }
}
