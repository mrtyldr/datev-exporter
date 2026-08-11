package io.github.mrtyldr.datev.core;

/** Controls semantic validation depth. */
public enum DatevValidationMode {
    /** Skip semantic checks. Structural checks in the selected exporter still apply. */
    NONE,

    /** Validate each supplied non-empty cell against its official field definition. */
    FIELD_LEVEL,

    /** Add required-field and cross-field dependency checks. */
    STRICT
}
