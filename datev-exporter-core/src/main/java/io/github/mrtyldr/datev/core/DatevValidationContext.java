package io.github.mrtyldr.datev.core;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Optional immutable context for rules that cannot be inferred from a booking row.
 *
 * <p>An empty context still enables every context-independent DATEV field rule. Account length
 * narrows account fields; a posting period allows validating the four-digit {@code Belegdatum}
 * against real calendar dates in that period.
 */
public final class DatevValidationContext {
    private static final DatevValidationContext EMPTY = new Builder().build();

    private final Integer accountLength;
    private final LocalDate fiscalYearStart;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;

    private DatevValidationContext(Builder builder) {
        this.accountLength = builder.accountLength;
        this.fiscalYearStart = builder.fiscalYearStart;
        this.periodStart = builder.periodStart;
        this.periodEnd = builder.periodEnd;
    }

    /**
     * Returns context with no metadata-dependent constraints.
     *
     * @return shared empty context
     */
    public static DatevValidationContext empty() {
        return EMPTY;
    }

    /**
     * Starts a context builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the configured base account length.
     *
     * @return account length, if known
     */
    public OptionalInt accountLength() {
        return accountLength == null ? OptionalInt.empty() : OptionalInt.of(accountLength);
    }

    /**
     * Returns the first day of the fiscal year.
     *
     * @return fiscal-year start, if known
     */
    public Optional<LocalDate> fiscalYearStart() {
        return Optional.ofNullable(fiscalYearStart);
    }

    /**
     * Returns the first covered posting date.
     *
     * @return posting-period start, if known
     */
    public Optional<LocalDate> periodStart() {
        return Optional.ofNullable(periodStart);
    }

    /**
     * Returns the last covered posting date.
     *
     * @return posting-period end, if known
     */
    public Optional<LocalDate> periodEnd() {
        return Optional.ofNullable(periodEnd);
    }

    /**
     * Compares every configured constraint.
     *
     * @param other the object to compare with
     * @return {@code true} if the other object is a context with equal constraints
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DatevValidationContext context)) {
            return false;
        }
        return Objects.equals(accountLength, context.accountLength)
                && Objects.equals(fiscalYearStart, context.fiscalYearStart)
                && Objects.equals(periodStart, context.periodStart)
                && Objects.equals(periodEnd, context.periodEnd);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(accountLength, fiscalYearStart, periodStart, periodEnd);
    }

    /**
     * Returns a diagnostic description of the configured constraints.
     *
     * @return the constraints, or {@code DatevValidationContext[empty]}
     */
    @Override
    public String toString() {
        if (accountLength == null && fiscalYearStart == null
                && periodStart == null && periodEnd == null) {
            return "DatevValidationContext[empty]";
        }
        return "DatevValidationContext[accountLength=" + accountLength
                + ", fiscalYearStart=" + fiscalYearStart
                + ", period=" + periodStart + ".." + periodEnd + ']';
    }

    Integer nullableAccountLength() {
        return accountLength;
    }

    LocalDate nullablePeriodStart() {
        return periodStart;
    }

    LocalDate nullablePeriodEnd() {
        return periodEnd;
    }

    /** Mutable builder producing validated immutable contexts. */
    public static final class Builder {
        private Integer accountLength;
        private LocalDate fiscalYearStart;
        private LocalDate periodStart;
        private LocalDate periodEnd;

        private Builder() {
        }

        /**
         * Sets the DATEV base account length.
         *
         * @param accountLength integer from 4 through 8
         * @return this builder
         */
        public Builder accountLength(int accountLength) {
            if (accountLength < 4 || accountLength > 8) {
                throw new IllegalArgumentException("DATEV account length must be from 4 through 8.");
            }
            this.accountLength = accountLength;
            return this;
        }

        /**
         * Sets the first day of the fiscal year.
         *
         * @param fiscalYearStart date in years 2000 through 2099
         * @return this builder
         */
        public Builder fiscalYearStart(LocalDate fiscalYearStart) {
            this.fiscalYearStart = validateDate(fiscalYearStart, "Fiscal-year start");
            return this;
        }

        /**
         * Sets the inclusive posting period.
         *
         * @param start first covered date
         * @param end last covered date
         * @return this builder
         */
        public Builder period(LocalDate start, LocalDate end) {
            this.periodStart = validateDate(start, "Posting-period start");
            this.periodEnd = validateDate(end, "Posting-period end");
            return this;
        }

        /**
         * Builds the context and validates relationships between supplied dates.
         *
         * @return immutable context
         */
        public DatevValidationContext build() {
            if ((periodStart == null) != (periodEnd == null)) {
                throw new IllegalStateException(
                        "Posting-period start and end must be configured together.");
            }
            if (periodStart != null && periodStart.isAfter(periodEnd)) {
                throw new IllegalStateException("Posting-period start must not be after its end.");
            }
            if (fiscalYearStart != null && periodStart != null) {
                LocalDate fiscalYearEndExclusive = fiscalYearStart.plusYears(1);
                if (periodStart.isBefore(fiscalYearStart)
                        || !periodEnd.isBefore(fiscalYearEndExclusive)) {
                    throw new IllegalStateException(
                            "Posting period must be contained in the configured fiscal year.");
                }
            }
            return new DatevValidationContext(this);
        }

        private static LocalDate validateDate(LocalDate date, String label) {
            Objects.requireNonNull(date, label);
            if (date.getYear() < 2000 || date.getYear() > 2099) {
                throw new IllegalArgumentException(label + " year must be from 2000 through 2099.");
            }
            return date;
        }
    }
}
