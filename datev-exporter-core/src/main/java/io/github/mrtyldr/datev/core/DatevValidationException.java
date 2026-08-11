package io.github.mrtyldr.datev.core;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/** Aggregate exception thrown when one row has semantic validation errors. */
public final class DatevValidationException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Immutable errors retained for programmatic inspection. */
    @SuppressWarnings("serial") // Validation exceptions are not used across serialization boundaries.
    private final List<DatevValidationError> errors;

    /**
     * Creates an aggregate exception.
     *
     * @param errors non-empty errors in deterministic field order
     */
    public DatevValidationException(List<DatevValidationError> errors) {
        super(messageFor(errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * Returns all errors as an immutable list.
     *
     * @return validation errors
     */
    public List<DatevValidationError> errors() {
        return errors;
    }

    private static String messageFor(List<DatevValidationError> errors) {
        Objects.requireNonNull(errors, "errors");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("At least one DATEV validation error is required.");
        }
        DatevValidationError first = Objects.requireNonNull(
                errors.get(0), "errors must not contain null");
        for (DatevValidationError error : errors) {
            Objects.requireNonNull(error, "errors must not contain null");
        }
        if (errors.size() == 1) {
            return first.message();
        }
        return "DATEV row contains " + errors.size()
                + " validation errors. First error: " + first.message();
    }
}
