package io.github.mrtyldr.datev.validation;

import io.github.mrtyldr.datev.core.DatevRowValidation;
import io.github.mrtyldr.datev.core.DatevSchema;
import io.github.mrtyldr.datev.core.DatevValidationContext;
import io.github.mrtyldr.datev.core.DatevValidationError;
import io.github.mrtyldr.datev.core.DatevValidationException;
import io.github.mrtyldr.datev.core.DatevValidationMode;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Immutable, implementation-neutral DATEV Buchungsstapel row validator.
 *
 * <p>This class is a thin adapter: it binds the shared validation core in
 * {@code datev-exporter-core} to the {@code BiConsumer<Integer, List<String>>} callback that both
 * lean {@code DatevFile} factories accept, without linking this artifact to either exporter
 * implementation.
 *
 * <p>The validator receives logical, unquoted values. CSV parsing and escaping remain the
 * responsibility of the selected exporter.
 */
public final class DatevValidator implements BiConsumer<Integer, List<String>> {
    private final DatevValidationMode mode;
    private final DatevValidationContext context;

    private DatevValidator(Builder builder) {
        this.mode = builder.mode;
        this.context = builder.context;
    }

    /**
     * Creates a strict validator without metadata-dependent constraints.
     *
     * @return strict validator
     */
    public static DatevValidator strict() {
        return builder().build();
    }

    /**
     * Creates a field-level validator without required or pair checks.
     *
     * @return field-level validator
     */
    public static DatevValidator fieldLevel() {
        return builder().mode(DatevValidationMode.FIELD_LEVEL).build();
    }

    /**
     * Starts an immutable validator builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the validation depth.
     *
     * @return mode
     */
    public DatevValidationMode mode() {
        return mode;
    }

    /**
     * Returns metadata-dependent constraints.
     *
     * @return immutable context
     */
    public DatevValidationContext context() {
        return context;
    }

    /**
     * Validates a complete fixed-schema row and returns every error in field order.
     *
     * @param schema fixed DATEV schema
     * @param values logical, unquoted cell values
     * @return immutable validation errors
     * @throws IllegalArgumentException when row width differs from the selected schema
     */
    public List<DatevValidationError> validate(DatevSchema schema, List<String> values) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(values, "values");
        if (values.size() != schema.columnCount()) {
            throw new IllegalArgumentException("DATEV " + schema.name() + " expects "
                    + schema.columnCount() + " values but received " + values.size() + '.');
        }
        return DatevRowValidation.validate(schema.headers(), values, mode, context, true);
    }

    /**
     * Validates by numeric schema version, matching the lean exporters' dependency-free callback.
     *
     * @param formatVersion DATEV Buchungsstapel format version 12 or 13
     * @param values logical, unquoted cell values
     * @return immutable validation errors
     */
    public List<DatevValidationError> validate(int formatVersion, List<String> values) {
        return validate(DatevSchema.fromFormatVersion(formatVersion), values);
    }

    /**
     * Validates and throws one aggregate exception when any rule fails.
     *
     * @param schema fixed DATEV schema
     * @param values logical, unquoted cell values
     * @throws DatevValidationException when validation fails
     */
    public void validateOrThrow(DatevSchema schema, List<String> values) {
        List<DatevValidationError> errors = validate(schema, values);
        if (!errors.isEmpty()) {
            throw new DatevValidationException(errors);
        }
    }

    /**
     * Validates by numeric schema version and throws one aggregate exception on failure.
     *
     * @param formatVersion DATEV Buchungsstapel format version 12 or 13
     * @param values logical, unquoted cell values
     */
    public void validateOrThrow(int formatVersion, List<String> values) {
        validateOrThrow(DatevSchema.fromFormatVersion(formatVersion), values);
    }

    /**
     * Implements the standard callback accepted directly by both lean exporters.
     *
     * @param formatVersion DATEV Buchungsstapel format version 12 or 13
     * @param values immutable logical row values
     */
    @Override
    public void accept(Integer formatVersion, List<String> values) {
        validateOrThrow(Objects.requireNonNull(formatVersion, "formatVersion"), values);
    }

    /** Builder for an immutable validator. */
    public static final class Builder {
        private DatevValidationMode mode = DatevValidationMode.STRICT;
        private DatevValidationContext context = DatevValidationContext.empty();

        private Builder() {
        }

        /**
         * Sets the validation depth.
         *
         * @param mode validation depth
         * @return this builder
         */
        public Builder mode(DatevValidationMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /**
         * Sets optional metadata-dependent constraints.
         *
         * @param context immutable validation context
         * @return this builder
         */
        public Builder context(DatevValidationContext context) {
            this.context = Objects.requireNonNull(context, "context");
            return this;
        }

        /**
         * Builds an immutable validator.
         *
         * @return validator
         */
        public DatevValidator build() {
            return new DatevValidator(this);
        }
    }
}
