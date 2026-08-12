package io.github.mrtyldr.datev.advanced;

import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevCsv;
import io.github.mrtyldr.datev.core.DatevHeader;
import io.github.mrtyldr.datev.core.DatevMetadata;
import io.github.mrtyldr.datev.core.DatevRowValidator;
import io.github.mrtyldr.datev.core.DatevValidationMode;

import java.io.ByteArrayOutputStream;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds header-aligned DATEV CSV rows and writes a complete Buchungsstapel file.
 *
 * <p>A file's header is immutable. Appended rows are validated and converted atomically into that
 * header's order. The class itself is an iterable of rows, so any row consumer can read it
 * directly.
 *
 * <p>Serialization goes through {@link DatevCsv}, so this module has no third-party dependencies.
 * For Univocity interoperability add {@code datev-exporter-advanced-univocity}.
 *
 * <p>Instances are mutable and are not thread-safe.
 */
public final class DatevFile implements Iterable<List<String>> {

    /** The separator required by the DATEV CSV format. */
    public static final char DEFAULT_DELIMITER = ';';

    /** The record separator required by the DATEV CSV format. */
    public static final String DEFAULT_LINE_SEPARATOR = "\r\n";

    /** The default character set accepted by DATEV accounting imports. */
    public static final Charset DEFAULT_CHARSET = Charset.forName("windows-1252");

    /** Maximum number of booking records in one DATEV Buchungsstapel file. */
    public static final int MAX_DATA_ROWS = 99_999;

    private final DatevHeader header;
    private final Charset charset;
    private final DatevMetadata metadata;
    private final DatevValidationMode validationMode;
    private final List<List<String>> rows = new ArrayList<>();

    private DatevFile(
            DatevHeader header,
            Charset charset,
            DatevMetadata metadata,
            DatevValidationMode validationMode
    ) {
        this.header = Objects.requireNonNull(header, "header");
        this.charset = Objects.requireNonNull(charset, "charset");
        this.metadata = metadata;
        this.validationMode = Objects.requireNonNull(validationMode, "validationMode");
    }

    /**
     * Creates a file with the current default DATEV Buchungsstapel header and writer settings.
     *
     * @return a new file using the default header and settings
     */
    public static DatevFile withDefaults() {
        return builder().build();
    }

    /**
     * Creates a complete Buchungsstapel v13 file with a typed EXTF management record.
     *
     * @param metadata the management-record metadata
     * @return a new strictly validated v13 file
     */
    public static DatevFile withDefaults(DatevMetadata metadata) {
        return builder().metadata(metadata).build();
    }

    /**
     * Creates a file with a semicolon-separated custom header.
     *
     * @param semicolonSeparatedHeader the complete header row separated by semicolons
     * @return a new file using the supplied header
     */
    public static DatevFile withHeader(String semicolonSeparatedHeader) {
        return withHeader(DatevHeader.parse(semicolonSeparatedHeader));
    }

    /**
     * Creates a file with a custom header. The input array is defensively copied.
     *
     * @param header the output header names in column order
     * @return a new file using the supplied header
     */
    public static DatevFile withHeader(String[] header) {
        return withHeader(DatevHeader.of(header));
    }

    /**
     * Creates a file with a custom header. The input list is defensively copied.
     *
     * @param header the output header names in column order
     * @return a new file using the supplied header
     */
    public static DatevFile withHeader(List<String> header) {
        return withHeader(DatevHeader.of(header));
    }

    /**
     * Creates a file with an already configured immutable header.
     *
     * @param header the immutable header configuration to use
     * @return a new file using the supplied header
     */
    public static DatevFile withHeader(DatevHeader header) {
        return builder(header).build();
    }

    /**
     * Starts a builder with the current default DATEV Buchungsstapel header.
     *
     * @return a new builder using the default header
     */
    public static Builder builder() {
        return new Builder(DatevHeader.current());
    }

    /**
     * Starts a builder with a custom or preconfigured header.
     *
     * @param header the initial immutable header configuration
     * @return a new builder using the supplied header
     */
    public static Builder builder(DatevHeader header) {
        return new Builder(header);
    }

    /**
     * Returns this file's immutable header definition.
     *
     * @return this file's header definition
     */
    public DatevHeader header() {
        return header;
    }

    /**
     * Returns the output header names in their configured order.
     *
     * @return the ordered, immutable output header names
     */
    public List<String> headers() {
        return header.names();
    }

    /**
     * Returns the charset used by {@link #writeTo(OutputStream)}.
     *
     * @return the output charset
     */
    public Charset charset() {
        return charset;
    }

    /**
     * Returns the optional EXTF management-record metadata.
     *
     * @return metadata for a complete EXTF file, or an empty optional for a data-only file
     */
    public Optional<DatevMetadata> metadata() {
        return Optional.ofNullable(metadata);
    }

    /**
     * Returns the configured semantic validation mode.
     *
     * @return the validation mode applied before a row is committed
     */
    public DatevValidationMode validationMode() {
        return validationMode;
    }

    /**
     * Returns whether convenience output contains a complete EXTF management record.
     *
     * @return {@code true} when metadata is configured
     */
    public boolean isCompleteExtf() {
        return metadata != null;
    }

    /**
     * Returns the number of appended rows.
     *
     * @return the current row count
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * Returns whether no data rows have been appended.
     *
     * @return {@code true} when this file contains no data rows
     */
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * Returns an immutable snapshot of all rows. Each row is immutable and header-aligned.
     *
     * @return an immutable snapshot of the current rows
     */
    public List<List<String>> rows() {
        return List.copyOf(rows);
    }

    /**
     * Returns an iterator over an immutable snapshot of the current rows.
     */
    @Override
    public Iterator<List<String>> iterator() {
        return rows().iterator();
    }







    /**
     * Appends a semicolon-delimited CSV row.
     *
     * <p>CSV quoting is parsed, so {@code "a;b";c} represents two values. The parsed row must have
     * exactly the same number of values as the configured header.
     *
     * @param semicolonSeparatedRow the complete semicolon-delimited CSV row
     */
    public void append(String semicolonSeparatedRow) {
        Objects.requireNonNull(semicolonSeparatedRow, "semicolonSeparatedRow");
        DatevCsv.rejectControlCharacters(semicolonSeparatedRow, "CSV row");
        validateCsvRowSyntax(semicolonSeparatedRow);
        append(DatevCsv.parseRecord(semicolonSeparatedRow).toArray(String[]::new));

    }

    /**
     * Appends a complete positional row from an array.
     *
     * @param orderedValues the values in configured header order
     */
    public void append(String[] orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        appendOrdered(Arrays.asList(orderedValues.clone()));
    }

    /**
     * Appends a complete positional row from a collection.
     *
     * @param orderedValues the values in configured header order
     */
    public void append(Collection<String> orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        appendOrdered(new ArrayList<>(orderedValues));
    }

    /**
     * Appends a complete positional row containing arbitrary values converted with
     * {@link String#valueOf(Object)}. Nulls produce empty cells.
     *
     * @param orderedValues the values in configured header order
     */
    public void appendValues(Object... orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        validateRowWidth(orderedValues.length);
        List<String> converted = new ArrayList<>(orderedValues.length);
        for (Object value : orderedValues) {
            converted.add(value == null ? null : String.valueOf(value));
        }
        appendOrdered(converted);
    }

    /**
     * Appends a sparse row addressed by canonical header keys or configured output names.
     * Missing columns are written as empty cells; unknown columns are rejected.
     *
     * @param valuesByHeader values keyed by canonical header key or configured output name
     */
    public void append(Map<String, ?> valuesByHeader) {
        Objects.requireNonNull(valuesByHeader, "valuesByHeader");
        List<String> identifiers = new ArrayList<>(valuesByHeader.size());
        List<Object> values = new ArrayList<>(valuesByHeader.size());
        for (Map.Entry<String, ?> entry : valuesByHeader.entrySet()) {
            identifiers.add(entry.getKey());
            values.add(entry.getValue());
        }

        String[] row = new String[header.size()];
        Set<Integer> assignedIndexes = new HashSet<>();
        int[] resolvedIndexes = new int[identifiers.size()];

        for (int position = 0; position < identifiers.size(); position++) {
            String identifier = identifiers.get(position);
            int index = header.indexOf(identifier);
            ensureUnassigned(assignedIndexes, index, identifier);
            resolvedIndexes[position] = index;
        }
        for (int position = 0; position < identifiers.size(); position++) {
            Object value = values.get(position);
            row[resolvedIndexes[position]] = validateCell(
                    value == null ? null : String.valueOf(value),
                    identifiers.get(position)
            );
        }

        addValidatedRow(row);
    }

    /**
     * Appends a sparse formatted row.
     *
     * <p>This varargs overload complements {@link #append(Collection)} because Java erases
     * {@code Collection<String>} and {@code Collection<DatevColumn<?>>} to the same signature.
     *
     * @param columns the formatted columns to place by header identifier
     */
    public void append(DatevColumn<?>... columns) {
        Objects.requireNonNull(columns, "columns");
        appendColumns(Arrays.asList(columns.clone()));
    }

    /**
     * Appends a sparse formatted row from an iterable of columns.
     *
     * <p>The {@code Iterable} parameter deliberately differs from the positional
     * {@code Collection<String>} overload after Java generic type erasure. A
     * {@code Collection<DatevColumn<?>>} can therefore still be passed directly to
     * {@code append(...)} with compile-time type safety.
     *
     * @param columns the formatted columns to place by header identifier
     */
    public void append(Iterable<? extends DatevColumn<?>> columns) {
        Objects.requireNonNull(columns, "columns");
        List<DatevColumn<?>> snapshot = new ArrayList<>();
        for (DatevColumn<?> column : columns) {
            snapshot.add(column);
        }
        appendColumns(snapshot);
    }

    /**
     * Appends a sparse formatted row from a collection of columns.
     *
     * <p>Formatting and validation complete before the row is added, so a failure never leaves a
     * partial row behind.
     *
     * @param columns the formatted columns to place by header identifier
     */
    public void appendColumns(Collection<? extends DatevColumn<?>> columns) {
        Objects.requireNonNull(columns, "columns");
        ensureCanAppendRow(header, rows.size());
        List<? extends DatevColumn<?>> snapshot = new ArrayList<>(columns);
        String[] row = new String[header.size()];
        Set<Integer> assignedIndexes = new HashSet<>();
        int[] resolvedIndexes = new int[snapshot.size()];

        for (int position = 0; position < snapshot.size(); position++) {
            DatevColumn<?> column = snapshot.get(position);
            Objects.requireNonNull(column, "columns must not contain null");
            int index = header.indexOf(column.header());
            ensureUnassigned(assignedIndexes, index, column.header());
            resolvedIndexes[position] = index;
        }
        for (int position = 0; position < snapshot.size(); position++) {
            DatevColumn<?> column = snapshot.get(position);
            row[resolvedIndexes[position]] = validateCell(column.formattedValue(), column.header());
        }

        addValidatedRow(row);
    }

    /**
     * Writes the header and all current rows to an output stream without closing it.
     *
     * @param output the caller-owned destination stream
     */
    public void writeTo(OutputStream output) {
        Objects.requireNonNull(output, "output");
        var encoder = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        writeTo(new NonClosingWriter(new OutputStreamWriter(output, encoder)));
    }

    /**
     * Alias for {@link #writeTo(OutputStream)}.
     *
     * @param output the caller-owned destination stream
     */
    public void write(OutputStream output) {
        writeTo(output);
    }

    /**
     * Writes the header and all current rows to a character writer without closing it.
     *
     * @param output the caller-owned destination writer
     */
    public void writeTo(Writer output) {
        Objects.requireNonNull(output, "output");
        NonClosingWriter destination = new NonClosingWriter(output);
        try {
            if (metadata != null) {
                destination.write(metadata.toCsvLine());
                destination.write(DEFAULT_LINE_SEPARATOR);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write DATEV metadata.", exception);
        }

        StringBuilder csv = new StringBuilder();
        DatevCsv.appendRecord(csv, header.names(), DatevCsv.QUOTE_NONE);
        for (List<String> row : rows) {
            DatevCsv.appendRecord(csv, row, header::isQuotedColumn);
        }
        try {
            destination.write(csv.toString());
            destination.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write DATEV rows.", exception);
        }
    }



    /**
     * Returns the complete CSV as bytes in {@link #charset()}.
     *
     * @return the complete CSV encoded with this file's charset
     */
    public byte[] toByteArray() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeTo(output);
        return output.toByteArray();
    }

    /**
     * Returns the complete CSV as characters, primarily for inspection and testing.
     *
     * @return the complete CSV text
     */
    public String toCsvString() {
        StringWriter output = new StringWriter();
        writeTo(output);
        return output.toString();
    }

    private void appendOrdered(List<String> orderedValues) {
        validateRowWidth(orderedValues.size());

        String[] row = new String[orderedValues.size()];
        for (int index = 0; index < orderedValues.size(); index++) {
            row[index] = validateCell(orderedValues.get(index), header.keys().get(index));
        }
        addValidatedRow(row);
    }

    private void addValidatedRow(String[] row) {
        List<String> immutable = immutableRow(row);
        DatevRowValidator.validateOrThrow(
                header,
                immutable,
                validationMode,
                metadata
        );
        ensureCanAppendRow(header, rows.size());
        rows.add(immutable);
    }

    static void ensureCanAppendRow(DatevHeader header, int currentRowCount) {
        Objects.requireNonNull(header, "header");
        if (currentRowCount < 0) {
            throw new IllegalArgumentException("Current row count must not be negative.");
        }
        if (header.bookingBatchVersion().isPresent() && currentRowCount >= MAX_DATA_ROWS) {
            throw new IllegalStateException(
                    "A DATEV Buchungsstapel file can contain at most " + MAX_DATA_ROWS
                            + " booking rows."
            );
        }
    }

    private void validateRowWidth(int valueCount) {
        if (valueCount != header.size()) {
            throw new IllegalArgumentException(
                    "Row contains " + valueCount + " values but header contains " + header.size() + "."
            );
        }
    }

    private static List<String> immutableRow(String[] row) {
        List<String> copy = new ArrayList<>(row.length);
        Collections.addAll(copy, row);
        return Collections.unmodifiableList(copy);
    }

    private static void ensureUnassigned(Set<Integer> assigned, int index, String identifier) {
        if (!assigned.add(index)) {
            throw new IllegalArgumentException(
                    "Multiple values were supplied for DATEV header '" + identifier + "'."
            );
        }
    }

    private static String validateCell(String value, String headerIdentifier) {
        if (value != null) {
            // The advanced exporter checks record structure only; unlike the plain exporter it
            // leaves Windows-1252 encodability to the configured CodingErrorAction, so this stays
            // on rejectControlCharacters rather than moving to requireExportable.
            DatevCsv.rejectControlCharacters(value,
                    "value for DATEV header '" + headerIdentifier + "'");
        }
        return value;
    }

    private static void validateCsvRowSyntax(String row) {
        boolean fieldStart = true;
        boolean quoted = false;
        boolean quoteClosed = false;

        for (int index = 0; index < row.length(); index++) {
            char character = row.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < row.length() && row.charAt(index + 1) == '"') {
                        index++;
                    } else {
                        quoted = false;
                        quoteClosed = true;
                    }
                }
                continue;
            }

            if (quoteClosed) {
                if (character != DEFAULT_DELIMITER) {
                    throw malformedCsvRow("Only a delimiter may follow a closing quote", index);
                }
                fieldStart = true;
                quoteClosed = false;
                continue;
            }

            if (character == DEFAULT_DELIMITER) {
                fieldStart = true;
            } else if (character == '"') {
                if (!fieldStart) {
                    throw malformedCsvRow("A quoted value must start at the beginning of a field", index);
                }
                quoted = true;
                fieldStart = false;
            } else {
                fieldStart = false;
            }
        }

        if (quoted) {
            throw malformedCsvRow("Quoted value is not closed", row.length());
        }
    }

    private static IllegalArgumentException malformedCsvRow(String reason, int index) {
        return new IllegalArgumentException("Malformed CSV row at character " + index + ": " + reason + '.');
    }

    private static final class NonClosingWriter extends FilterWriter {
        private NonClosingWriter(Writer delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    /** Builds a file with immutable header configuration. */
    public static final class Builder {
        private DatevHeader header;
        private Charset charset = DEFAULT_CHARSET;
        private DatevMetadata metadata;
        private DatevValidationMode validationMode;

        private Builder(DatevHeader header) {
            this.header = Objects.requireNonNull(header, "header");
            this.validationMode = header.bookingBatchVersion().isEmpty()
                    ? DatevValidationMode.NONE
                    : DatevValidationMode.STRICT;
        }

        /**
         * Renames one header while retaining its stable canonical key and position.
         *
         * @param canonicalKey the canonical key of the header to rename
         * @param outputName the replacement output name
         * @return this builder
         */
        public Builder renameHeader(String canonicalKey, String outputName) {
            header = header.renamed(canonicalKey, outputName);
            return this;
        }

        /**
         * Renames selected headers while retaining their stable canonical keys and positions.
         *
         * @param namesByCanonicalKey replacement output names keyed by canonical header key
         * @return this builder
         */
        public Builder renameHeaders(Map<String, String> namesByCanonicalKey) {
            header = header.renamed(namesByCanonicalKey);
            return this;
        }

        /**
         * Reorders the complete header using canonical keys or unambiguous output names.
         *
         * @param completeOrder every header identifier in the desired order
         * @return this builder
         */
        public Builder headerOrder(List<String> completeOrder) {
            header = header.reordered(completeOrder);
            return this;
        }

        /**
         * Reorders the complete header using canonical keys or unambiguous output names.
         *
         * @param completeOrder every header identifier in the desired order
         * @return this builder
         */
        public Builder headerOrder(String... completeOrder) {
            Objects.requireNonNull(completeOrder, "completeOrder");
            header = header.reordered(completeOrder);
            return this;
        }

        /**
         * Changes the character set used by output-stream convenience methods.
         *
         * <p>The caller is responsible for any encoding-specific markers required by the target
         * DATEV import route.
         *
         * @param charset the charset for output-stream based writing
         * @return this builder
         */
        public Builder charset(Charset charset) {
            this.charset = Objects.requireNonNull(charset, "charset");
            return this;
        }

        /**
         * Adds the typed EXTF management record required for a complete Buchungsstapel v13 file.
         *
         * <p>Complete output intentionally requires the exact official v13 header, strict row
         * validation and Windows-1252 encoding. These constraints prevent metadata that disagrees
         * with the following records.
         *
         * @param metadata the v13 metadata to serialize as record one
         * @return this builder
         */
        public Builder metadata(DatevMetadata metadata) {
            this.metadata = Objects.requireNonNull(metadata, "metadata");
            return this;
        }

        /**
         * Selects semantic row validation.
         *
         * <p>Official v12/v13 headers default to {@link DatevValidationMode#STRICT}; custom
         * headers default to {@link DatevValidationMode#NONE}. Complete EXTF output requires
         * strict validation.
         *
         * @param validationMode the mode applied atomically during every append
         * @return this builder
         */
        public Builder validationMode(DatevValidationMode validationMode) {
            this.validationMode = Objects.requireNonNull(validationMode, "validationMode");
            return this;
        }

        /**
         * Creates a new file. Reusing this builder never shares row state between files.
         *
         * @return a new file with this builder's current configuration
         */
        public DatevFile build() {
            if (metadata != null) {
                if (!DatevHeader.current().equals(header) && !DatevHeader.legacyV12().equals(header)) {
                    throw new IllegalStateException(
                            "EXTF metadata requires an exact official Buchungsstapel header."
                    );
                }
                int headerVersion = header.bookingBatchVersion().orElseThrow();
                if (metadata.formatVersion() != headerVersion) {
                    throw new IllegalStateException("EXTF metadata declares format version "
                            + metadata.formatVersion() + " but the header is version "
                            + headerVersion + '.');
                }
                if (validationMode != DatevValidationMode.STRICT) {
                    throw new IllegalStateException("Complete EXTF output requires STRICT validation.");
                }
                if (!DEFAULT_CHARSET.equals(charset)) {
                    throw new IllegalStateException(
                            "Complete EXTF output currently supports Windows-1252 only."
                    );
                }
            }
            return new DatevFile(header, charset, metadata, validationMode);
        }
    }
}
