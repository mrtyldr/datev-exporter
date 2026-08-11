package io.github.mrtyldr.datev.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Format;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Function;

/**
 * A value addressed by its exact official DATEV heading and optionally converted by a formatter.
 *
 * <p>Every factory has two equivalent forms: one taking the raw German heading as a
 * {@code String}, and one taking a {@link DatevField} constant with a readable English name. Both
 * produce an identical column, so {@code DatevColumn.of("Konto", 1200L)} and
 * {@code DatevColumn.of(DatevField.ACCOUNT, 1200L)} are interchangeable. Passing an untyped
 * {@code null} to a two-argument factory is ambiguous and must be cast to the intended overload.
 *
 * @param header exact heading from {@link DatevSchema#headers()}
 * @param value the value; {@code null} represents an empty cell
 * @param formatter optional converter for non-null values
 * @param <T> value type
 */
public record DatevColumn<T>(
        String header,
        T value,
        Function<? super T, String> formatter
) {
    private static final DateTimeFormatter DOCUMENT_DATE = DateTimeFormatter.ofPattern("ddMM");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("ddMMuuuu");

    /**
     * Validates and normalizes the heading identifier.
     *
     * @param header exact official DATEV heading
     * @param value value to serialize, or {@code null}
     * @param formatter optional formatter for non-null values
     */
    public DatevColumn {
        header = normalizeHeader(header);
    }

    /**
     * Creates an unformatted column.
     *
     * @param header exact official DATEV heading
     * @param value value to serialize, or {@code null}
     */
    public DatevColumn(String header, T value) {
        this(header, value, null);
    }

    /**
     * Creates an unformatted column for an official field.
     *
     * @param field official DATEV field
     * @param value value to serialize, or {@code null}
     */
    public DatevColumn(DatevField field, T value) {
        this(headingOf(field), value, null);
    }

    /**
     * Creates a column for an official field, converted by a function.
     *
     * @param field official DATEV field
     * @param value value to serialize, or {@code null}
     * @param formatter optional formatter for non-null values
     */
    public DatevColumn(DatevField field, T value, Function<? super T, String> formatter) {
        this(headingOf(field), value, formatter);
    }

    /**
     * Creates an unformatted column.
     *
     * @param <T> value type
     * @param header exact official DATEV heading
     * @param value value to serialize, or {@code null}
     * @return a new column
     */
    public static <T> DatevColumn<T> of(String header, T value) {
        return new DatevColumn<>(header, value);
    }

    /**
     * Creates an unformatted column for an official field.
     *
     * @param <T> value type
     * @param field official DATEV field
     * @param value value to serialize, or {@code null}
     * @return a new column
     */
    public static <T> DatevColumn<T> of(DatevField field, T value) {
        return new DatevColumn<>(headingOf(field), value);
    }

    /**
     * Creates a column converted by a function.
     *
     * @param <T> value type
     * @param header exact official DATEV heading
     * @param value value to serialize, or {@code null}
     * @param formatter converter invoked for a non-null value
     * @return a new formatted column
     */
    public static <T> DatevColumn<T> formatted(
            String header,
            T value,
            Function<? super T, String> formatter
    ) {
        return new DatevColumn<>(header, value, Objects.requireNonNull(formatter, "formatter"));
    }

    /**
     * Creates a column for an official field, converted by a function.
     *
     * @param <T> value type
     * @param field official DATEV field
     * @param value value to serialize, or {@code null}
     * @param formatter converter invoked for a non-null value
     * @return a new formatted column
     */
    public static <T> DatevColumn<T> formatted(
            DatevField field,
            T value,
            Function<? super T, String> formatter
    ) {
        return formatted(headingOf(field), value, formatter);
    }

    /**
     * Creates a column converted by a {@link Format}. Mutable formats remain the caller's
     * synchronization responsibility.
     *
     * @param <T> value type
     * @param header exact official DATEV heading
     * @param value value to serialize, or {@code null}
     * @param formatter format invoked for a non-null value
     * @return a new formatted column
     */
    public static <T> DatevColumn<T> formatted(String header, T value, Format formatter) {
        Objects.requireNonNull(formatter, "formatter");
        return new DatevColumn<>(header, value, formatter::format);
    }

    /**
     * Creates a column for an official field, converted by a {@link Format}. Mutable formats
     * remain the caller's synchronization responsibility.
     *
     * @param <T> value type
     * @param field official DATEV field
     * @param value value to serialize, or {@code null}
     * @param formatter format invoked for a non-null value
     * @return a new formatted column
     */
    public static <T> DatevColumn<T> formatted(DatevField field, T value, Format formatter) {
        return formatted(headingOf(field), value, formatter);
    }

    /**
     * Creates a positive DATEV amount with exactly two comma-decimal places.
     *
     * @param header exact official DATEV amount heading
     * @param value amount to serialize, or {@code null}
     * @return a canonically formatted amount column
     */
    public static DatevColumn<BigDecimal> amount(String header, BigDecimal value) {
        return formatted(header, value, DatevColumn::formatAmount);
    }

    /**
     * Creates a positive DATEV amount with exactly two comma-decimal places.
     *
     * @param field official DATEV amount field
     * @param value amount to serialize, or {@code null}
     * @return a canonically formatted amount column
     */
    public static DatevColumn<BigDecimal> amount(DatevField field, BigDecimal value) {
        return amount(headingOf(field), value);
    }

    /**
     * Creates a positive numeric DATEV account value.
     *
     * @param header exact official DATEV account heading
     * @param value positive account number
     * @return a formatted account column
     */
    public static DatevColumn<Long> account(String header, long value) {
        return formatted(header, value, DatevColumn::formatAccount);
    }

    /**
     * Creates a positive numeric DATEV account value.
     *
     * @param field official DATEV account field
     * @param value positive account number
     * @return a formatted account column
     */
    public static DatevColumn<Long> account(DatevField field, long value) {
        return account(headingOf(field), value);
    }

    /**
     * Creates the {@code Belegdatum} field in {@code DDMM} form.
     *
     * @param value document date, or {@code null}
     * @return a formatted document-date column
     */
    public static DatevColumn<LocalDate> documentDate(LocalDate value) {
        return formatted(DatevField.DOCUMENT_DATE.heading(), value, DOCUMENT_DATE::format);
    }

    /**
     * Creates a full DATEV date in {@code DDMMYYYY} form.
     *
     * @param header exact official DATEV date heading
     * @param value date to serialize, or {@code null}
     * @return a formatted full-date column
     */
    public static DatevColumn<LocalDate> date(String header, LocalDate value) {
        return formatted(header, value, FULL_DATE::format);
    }

    /**
     * Creates a full DATEV date in {@code DDMMYYYY} form.
     *
     * @param field official DATEV date field
     * @param value date to serialize, or {@code null}
     * @return a formatted full-date column
     */
    public static DatevColumn<LocalDate> date(DatevField field, LocalDate value) {
        return date(headingOf(field), value);
    }

    /**
     * Converts the value once when it is appended.
     *
     * @return the formatted value, or {@code null} for an empty cell
     * @throws IllegalArgumentException if the formatter fails
     */
    public String formattedValue() {
        if (value == null) {
            return null;
        }
        if (formatter == null) {
            return String.valueOf(value);
        }
        try {
            return formatter.apply(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Could not format value for DATEV header '" + header + "'.",
                    exception
            );
        }
    }

    private static String headingOf(DatevField field) {
        return Objects.requireNonNull(field, "field").heading();
    }

    private static String normalizeHeader(String header) {
        Objects.requireNonNull(header, "header");
        String normalized = Normalizer.normalize(header, Normalizer.Form.NFC);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Column header must not be blank.");
        }
        int first = normalized.codePointAt(0);
        int last = normalized.codePointBefore(normalized.length());
        if (isWhitespace(first) || isWhitespace(last)) {
            throw new IllegalArgumentException(
                    "Column header must not have surrounding whitespace: '" + header + "'."
            );
        }
        if (normalized.indexOf(';') >= 0) {
            throw new IllegalArgumentException("Column header must not contain ';': '" + header + "'.");
        }
        rejectControlCharacters(normalized);
        return normalized;
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static void rejectControlCharacters(String value) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException(
                        "Column header must not contain control or line-separator characters."
                );
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static String formatAmount(BigDecimal value) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("DATEV amounts must be greater than zero.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString()
                .replace('.', ',');
    }

    private static String formatAccount(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("DATEV account numbers must be greater than zero.");
        }
        return Long.toString(value);
    }
}
