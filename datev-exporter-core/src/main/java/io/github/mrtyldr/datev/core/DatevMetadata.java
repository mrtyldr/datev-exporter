package io.github.mrtyldr.datev.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable metadata for the first record of a DATEV Buchungsstapel v13 file.
 *
 * <p>The generated record contains the 31 fields required by DATEV header version 700,
 * format category 21 and format version 13. Fixed and reserved fields cannot be changed. Use
 * {@link #bookingBatchV13()} to configure the business-specific fields.
 */
public final class DatevMetadata {

    /** DATEV management-record version used by this metadata type. */
    public static final int HEADER_VERSION = 700;

    /** DATEV format category for Buchungsstapel. */
    public static final int FORMAT_CATEGORY = 21;

    /** DATEV format name for booking batches. */
    public static final String FORMAT_NAME = "Buchungsstapel";

    /** Current DATEV Buchungsstapel format version. */
    public static final int FORMAT_VERSION = 13;

    private static final int FIELD_COUNT = 31;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmssSSS", Locale.ROOT);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Currency EURO = Currency.getInstance("EUR");

    private static final Pattern ORIGIN_PATTERN = Pattern.compile("[A-Za-z0-9_]{0,2}");
    // DATEV publishes a \w expression here but its own example is "Max Mustermann".
    // Accept ASCII spaces as the narrow, example-backed exception.
    private static final Pattern PERSON_PATTERN = Pattern.compile("[A-Za-z0-9_ ]{0,25}");
    private static final Pattern DESCRIPTION_PATTERN =
            Pattern.compile("[A-Za-z0-9_.\\-/ ]{0,30}");
    private static final Pattern DICTATION_CODE_PATTERN = Pattern.compile("(?:[A-Z]{2}){0,2}");
    private static final Pattern CHART_OF_ACCOUNTS_PATTERN = Pattern.compile("(?:[0-9]{2}){0,2}");
    private static final Pattern INDUSTRY_SOLUTION_PATTERN = Pattern.compile("[0-9]{0,4}");

    private static final boolean[] QUOTED_FIELDS = {
            true, false, false, true, false, false, false, true, true, true,
            false, false, false, false, false, false, true, true, false, false,
            false, true, false, true, false, false, true, false, false, true, true
    };

    private final LocalDateTime createdAt;
    private final String origin;
    private final String exportedBy;
    private final String importedBy;
    private final int advisorNumber;
    private final int clientNumber;
    private final LocalDate fiscalYearStart;
    private final int accountLength;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final String description;
    private final String dictationCode;
    private final BookingType bookingType;
    private final AccountingPurpose accountingPurpose;
    private final boolean fixed;
    private final Currency currency;
    private final String chartOfAccounts;
    private final String industrySolutionId;
    private final String applicationInformation;

    private DatevMetadata(Builder builder) {
        this.createdAt = builder.createdAt;
        this.origin = builder.origin;
        this.exportedBy = builder.exportedBy;
        this.importedBy = builder.importedBy;
        this.advisorNumber = builder.advisorNumber;
        this.clientNumber = builder.clientNumber;
        this.fiscalYearStart = builder.fiscalYearStart;
        this.accountLength = builder.accountLength;
        this.periodStart = builder.periodStart;
        this.periodEnd = builder.periodEnd;
        this.description = builder.description;
        this.dictationCode = builder.dictationCode;
        this.bookingType = builder.bookingType;
        this.accountingPurpose = builder.accountingPurpose;
        this.fixed = builder.fixed;
        this.currency = builder.currency;
        this.chartOfAccounts = builder.chartOfAccounts;
        this.industrySolutionId = builder.industrySolutionId;
        this.applicationInformation = builder.applicationInformation;
    }

    /**
     * Compares every business-specific field. The fixed and reserved fields are identical for all
     * instances and are therefore not part of the comparison.
     *
     * @param other the object to compare with
     * @return {@code true} if the other object is a metadata record with equal fields
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DatevMetadata metadata)) {
            return false;
        }
        return advisorNumber == metadata.advisorNumber
                && clientNumber == metadata.clientNumber
                && accountLength == metadata.accountLength
                && fixed == metadata.fixed
                && Objects.equals(createdAt, metadata.createdAt)
                && Objects.equals(origin, metadata.origin)
                && Objects.equals(exportedBy, metadata.exportedBy)
                && Objects.equals(importedBy, metadata.importedBy)
                && Objects.equals(fiscalYearStart, metadata.fiscalYearStart)
                && Objects.equals(periodStart, metadata.periodStart)
                && Objects.equals(periodEnd, metadata.periodEnd)
                && Objects.equals(description, metadata.description)
                && Objects.equals(dictationCode, metadata.dictationCode)
                && bookingType == metadata.bookingType
                && accountingPurpose == metadata.accountingPurpose
                && Objects.equals(currency, metadata.currency)
                && Objects.equals(chartOfAccounts, metadata.chartOfAccounts)
                && Objects.equals(industrySolutionId, metadata.industrySolutionId)
                && Objects.equals(applicationInformation, metadata.applicationInformation);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(createdAt, origin, exportedBy, importedBy, advisorNumber, clientNumber,
                fiscalYearStart, accountLength, periodStart, periodEnd, description, dictationCode,
                bookingType, accountingPurpose, fixed, currency, chartOfAccounts,
                industrySolutionId, applicationInformation);
    }

    /**
     * Returns a diagnostic description identifying the client and period.
     *
     * <p>This is deliberately short. Use {@link #toCsvLine()} for the complete record.
     *
     * @return the advisor, client, period and fixed flag
     */
    @Override
    public String toString() {
        return "DatevMetadata[advisor=" + advisorNumber
                + ", client=" + clientNumber
                + ", period=" + periodStart + ".." + periodEnd
                + ", fixed=" + fixed + ']';
    }

    /**
     * Starts a builder for an external Buchungsstapel v13 export.
     *
     * @return a builder with DATEV's documented defaults
     */
    public static Builder bookingBatchV13() {
        return new Builder();
    }

    /**
     * Returns DATEV's management-record version.
     *
     * @return {@value #HEADER_VERSION}
     */
    public int headerVersion() {
        return HEADER_VERSION;
    }

    /**
     * Returns the Buchungsstapel format category.
     *
     * @return {@value #FORMAT_CATEGORY}
     */
    public int formatCategory() {
        return FORMAT_CATEGORY;
    }

    /**
     * Returns the DATEV format name.
     *
     * @return {@value #FORMAT_NAME}
     */
    public String formatName() {
        return FORMAT_NAME;
    }

    /**
     * Returns the Buchungsstapel data format version.
     *
     * @return {@value #FORMAT_VERSION}
     */
    public int formatVersion() {
        return FORMAT_VERSION;
    }

    /**
     * Returns the creation timestamp, truncated to millisecond precision.
     *
     * @return the creation timestamp
     */
    public LocalDateTime createdAt() {
        return createdAt;
    }

    /**
     * Returns the optional two-character origin code.
     *
     * @return the origin code, possibly empty
     */
    public String origin() {
        return origin;
    }

    /**
     * Returns the optional exporting user or application identifier.
     *
     * @return the exporter identifier, possibly empty
     */
    public String exportedBy() {
        return exportedBy;
    }

    /**
     * Returns the optional importing user identifier.
     *
     * @return the importer identifier, possibly empty
     */
    public String importedBy() {
        return importedBy;
    }

    /**
     * Returns the DATEV adviser number.
     *
     * @return the adviser number
     */
    public int advisorNumber() {
        return advisorNumber;
    }

    /**
     * Returns the DATEV client number.
     *
     * @return the client number
     */
    public int clientNumber() {
        return clientNumber;
    }

    /**
     * Returns the first day of the fiscal year.
     *
     * @return the fiscal-year start date
     */
    public LocalDate fiscalYearStart() {
        return fiscalYearStart;
    }

    /**
     * Returns the configured G/L account length.
     *
     * @return an account length from 4 through 8
     */
    public int accountLength() {
        return accountLength;
    }

    /**
     * Returns the first date covered by this batch.
     *
     * @return the period start
     */
    public LocalDate periodStart() {
        return periodStart;
    }

    /**
     * Returns the last date covered by this batch.
     *
     * @return the period end
     */
    public LocalDate periodEnd() {
        return periodEnd;
    }

    /**
     * Returns the optional batch description.
     *
     * @return the description, possibly empty
     */
    public String description() {
        return description;
    }

    /**
     * Returns the optional editor code.
     *
     * @return the dictation code, possibly empty
     */
    public String dictationCode() {
        return dictationCode;
    }

    /**
     * Returns the booking type.
     *
     * @return the booking type
     */
    public BookingType bookingType() {
        return bookingType;
    }

    /**
     * Returns the accounting purpose.
     *
     * @return the accounting purpose
     */
    public AccountingPurpose accountingPurpose() {
        return accountingPurpose;
    }

    /**
     * Returns whether the batch is marked for fixation.
     *
     * @return {@code true} for DATEV value 1, {@code false} for value 0
     */
    public boolean fixed() {
        return fixed;
    }

    /**
     * Returns the batch currency.
     *
     * @return the ISO 4217 currency
     */
    public Currency currency() {
        return currency;
    }

    /**
     * Returns the optional chart-of-accounts identifier.
     *
     * @return zero, two or four digits
     */
    public String chartOfAccounts() {
        return chartOfAccounts;
    }

    /**
     * Returns the optional DATEV industry-solution identifier.
     *
     * @return up to four digits, possibly empty
     */
    public String industrySolutionId() {
        return industrySolutionId;
    }

    /**
     * Returns the optional identifier of the issuing application.
     *
     * @return the application information, possibly empty
     */
    public String applicationInformation() {
        return applicationInformation;
    }

    /**
     * Serializes the exact 31-field EXTF management record without a line terminator.
     *
     * <p>Text fields, including designated empty text fields, are quoted. Quotes occurring in
     * application information are escaped by doubling them.
     *
     * @return the semicolon-separated EXTF record
     */
    public String toCsvLine() {
        String[] fields = {
                "EXTF",
                Integer.toString(HEADER_VERSION),
                Integer.toString(FORMAT_CATEGORY),
                FORMAT_NAME,
                Integer.toString(FORMAT_VERSION),
                TIMESTAMP_FORMATTER.format(createdAt),
                "",
                origin,
                exportedBy,
                importedBy,
                Integer.toString(advisorNumber),
                Integer.toString(clientNumber),
                DATE_FORMATTER.format(fiscalYearStart),
                Integer.toString(accountLength),
                DATE_FORMATTER.format(periodStart),
                DATE_FORMATTER.format(periodEnd),
                description,
                dictationCode,
                Integer.toString(bookingType.code()),
                Integer.toString(accountingPurpose.code()),
                fixed ? "1" : "0",
                currency.getCurrencyCode(),
                "",
                "",
                "",
                "",
                chartOfAccounts,
                industrySolutionId,
                "",
                "",
                applicationInformation
        };

        StringBuilder line = new StringBuilder(256);
        for (int index = 0; index < FIELD_COUNT; index++) {
            if (index > 0) {
                line.append(';');
            }
            if (QUOTED_FIELDS[index]) {
                appendQuoted(line, fields[index]);
            } else {
                line.append(fields[index]);
            }
        }
        return line.toString();
    }

    private static void appendQuoted(StringBuilder target, String value) {
        target.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            target.append(character);
            if (character == '"') {
                target.append('"');
            }
        }
        target.append('"');
    }

    private static LocalDateTime validateTimestamp(LocalDateTime value) {
        if (value == null) {
            throw new IllegalArgumentException("Creation timestamp must not be null.");
        }
        validateYear(value.getYear(), "Creation timestamp");
        return value.truncatedTo(ChronoUnit.MILLIS);
    }

    private static LocalDate validateDate(LocalDate value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null.");
        }
        validateYear(value.getYear(), field);
        return value;
    }

    private static void validateYear(int year, String field) {
        if (year < 2000 || year > 2099) {
            throw new IllegalArgumentException(field + " year must be between 2000 and 2099.");
        }
    }

    private static String validatePattern(String value, Pattern pattern, String field, String rule) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null.");
        }
        rejectControlCharacters(value, field);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must match " + rule + ".");
        }
        return value;
    }

    private static String validateApplicationInformation(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Application information must not be null.");
        }
        rejectControlCharacters(value, "Application information");
        int length = value.codePointCount(0, value.length());
        if (length > 16) {
            throw new IllegalArgumentException(
                    "Application information must contain at most 16 characters."
            );
        }
        return value;
    }

    private static void rejectControlCharacters(String value, String field) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException(field + " must not contain control characters.");
            }
            offset += Character.charCount(codePoint);
        }
    }

    /** Booking type encoded in metadata field 19. */
    public enum BookingType {
        /** Financial accounting, DATEV value 1. */
        FINANCIAL_ACCOUNTING(1),

        /** Annual financial statements, DATEV value 2. */
        ANNUAL_FINANCIAL_STATEMENTS(2);

        private final int code;

        BookingType(int code) {
            this.code = code;
        }

        /**
         * Returns the DATEV numeric value.
         *
         * @return 1 or 2
         */
        public int code() {
            return code;
        }
    }

    /** Accounting purpose encoded in metadata field 20. */
    public enum AccountingPurpose {
        /** Independent accounting, DATEV value 0. */
        INDEPENDENT(0),

        /** Tax-law accounting, DATEV value 30. */
        TAX_LAW(30),

        /** Cost-accounting purpose, DATEV value 40. */
        CALCULATION(40),

        /** Commercial-law accounting, DATEV value 50. */
        COMMERCIAL_LAW(50),

        /** International Financial Reporting Standards, DATEV value 64. */
        IFRS(64);

        private final int code;

        AccountingPurpose(int code) {
            this.code = code;
        }

        /**
         * Returns the DATEV numeric value.
         *
         * @return one of 0, 30, 40, 50 or 64
         */
        public int code() {
            return code;
        }
    }

    /** Builds immutable Buchungsstapel v13 metadata. */
    public static final class Builder {
        private LocalDateTime createdAt;
        private String origin = "";
        private String exportedBy = "";
        private String importedBy = "";
        private Integer advisorNumber;
        private Integer clientNumber;
        private LocalDate fiscalYearStart;
        private Integer accountLength;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String description = "";
        private String dictationCode = "";
        private BookingType bookingType = BookingType.FINANCIAL_ACCOUNTING;
        private AccountingPurpose accountingPurpose = AccountingPurpose.INDEPENDENT;
        private boolean fixed = true;
        private Currency currency = EURO;
        private String chartOfAccounts = "";
        private String industrySolutionId = "";
        private String applicationInformation = "";

        private Builder() {
        }

        /**
         * Sets the local creation timestamp. Sub-millisecond precision is truncated.
         *
         * @param createdAt timestamp to encode as {@code uuuuMMddHHmmssSSS}
         * @return this builder
         */
        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = validateTimestamp(createdAt);
            return this;
        }

        /**
         * Sets the optional origin code.
         *
         * @param origin zero to two ASCII letters, digits or underscores
         * @return this builder
         */
        public Builder origin(String origin) {
            this.origin = validatePattern(origin, ORIGIN_PATTERN, "Origin", "[A-Za-z0-9_]{0,2}");
            return this;
        }

        /**
         * Sets the optional exporter identifier.
         *
         * @param exportedBy zero to 25 ASCII letters, digits, underscores or spaces
         * @return this builder
         */
        public Builder exportedBy(String exportedBy) {
            this.exportedBy = validatePattern(
                    exportedBy,
                    PERSON_PATTERN,
                    "Exporter identifier",
                    "[A-Za-z0-9_ ]{0,25}"
            );
            return this;
        }

        /**
         * Sets the optional importer identifier.
         *
         * @param importedBy zero to 25 ASCII letters, digits, underscores or spaces
         * @return this builder
         */
        public Builder importedBy(String importedBy) {
            this.importedBy = validatePattern(
                    importedBy,
                    PERSON_PATTERN,
                    "Importer identifier",
                    "[A-Za-z0-9_ ]{0,25}"
            );
            return this;
        }

        /**
         * Sets the DATEV adviser number.
         *
         * @param advisorNumber a value from 1001 through 9,999,999
         * @return this builder
         */
        public Builder advisorNumber(int advisorNumber) {
            if (advisorNumber < 1001 || advisorNumber > 9_999_999) {
                throw new IllegalArgumentException(
                        "Adviser number must be between 1001 and 9999999."
                );
            }
            this.advisorNumber = advisorNumber;
            return this;
        }

        /**
         * Sets the DATEV client number.
         *
         * @param clientNumber a value from 1 through 99,999
         * @return this builder
         */
        public Builder clientNumber(int clientNumber) {
            if (clientNumber < 1 || clientNumber > 99_999) {
                throw new IllegalArgumentException("Client number must be between 1 and 99999.");
            }
            this.clientNumber = clientNumber;
            return this;
        }

        /**
         * Sets the first day of the fiscal year.
         *
         * @param fiscalYearStart a valid date in years 2000 through 2099
         * @return this builder
         */
        public Builder fiscalYearStart(LocalDate fiscalYearStart) {
            this.fiscalYearStart = validateDate(fiscalYearStart, "Fiscal-year start");
            return this;
        }

        /**
         * Sets the G/L account length configured for the DATEV client.
         *
         * @param accountLength an integer from 4 through 8
         * @return this builder
         */
        public Builder accountLength(int accountLength) {
            if (accountLength < 4 || accountLength > 8) {
                throw new IllegalArgumentException("Account length must be between 4 and 8.");
            }
            this.accountLength = accountLength;
            return this;
        }

        /**
         * Sets both inclusive dates covered by the booking batch.
         *
         * @param start first covered date
         * @param end last covered date
         * @return this builder
         */
        public Builder period(LocalDate start, LocalDate end) {
            this.periodStart = validateDate(start, "Period start");
            this.periodEnd = validateDate(end, "Period end");
            return this;
        }

        /**
         * Sets the inclusive first date covered by the booking batch.
         *
         * @param periodStart first covered date
         * @return this builder
         */
        public Builder periodStart(LocalDate periodStart) {
            this.periodStart = validateDate(periodStart, "Period start");
            return this;
        }

        /**
         * Sets the inclusive last date covered by the booking batch.
         *
         * @param periodEnd last covered date
         * @return this builder
         */
        public Builder periodEnd(LocalDate periodEnd) {
            this.periodEnd = validateDate(periodEnd, "Period end");
            return this;
        }

        /**
         * Sets the optional batch description.
         *
         * @param description zero to 30 DATEV-supported description characters
         * @return this builder
         */
        public Builder description(String description) {
            this.description = validatePattern(
                    description,
                    DESCRIPTION_PATTERN,
                    "Description",
                    "[A-Za-z0-9_.\\-/ ]{0,30}"
            );
            return this;
        }

        /**
         * Sets the optional editor code.
         *
         * @param dictationCode zero, two or four uppercase ASCII letters
         * @return this builder
         */
        public Builder dictationCode(String dictationCode) {
            this.dictationCode = validatePattern(
                    dictationCode,
                    DICTATION_CODE_PATTERN,
                    "Dictation code",
                    "([A-Z]{2}){0,2}"
            );
            return this;
        }

        /**
         * Sets the booking type.
         *
         * @param bookingType booking type to serialize
         * @return this builder
         */
        public Builder bookingType(BookingType bookingType) {
            if (bookingType == null) {
                throw new IllegalArgumentException("Booking type must not be null.");
            }
            this.bookingType = bookingType;
            return this;
        }

        /**
         * Sets the accounting purpose.
         *
         * @param accountingPurpose purpose to serialize
         * @return this builder
         */
        public Builder accountingPurpose(AccountingPurpose accountingPurpose) {
            if (accountingPurpose == null) {
                throw new IllegalArgumentException("Accounting purpose must not be null.");
            }
            this.accountingPurpose = accountingPurpose;
            return this;
        }

        /**
         * Sets DATEV's Festschreibung flag.
         *
         * @param fixed {@code true} for value 1, {@code false} for value 0
         * @return this builder
         */
        public Builder fixed(boolean fixed) {
            this.fixed = fixed;
            return this;
        }

        /**
         * Sets the ISO 4217 currency.
         *
         * @param currency three-letter currency to serialize
         * @return this builder
         */
        public Builder currency(Currency currency) {
            if (currency == null) {
                throw new IllegalArgumentException("Currency must not be null.");
            }
            String currencyCode = currency.getCurrencyCode();
            if (!currencyCode.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("Currency code must contain three uppercase letters.");
            }
            this.currency = currency;
            return this;
        }

        /**
         * Sets the optional chart-of-accounts code.
         *
         * @param chartOfAccounts zero, two or four digits
         * @return this builder
         */
        public Builder chartOfAccounts(String chartOfAccounts) {
            this.chartOfAccounts = validatePattern(
                    chartOfAccounts,
                    CHART_OF_ACCOUNTS_PATTERN,
                    "Chart of accounts",
                    "([0-9]{2}){0,2}"
            );
            return this;
        }

        /**
         * Sets the optional DATEV industry-solution identifier.
         *
         * @param industrySolutionId zero to four digits; leading zeroes are preserved
         * @return this builder
         */
        public Builder industrySolutionId(String industrySolutionId) {
            this.industrySolutionId = validatePattern(
                    industrySolutionId,
                    INDUSTRY_SOLUTION_PATTERN,
                    "Industry-solution identifier",
                    "[0-9]{0,4}"
            );
            return this;
        }

        /**
         * Sets the optional issuing-application identifier.
         *
         * @param applicationInformation up to 16 non-control characters
         * @return this builder
         */
        public Builder applicationInformation(String applicationInformation) {
            this.applicationInformation = validateApplicationInformation(applicationInformation);
            return this;
        }

        /**
         * Validates all required and cross-field values and creates immutable metadata.
         *
         * @return configured Buchungsstapel v13 metadata
         * @throws IllegalStateException if a required value is missing or dates are inconsistent
         */
        public DatevMetadata build() {
            List<String> missing = new ArrayList<>();
            if (createdAt == null) {
                missing.add("createdAt");
            }
            if (advisorNumber == null) {
                missing.add("advisorNumber");
            }
            if (clientNumber == null) {
                missing.add("clientNumber");
            }
            if (fiscalYearStart == null) {
                missing.add("fiscalYearStart");
            }
            if (accountLength == null) {
                missing.add("accountLength");
            }
            if (periodStart == null) {
                missing.add("periodStart");
            }
            if (periodEnd == null) {
                missing.add("periodEnd");
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "Missing required DATEV metadata fields: " + String.join(", ", missing) + "."
                );
            }

            if (periodStart.isAfter(periodEnd)) {
                throw new IllegalStateException("Period start must not be after period end.");
            }
            if (fiscalYearStart.isAfter(periodStart)) {
                throw new IllegalStateException("Period start must not precede the fiscal-year start.");
            }
            if (!periodEnd.isBefore(fiscalYearStart.plusYears(1))) {
                throw new IllegalStateException(
                        "Period end must fall before the next fiscal-year start."
                );
            }

            return new DatevMetadata(this);
        }
    }
}
