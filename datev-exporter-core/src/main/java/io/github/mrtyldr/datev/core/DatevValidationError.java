package io.github.mrtyldr.datev.core;

import java.util.Objects;

/**
 * One immutable semantic validation error.
 *
 * @param code stable machine-readable category
 * @param fieldNumber one-based DATEV field number
 * @param canonicalKey official column name
 * @param message human-readable explanation
 */
public record DatevValidationError(
        Code code,
        int fieldNumber,
        String canonicalKey,
        String message
) {

    /** Stable validation error categories. */
    public enum Code {
        /** A necessary field is empty. */
        REQUIRED_FIELD,
        /** A value does not use DATEV's required representation. */
        INVALID_FORMAT,
        /** A syntactically valid value exceeds its range. */
        VALUE_OUT_OF_RANGE,
        /** A textual value is too long. */
        TEXT_TOO_LONG,
        /** A value cannot be represented by DATEV's Windows-1252 output profile. */
        UNMAPPABLE_CHARACTER,
        /** A paired field is missing. */
        DEPENDENT_FIELD_MISSING
    }

    /**
     * Validates this immutable error.
     *
     * @param code machine-readable category
     * @param fieldNumber one-based DATEV field number
     * @param canonicalKey official column name
     * @param message human-readable explanation
     */
    public DatevValidationError {
        code = Objects.requireNonNull(code, "code");
        if (fieldNumber < 1) {
            throw new IllegalArgumentException("DATEV field number must be positive.");
        }
        canonicalKey = Objects.requireNonNull(canonicalKey, "canonicalKey");
        if (canonicalKey.isBlank()) {
            throw new IllegalArgumentException("DATEV canonical key must not be blank.");
        }
        message = Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("Validation error message must not be blank.");
        }
    }
}
