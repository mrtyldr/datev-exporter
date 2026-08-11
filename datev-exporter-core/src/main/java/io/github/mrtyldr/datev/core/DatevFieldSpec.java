package io.github.mrtyldr.datev.core;

import java.util.Objects;

/**
 * Immutable technical definition of an official DATEV Buchungsstapel field.
 *
 * @param fieldNumber one-based DATEV field number
 * @param canonicalKey official column name
 * @param type logical checker type
 * @param maxLength maximum integral digits or text length, depending on {@code type}
 * @param decimalPlaces allowed decimal precision
 * @param required whether the official checker marks the field as necessary
 */
public record DatevFieldSpec(
        int fieldNumber,
        String canonicalKey,
        DatevFieldType type,
        int maxLength,
        int decimalPlaces,
        boolean required
) {

    /**
     * Validates this immutable field definition.
     *
     * @param fieldNumber one-based DATEV field number
     * @param canonicalKey official column name
     * @param type logical checker type
     * @param maxLength maximum integral digits or text length
     * @param decimalPlaces allowed decimal precision
     * @param required whether the field is necessary
     */
    public DatevFieldSpec {
        if (fieldNumber < 1) {
            throw new IllegalArgumentException("DATEV field number must be positive.");
        }
        canonicalKey = Objects.requireNonNull(canonicalKey, "canonicalKey");
        if (canonicalKey.isBlank()) {
            throw new IllegalArgumentException("DATEV canonical key must not be blank.");
        }
        type = Objects.requireNonNull(type, "type");
        if (maxLength < 1) {
            throw new IllegalArgumentException("DATEV field length must be positive.");
        }
        if (decimalPlaces < 0) {
            throw new IllegalArgumentException("DATEV decimal places must not be negative.");
        }
        if ((type == DatevFieldType.TEXT || type == DatevFieldType.ACCOUNT
                || type == DatevFieldType.DATE) && decimalPlaces != 0) {
            throw new IllegalArgumentException(type + " fields cannot define decimal places.");
        }
    }
}
