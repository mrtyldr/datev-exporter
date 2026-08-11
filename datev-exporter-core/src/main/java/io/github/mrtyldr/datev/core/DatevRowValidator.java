package io.github.mrtyldr.datev.core;


import java.util.List;
import java.util.Objects;

/**
 * Validates logical, unquoted DATEV row values against the official Buchungsstapel schema.
 *
 * <p>This class is a thin adapter over {@link DatevRowValidation}. It adds schema identity
 * handling for a {@link DatevHeader} and translates {@link DatevMetadata} into a
 * {@link DatevValidationContext}.
 *
 * <p>CSV delimiters, quotes, and escaping are deliberately outside this class. Values are
 * validated after a {@link DatevColumn} formatter has run and before a row is committed to a file.
 * Rules are located through stable canonical keys, so renaming or reordering an official
 * {@link DatevHeader} does not detach its field semantics.
 *
 * @see <a href="https://developer.datev.de/de/file-format/details/datev-format/format-description/booking-batch">
 *     Official Buchungsstapel field description</a>
 */
public final class DatevRowValidator {

    private DatevRowValidator() {
    }

    /**
     * Validates a row without metadata-dependent account and posting-period checks.
     *
     * @param canonicalKeys stable canonical keys in the row's output order
     * @param values logical, unquoted cell values in the same order
     * @param mode validation mode
     * @return an immutable list of all discovered errors
     */
    public static List<DatevValidationError> validate(
            List<String> canonicalKeys,
            List<String> values,
            DatevValidationMode mode
    ) {
        return validate(canonicalKeys, values, mode, null);
    }

    /**
     * Validates a row and returns every discovered error in deterministic field order.
     *
     * <p>Unknown custom fields are ignored. Mandatory and cross-field rules are applied only when
     * {@code canonicalKeys} is a complete official v12 or v13 schema; known official fields in a
     * custom schema still receive field-level validation.
     *
     * @param canonicalKeys stable canonical keys in the row's output order
     * @param values logical, unquoted cell values in the same order
     * @param mode validation mode
     * @param metadata optional metadata used for account length and posting-period checks
     * @return an immutable list of all discovered errors
     * @throws IllegalArgumentException if key/value widths differ or keys are null or duplicated
     */
    public static List<DatevValidationError> validate(
            List<String> canonicalKeys,
            List<String> values,
            DatevValidationMode mode,
            DatevMetadata metadata
    ) {
        Objects.requireNonNull(canonicalKeys, "canonicalKeys");
        return DatevRowValidation.validate(
                canonicalKeys,
                values,
                mode,
                contextFor(metadata),
                DatevFieldSpecs.isOfficialSchema(canonicalKeys)
        );
    }

    /**
     * Validates a row using the schema identity retained by a {@link DatevHeader}.
     *
     * <p>This overload distinguishes an official header that was renamed or reordered from an
     * independently created custom header containing the same names.
     *
     * @param header immutable header supplying stable canonical keys and schema identity
     * @param values logical, unquoted cell values in configured output order
     * @param mode validation mode
     * @param metadata optional metadata used for account length and posting-period checks
     * @return an immutable list of all discovered errors
     */
    public static List<DatevValidationError> validate(
            DatevHeader header,
            List<String> values,
            DatevValidationMode mode,
            DatevMetadata metadata
    ) {
        Objects.requireNonNull(header, "header");
        return DatevRowValidation.validate(
                header.keys(),
                values,
                mode,
                contextFor(metadata),
                header.bookingBatchFormatVersion() != null
        );
    }

    /**
     * Validates a row and throws one aggregate exception if any rule fails.
     *
     * @param canonicalKeys stable canonical keys in the row's output order
     * @param values logical, unquoted cell values in the same order
     * @param mode validation mode
     */
    public static void validateOrThrow(
            List<String> canonicalKeys,
            List<String> values,
            DatevValidationMode mode
    ) {
        validateOrThrow(canonicalKeys, values, mode, null);
    }

    /**
     * Validates a row with optional metadata and throws one aggregate exception if any rule fails.
     *
     * @param canonicalKeys stable canonical keys in the row's output order
     * @param values logical, unquoted cell values in the same order
     * @param mode validation mode
     * @param metadata optional metadata context
     * @throws DatevValidationException when at least one semantic rule fails
     */
    public static void validateOrThrow(
            List<String> canonicalKeys,
            List<String> values,
            DatevValidationMode mode,
            DatevMetadata metadata
    ) {
        throwIfInvalid(validate(canonicalKeys, values, mode, metadata));
    }

    /**
     * Validates a row using its header's schema identity and throws if any semantic rule fails.
     *
     * @param header immutable header supplying stable canonical keys and schema identity
     * @param values logical, unquoted cell values in configured output order
     * @param mode validation mode
     * @param metadata optional metadata context
     * @throws DatevValidationException when at least one semantic rule fails
     */
    public static void validateOrThrow(
            DatevHeader header,
            List<String> values,
            DatevValidationMode mode,
            DatevMetadata metadata
    ) {
        throwIfInvalid(validate(header, values, mode, metadata));
    }

    private static void throwIfInvalid(List<DatevValidationError> errors) {
        if (!errors.isEmpty()) {
            throw new DatevValidationException(errors);
        }
    }

    private static DatevValidationContext contextFor(DatevMetadata metadata) {
        if (metadata == null) {
            return DatevValidationContext.empty();
        }
        return DatevValidationContext.builder()
                .accountLength(metadata.accountLength())
                .period(metadata.periodStart(), metadata.periodEnd())
                .build();
    }
}
