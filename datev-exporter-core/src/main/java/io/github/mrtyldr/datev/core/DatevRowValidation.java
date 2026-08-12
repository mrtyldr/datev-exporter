package io.github.mrtyldr.datev.core;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The single semantic validation engine shared by every {@code datev-exporter} module.
 *
 * <p>The engine receives logical, unquoted values. CSV parsing, quoting and escaping remain the
 * responsibility of the calling exporter. Rules are resolved through stable canonical keys, so a
 * renamed or reordered official column keeps its field semantics.
 *
 * <p>These rules cover deterministic technical constraints available without a DATEV client data
 * set. A real DATEV checker or a test import remains the compatibility authority for master-data
 * and tax-law rules.
 *
 * @see <a href="https://developer.datev.de/de/file-format/details/datev-format/format-description/booking-batch">
 *     Official Buchungsstapel field description</a>
 */
public final class DatevRowValidation {
    private static final String AMOUNT = "Umsatz (ohne Soll/Haben-Kz)";
    private static final String SIDE = "Soll/Haben-Kennzeichen";
    private static final String TURNOVER_CURRENCY = "WKZ Umsatz";
    private static final String RATE = "Kurs";
    private static final String BASE_AMOUNT = "Basis-Umsatz";
    private static final String BASE_CURRENCY = "WKZ Basis-Umsatz";
    private static final String DISCOUNT = "Skonto";
    private static final String ACCOUNT = "Konto";
    private static final String CONTRA_ACCOUNT = "Gegenkonto (ohne BU-Schlüssel)";
    private static final String BU_KEY = "BU-Schlüssel";
    private static final String DOCUMENT_DATE = "Belegdatum";
    private static final String DOCUMENT_FIELD_1 = "Belegfeld 1";
    private static final String DOCUMENT_FIELD_2 = "Belegfeld 2";
    private static final String REVENUE_ACCOUNT = "Erlöskonto (Anzahlungen)";
    private static final String DEVIATING_DISCOUNT_ACCOUNT = "Abw. Skontokonto";

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern DOCUMENT_FIELD = Pattern.compile("[A-Za-z0-9_$&%*+\\-/]*");
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]*");
    private static final Pattern WORD_AND_SPACE = Pattern.compile("[A-Za-z0-9_ ]*");
    private static final Pattern UPPERCASE_TWO = Pattern.compile("[A-Z]{2}");
    private static final Pattern UPPERCASE_THREE = Pattern.compile("[A-Z]{3}");
    private static final DateTimeFormatter FULL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMuuuu", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> ISO_COUNTRIES = isoCountries();
    private static final Set<String> ZERO_ONE_FIELDS = Set.of(
            "Postensperre",
            "Zinssperre",
            "Skontosperre",
            "Kennzeichen SoBil-Buchung",
            "Festschreibung",
            "Generalumkehr (GU)"
    );
    private static final Set<String> FULL_DATE_FIELDS = Set.of(
            "Zugeordnete Fälligkeit",
            "KOST-Datum",
            "Postensperre bis",
            "Leistungsdatum",
            "Datum Zuord. Steuerperiode",
            "Fälligkeit"
    );
    private static final Set<String> NONZERO_INTEGER_FIELDS = Set.of(
            "Sachverhalt L+L",
            "Funktionsergänzung L+L",
            "Sachverhalt L+L (Anzahlungen)"
    );
    private static final Set<String> ACCOUNT_FIELDS = Set.of(
            ACCOUNT,
            CONTRA_ACCOUNT,
            REVENUE_ACCOUNT,
            DEVIATING_DISCOUNT_ACCOUNT
    );
    private static final Set<String> SPECIFIC_RULE_KEYS = specificRuleKeys();

    private DatevRowValidation() {
    }

    /**
     * Validates one row and returns every discovered error in deterministic field order.
     *
     * <p>Unknown custom keys are ignored. Required-field and cross-field dependency rules apply
     * only when {@code officialSchema} is {@code true}; known official fields in a custom schema
     * still receive field-level validation.
     *
     * @param canonicalKeys stable canonical keys in the row's output order
     * @param values logical, unquoted cell values in the same order
     * @param mode validation depth
     * @param context optional metadata-dependent constraints, never {@code null}
     * @param officialSchema whether the keys form a complete official v12 or v13 schema
     * @return immutable validation errors ordered by DATEV field number
     * @throws IllegalArgumentException if key and value widths differ or keys are duplicated
     */
    public static List<DatevValidationError> validate(
            List<String> canonicalKeys,
            List<String> values,
            DatevValidationMode mode,
            DatevValidationContext context,
            boolean officialSchema
    ) {
        Objects.requireNonNull(canonicalKeys, "canonicalKeys");
        requireCommonArguments(canonicalKeys.size(), values, mode, context);
        // Runs before the mode check because indexing also rejects null and duplicated keys, which
        // every mode reports. The list is caller-owned and may be mutable, so it is re-indexed on
        // every call.
        return validateIndexed(canonicalKeys, indexCanonicalKeys(canonicalKeys), values, mode,
                context, officialSchema);
    }

    /**
     * Validates one row whose canonical keys have already been indexed by their immutable owner.
     *
     * <p>Skipping {@code indexCanonicalKeys} also skips its null and duplicate key rejection. That
     * is safe precisely because the only callers are {@link DatevSchema} and {@link DatevHeader},
     * which build their index maps once during construction and reject null, duplicated and
     * colliding keys there; a row can therefore never reach this method with keys that the public
     * {@code List}-based overload would have rejected. Any future caller must uphold the same
     * invariant.
     *
     * @param canonicalKeys stable canonical keys in the row's output order
     * @param canonicalKeyIndexes the owner's immutable key-to-index map for exactly those keys
     * @param values logical, unquoted cell values in the same order
     * @param mode validation depth
     * @param context optional metadata-dependent constraints, never {@code null}
     * @param officialSchema whether the keys form a complete official v12 or v13 schema
     * @return immutable validation errors ordered by DATEV field number
     */
    static List<DatevValidationError> validate(
            List<String> canonicalKeys,
            Map<String, Integer> canonicalKeyIndexes,
            List<String> values,
            DatevValidationMode mode,
            DatevValidationContext context,
            boolean officialSchema
    ) {
        Objects.requireNonNull(canonicalKeys, "canonicalKeys");
        Objects.requireNonNull(canonicalKeyIndexes, "canonicalKeyIndexes");
        requireCommonArguments(canonicalKeys.size(), values, mode, context);
        return validateIndexed(canonicalKeys, canonicalKeyIndexes, values, mode, context,
                officialSchema);
    }

    private static void requireCommonArguments(
            int keyCount,
            List<String> values,
            DatevValidationMode mode,
            DatevValidationContext context
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(context, "context");
        if (keyCount != values.size()) {
            throw new IllegalArgumentException(
                    "Canonical key count " + keyCount + " does not match value count "
                            + values.size() + '.');
        }
    }

    private static List<DatevValidationError> validateIndexed(
            List<String> canonicalKeys,
            Map<String, Integer> indexes,
            List<String> values,
            DatevValidationMode mode,
            DatevValidationContext context,
            boolean officialSchema
    ) {
        if (mode == DatevValidationMode.NONE) {
            return List.of();
        }

        var errors = new ArrayList<DatevValidationError>();
        for (int index = 0; index < canonicalKeys.size(); index++) {
            String canonicalKey = canonicalKeys.get(index);
            DatevFieldSpec spec = DatevFieldSpecs.findOrNull(canonicalKey);
            if (spec == null) {
                continue;
            }

            String value = values.get(index);
            if (isEmpty(value)) {
                if (mode == DatevValidationMode.STRICT && officialSchema && spec.required()) {
                    errors.add(error(spec, DatevValidationError.Code.REQUIRED_FIELD,
                            "DATEV field #" + spec.fieldNumber() + " '" + canonicalKey
                                    + "' is required."));
                }
                continue;
            }
            validateField(spec, value, context, errors);
        }

        if (mode == DatevValidationMode.STRICT && officialSchema) {
            validateDependencies(values, indexes, errors);
        }
        errors.sort(Comparator.comparingInt(DatevValidationError::fieldNumber));
        return List.copyOf(errors);
    }

    private static void validateField(
            DatevFieldSpec spec,
            String value,
            DatevValidationContext context,
            List<DatevValidationError> errors
    ) {
        // One pass replaces the former control-character scan plus a per-field CharsetEncoder
        // allocation. The reported precedence stays control character first, unmappable second.
        // This uses the package-private inspection rather than DatevCsv.requireExportable because
        // it must report two distinct errors instead of throwing on the first violation.
        DatevCsv.OutputSafety outputSafety = DatevCsv.inspectOutputSafety(value);
        if (outputSafety == DatevCsv.OutputSafety.CONTROL_CHARACTER) {
            errors.add(formatError(spec,
                    "must not contain control or line-separator characters"));
            return;
        }
        if (outputSafety == DatevCsv.OutputSafety.UNMAPPABLE_CHARACTER) {
            errors.add(error(spec, DatevValidationError.Code.UNMAPPABLE_CHARACTER,
                    "DATEV field #" + spec.fieldNumber() + " '" + spec.canonicalKey()
                            + "' contains a character that Windows-1252 cannot encode."));
            return;
        }

        int initialErrorCount = errors.size();
        validateSpecificField(spec, value, context, errors);
        if (errors.size() != initialErrorCount || SPECIFIC_RULE_KEYS.contains(spec.canonicalKey())) {
            return;
        }

        switch (spec.type()) {
            case TEXT -> validateTextLength(spec, value, errors);
            case NUMBER -> validateGenericNumber(spec, value, errors);
            case AMOUNT -> validateAmount(spec, value, errors);
            case ACCOUNT -> validateAccount(spec, value, context, errors);
            case DATE -> validateFullDate(spec, value, errors);
        }
    }

    private static void validateSpecificField(
            DatevFieldSpec spec,
            String value,
            DatevValidationContext context,
            List<DatevValidationError> errors
    ) {
        String key = spec.canonicalKey();
        if (AMOUNT.equals(key)) {
            validateAmount(spec, value, errors);
        } else if (BASE_AMOUNT.equals(key) || DISCOUNT.equals(key)) {
            validateFixedScaleAmount(spec, value, errors);
        } else if (SIDE.equals(key)) {
            requireOneOf(spec, value, errors, "S", "H");
        } else if (TURNOVER_CURRENCY.equals(key) || BASE_CURRENCY.equals(key)) {
            validateCurrency(spec, value, errors);
        } else if (RATE.equals(key)) {
            validateRate(spec, value, errors);
        } else if (ACCOUNT_FIELDS.contains(key)) {
            validateAccount(spec, value, context, errors);
        } else if (BU_KEY.equals(key)) {
            requireDigits(spec, value, 1, 4, errors);
        } else if (DOCUMENT_DATE.equals(key)) {
            validateDocumentDate(spec, value, context, errors);
        } else if (DOCUMENT_FIELD_1.equals(key) || DOCUMENT_FIELD_2.equals(key)) {
            validateRestrictedText(spec, value, DOCUMENT_FIELD, errors);
        } else if ("Diverse Adressnummer".equals(key) || "Forderungsart".equals(key)) {
            validateRestrictedText(spec, value, WORD, errors);
        } else if ("KOST1 - Kostenstelle".equals(key) || "KOST2 - Kostenstelle".equals(key)) {
            validateRestrictedText(spec, value, WORD_AND_SPACE, errors);
        } else if ("Sachverhalt".equals(key)) {
            requireOneOf(spec, value, errors, "31", "40");
        } else if ("Abw. Versteuerungsart".equals(key)) {
            requireOneOf(spec, value, errors, "I", "K", "P", "S");
        } else if ("Veranlagungsjahr".equals(key)) {
            if (!isAsciiDigits(value, 4) || value.charAt(0) != '2' || value.charAt(1) != '0') {
                errors.add(formatError(spec,
                        "must use a four-digit year from 2000 through 2099"));
            }
        } else if (FULL_DATE_FIELDS.contains(key)) {
            validateFullDate(spec, value, errors);
        } else if ("Zahlweise".equals(key)) {
            requireOneOf(spec, value, errors, "1", "2", "3");
        } else if ("Skontotyp".equals(key)) {
            requireOneOf(spec, value, errors, "1", "2");
        } else if ("Buchungstyp".equals(key)) {
            requireOneOf(spec, value, errors, "AA", "AG", "AV", "SR", "SU", "SG", "SO");
        } else if ("EU-Land (Anzahlungen)".equals(key) || "Land".equals(key)) {
            validateCountry(spec, value, errors);
        } else if ("Herkunft-Kz".equals(key)) {
            if (!UPPERCASE_TWO.matcher(value).matches()) {
                errors.add(formatError(spec,
                        "must contain exactly two uppercase ASCII letters"));
            }
        } else if (NONZERO_INTEGER_FIELDS.contains(key)) {
            validateNonzeroInteger(spec, value, errors);
        } else if ("Beteiligtennummer".equals(key)) {
            requireDigits(spec, value, 4, 4, errors);
        } else if (ZERO_ONE_FIELDS.contains(key)) {
            requireOneOf(spec, value, errors, "0", "1");
        } else if ("BVV-Position".equals(key)) {
            requireOneOf(spec, value, errors, "1", "2", "3", "4", "5");
        }
    }

    private static Set<String> specificRuleKeys() {
        var keys = new HashSet<String>();
        keys.add(AMOUNT);
        keys.add(BASE_AMOUNT);
        keys.add(DISCOUNT);
        keys.add(SIDE);
        keys.add(TURNOVER_CURRENCY);
        keys.add(BASE_CURRENCY);
        keys.add(RATE);
        keys.addAll(ACCOUNT_FIELDS);
        keys.add(BU_KEY);
        keys.add(DOCUMENT_DATE);
        keys.add(DOCUMENT_FIELD_1);
        keys.add(DOCUMENT_FIELD_2);
        keys.add("Diverse Adressnummer");
        keys.add("Forderungsart");
        keys.add("KOST1 - Kostenstelle");
        keys.add("KOST2 - Kostenstelle");
        keys.add("Sachverhalt");
        keys.add("Abw. Versteuerungsart");
        keys.add("Veranlagungsjahr");
        keys.addAll(FULL_DATE_FIELDS);
        keys.add("Zahlweise");
        keys.add("Skontotyp");
        keys.add("Buchungstyp");
        keys.add("EU-Land (Anzahlungen)");
        keys.add("Land");
        keys.add("Herkunft-Kz");
        keys.addAll(NONZERO_INTEGER_FIELDS);
        keys.add("Beteiligtennummer");
        keys.addAll(ZERO_ONE_FIELDS);
        keys.add("BVV-Position");
        return Set.copyOf(keys);
    }

    private static void validateTextLength(
            DatevFieldSpec spec,
            String value,
            List<DatevValidationError> errors
    ) {
        if (value.codePointCount(0, value.length()) > spec.maxLength()) {
            errors.add(error(spec, DatevValidationError.Code.TEXT_TOO_LONG,
                    "DATEV field #" + spec.fieldNumber() + " '" + spec.canonicalKey()
                            + "' must contain at most " + spec.maxLength() + " characters."));
        }
    }

    private static void validateRestrictedText(
            DatevFieldSpec spec,
            String value,
            Pattern allowedCharacters,
            List<DatevValidationError> errors
    ) {
        validateTextLength(spec, value, errors);
        if (!allowedCharacters.matcher(value).matches()) {
            errors.add(formatError(spec, "contains characters that DATEV does not permit"));
        }
    }

    private static void validateGenericNumber(
            DatevFieldSpec spec,
            String value,
            List<DatevValidationError> errors
    ) {
        NumericParts parts = numericParts(value);
        if (parts == null) {
            errors.add(formatError(spec,
                    "must be an unsigned number using ',' as decimal separator"));
            return;
        }
        if (parts.integralDigits() > spec.maxLength()
                || parts.fractionalDigits() > spec.decimalPlaces()
                || spec.decimalPlaces() == 0 && parts.fractionalDigits() != 0) {
            errors.add(rangeError(spec, "supports at most " + spec.maxLength()
                    + " integral and " + spec.decimalPlaces() + " fractional digits"));
        }
    }

    private static void validateAmount(
            DatevFieldSpec spec,
            String value,
            List<DatevValidationError> errors
    ) {
        NumericParts parts = numericParts(value);
        if (parts == null || parts.fractionalDigits() != 0
                && parts.fractionalDigits() != spec.decimalPlaces()) {
            errors.add(formatError(spec,
                    "must be a positive amount with either no decimals or exactly "
                            + spec.decimalPlaces() + " decimal places"));
            return;
        }
        if (parts.integralDigits() > spec.maxLength() || !parts.nonZero()) {
            errors.add(rangeError(spec, "must be greater than zero with at most "
                    + spec.maxLength() + " integral digits"));
        }
    }

    private static void validateFixedScaleAmount(
            DatevFieldSpec spec,
            String value,
            List<DatevValidationError> errors
    ) {
        NumericParts parts = numericParts(value);
        if (parts == null || parts.fractionalDigits() != spec.decimalPlaces()) {
            errors.add(formatError(spec, "must be a positive amount with exactly "
                    + spec.decimalPlaces() + " decimal places"));
            return;
        }
        if (parts.integralDigits() > spec.maxLength() || !parts.nonZero()) {
            errors.add(rangeError(spec, "must be greater than zero with at most "
                    + spec.maxLength() + " integral digits"));
        }
    }

    private static void validateRate(
            DatevFieldSpec spec,
            String value,
            List<DatevValidationError> errors
    ) {
        NumericParts parts = numericParts(value);
        if (parts == null || parts.fractionalDigits() < 2 || parts.fractionalDigits() > 6) {
            errors.add(formatError(spec, "must contain between two and six decimal places"));
            return;
        }
        if (parts.integralDigits() > 4 || !parts.nonZero()) {
            errors.add(rangeError(spec,
                    "must be greater than zero with at most four integral digits"));
        }
    }

    private static void validateAccount(
            DatevFieldSpec spec,
            String value,
            DatevValidationContext context,
            List<DatevValidationError> errors
    ) {
        if (!DIGITS.matcher(value).matches()) {
            errors.add(formatError(spec, "must contain decimal digits only"));
            return;
        }
        String key = spec.canonicalKey();
        boolean zeroMeansEmpty = REVENUE_ACCOUNT.equals(key)
                || DEVIATING_DISCOUNT_ACCOUNT.equals(key);
        if (value.codePoints().allMatch(character -> character == '0')) {
            if (zeroMeansEmpty) {
                return;
            }
            errors.add(rangeError(spec, "must not be zero"));
            return;
        }

        int minimum = REVENUE_ACCOUNT.equals(key) ? 4 : 1;
        if (value.length() < minimum) {
            errors.add(rangeError(spec, "must contain at least " + minimum + " digits"));
            return;
        }

        int maximum = spec.maxLength();
        Integer accountLength = context.nullableAccountLength();
        if (accountLength != null) {
            boolean mayBePersonAccount = ACCOUNT.equals(key) || CONTRA_ACCOUNT.equals(key);
            maximum = Math.min(maximum, accountLength + (mayBePersonAccount ? 1 : 0));
        }
        if (value.length() > maximum) {
            errors.add(rangeError(spec, "must contain at most " + maximum + " digits"));
        }
    }

    private static void validateCurrency(
            DatevFieldSpec spec,
            String value,
            List<DatevValidationError> errors
    ) {
        if (!UPPERCASE_THREE.matcher(value).matches()) {
            errors.add(formatError(spec,
                    "must contain an uppercase three-letter ISO 4217 code"));
            return;
        }
        try {
            Currency.getInstance(value);
        } catch (IllegalArgumentException exception) {
            errors.add(formatError(spec, "is not a recognized ISO 4217 currency code"));
        }
    }

    private static void validateCountry(
            DatevFieldSpec spec,
            String value,
            List<DatevValidationError> errors
    ) {
        if (!UPPERCASE_TWO.matcher(value).matches() || !ISO_COUNTRIES.contains(value)) {
            errors.add(formatError(spec,
                    "must be an ISO 3166-1 alpha-2 code (DATEV also accepts EL and XI)"));
        }
    }

    private static void validateDocumentDate(
            DatevFieldSpec spec,
            String value,
            DatevValidationContext context,
            List<DatevValidationError> errors
    ) {
        if (!isAsciiDigits(value, 4)) {
            errors.add(formatError(spec, "must use DATEV's DDMM representation"));
            return;
        }

        MonthDay monthDay;
        try {
            monthDay = MonthDay.of(Integer.parseInt(value.substring(2, 4)),
                    Integer.parseInt(value.substring(0, 2)));
        } catch (DateTimeException exception) {
            errors.add(formatError(spec, "is not a valid day and month"));
            return;
        }

        LocalDate periodStart = context.nullablePeriodStart();
        LocalDate periodEnd = context.nullablePeriodEnd();
        if (periodStart != null && !occursInPeriod(monthDay, periodStart, periodEnd)) {
            errors.add(rangeError(spec,
                    "does not occur within the configured posting period"));
        }
    }

    private static void validateFullDate(
            DatevFieldSpec spec,
            String value,
            List<DatevValidationError> errors
    ) {
        if (!isAsciiDigits(value, 8)) {
            errors.add(formatError(spec, "must use DATEV's DDMMYYYY representation"));
            return;
        }
        try {
            LocalDate date = LocalDate.parse(value, FULL_DATE_FORMATTER);
            if (date.getYear() < 2000 || date.getYear() > 2099) {
                errors.add(rangeError(spec, "year must be between 2000 and 2099"));
            }
        } catch (DateTimeParseException exception) {
            errors.add(formatError(spec, "is not a valid calendar date"));
        }
    }

    private static void validateNonzeroInteger(
            DatevFieldSpec spec,
            String value,
            List<DatevValidationError> errors
    ) {
        if (!DIGITS.matcher(value).matches()) {
            errors.add(formatError(spec, "must contain decimal digits only"));
        } else if (value.length() > spec.maxLength()
                || value.codePoints().allMatch(character -> character == '0')) {
            errors.add(rangeError(spec, "must be non-zero with at most "
                    + spec.maxLength() + " digits"));
        }
    }

    private static void requireDigits(
            DatevFieldSpec spec,
            String value,
            int minimum,
            int maximum,
            List<DatevValidationError> errors
    ) {
        if (!DIGITS.matcher(value).matches()
                || value.length() < minimum || value.length() > maximum) {
            errors.add(formatError(spec, "must contain "
                    + (minimum == maximum ? "exactly " + minimum
                    : "between " + minimum + " and " + maximum) + " digits"));
        }
    }

    private static void requireOneOf(
            DatevFieldSpec spec,
            String value,
            List<DatevValidationError> errors,
            String... allowed
    ) {
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return;
            }
        }
        errors.add(formatError(spec, "must be one of " + String.join(", ", allowed)));
    }

    private static void validateDependencies(
            List<String> values,
            Map<String, Integer> indexes,
            List<DatevValidationError> errors
    ) {
        requirePair(BASE_AMOUNT, BASE_CURRENCY, values, indexes, errors);
        for (int pair = 1; pair <= 8; pair++) {
            requirePair("Beleginfo - Art " + pair, "Beleginfo - Inhalt " + pair,
                    values, indexes, errors);
        }
        for (int pair = 1; pair <= 20; pair++) {
            requirePair("Zusatzinformation - Art " + pair,
                    "Zusatzinformation- Inhalt " + pair, values, indexes, errors);
        }
    }

    private static void requirePair(
            String firstKey,
            String secondKey,
            List<String> values,
            Map<String, Integer> indexes,
            List<DatevValidationError> errors
    ) {
        boolean firstSet = isSet(firstKey, values, indexes);
        boolean secondSet = isSet(secondKey, values, indexes);
        if (firstSet && !secondSet) {
            addDependencyError(secondKey, firstKey, indexes, errors);
        } else if (secondSet && !firstSet) {
            addDependencyError(firstKey, secondKey, indexes, errors);
        }
    }

    private static void addDependencyError(
            String missingKey,
            String suppliedKey,
            Map<String, Integer> indexes,
            List<DatevValidationError> errors
    ) {
        if (!indexes.containsKey(missingKey)) {
            return;
        }
        DatevFieldSpec spec = DatevFieldSpecs.find(missingKey).orElseThrow();
        errors.add(error(spec, DatevValidationError.Code.DEPENDENT_FIELD_MISSING,
                "DATEV field #" + spec.fieldNumber() + " '" + missingKey
                        + "' is required when '" + suppliedKey + "' is supplied."));
    }

    private static boolean isSet(String key, List<String> values, Map<String, Integer> indexes) {
        Integer index = indexes.get(key);
        return index != null && !isEmpty(values.get(index));
    }

    /**
     * Indexes a caller-owned key list, rejecting null and duplicated keys.
     *
     * <p>Runs on every call: the list may be mutable, so a caller that edits it between calls must
     * still get correct results. Callers holding an immutable, already indexed header avoid this
     * cost through the package-private overload above.
     *
     * <p>The two official schemas are the exception. Each hands out one immutable header list for
     * the lifetime of the JVM and indexed it once when its enum constant was created, so a caller
     * passing {@code schema.headers()} is recognized by reference and reuses that map. Skipping the
     * null and duplicate rejection is safe for exactly those two lists, because
     * {@link DatevSchema} rejected both when it built the map. Any other list, including a mutable
     * copy of an official one, is re-indexed.
     */
    private static Map<String, Integer> indexCanonicalKeys(List<String> canonicalKeys) {
        if (canonicalKeys == DatevSchema.CURRENT_V13.headers()) {
            return DatevSchema.CURRENT_V13.headerIndexes();
        }
        if (canonicalKeys == DatevSchema.LEGACY_V12.headers()) {
            return DatevSchema.LEGACY_V12.headerIndexes();
        }
        var result = new HashMap<String, Integer>(canonicalKeys.size());
        for (int index = 0; index < canonicalKeys.size(); index++) {
            String key = Objects.requireNonNull(canonicalKeys.get(index),
                    "canonicalKeys must not contain null");
            if (result.putIfAbsent(key, index) != null) {
                throw new IllegalArgumentException("Duplicate canonical key: " + key);
            }
        }
        return result;
    }

    private static boolean occursInPeriod(MonthDay monthDay, LocalDate start, LocalDate end) {
        for (int year = start.getYear(); year <= end.getYear(); year++) {
            try {
                LocalDate candidate = LocalDate.of(year, monthDay.getMonthValue(),
                        monthDay.getDayOfMonth());
                if (!candidate.isBefore(start) && !candidate.isAfter(end)) {
                    return true;
                }
            } catch (DateTimeException ignored) {
                // February 29 may still occur in another year covered by the period.
            }
        }
        return false;
    }

    private static NumericParts numericParts(String value) {
        int comma = value.indexOf(',');
        if (comma != value.lastIndexOf(',')) {
            return null;
        }
        String integral = comma < 0 ? value : value.substring(0, comma);
        String fractional = comma < 0 ? "" : value.substring(comma + 1);
        if (integral.isEmpty() || !DIGITS.matcher(integral).matches()
                || comma >= 0 && (fractional.isEmpty()
                || !DIGITS.matcher(fractional).matches())) {
            return null;
        }
        boolean nonZero = value.codePoints()
                .anyMatch(character -> character >= '1' && character <= '9');
        return new NumericParts(integral.length(), fractional.length(), nonZero);
    }

    private static DatevValidationError formatError(DatevFieldSpec spec, String rule) {
        return error(spec, DatevValidationError.Code.INVALID_FORMAT,
                "DATEV field #" + spec.fieldNumber() + " '" + spec.canonicalKey()
                        + "' " + rule + '.');
    }

    private static DatevValidationError rangeError(DatevFieldSpec spec, String rule) {
        return error(spec, DatevValidationError.Code.VALUE_OUT_OF_RANGE,
                "DATEV field #" + spec.fieldNumber() + " '" + spec.canonicalKey()
                        + "' " + rule + '.');
    }

    private static DatevValidationError error(
            DatevFieldSpec spec,
            DatevValidationError.Code code,
            String message
    ) {
        return new DatevValidationError(code, spec.fieldNumber(), spec.canonicalKey(), message);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * Returns whether a value is exactly {@code length} ASCII digits.
     *
     * <p>Replaces {@code String.matches("[0-9]{n}")} on the row hot path, which compiled a fresh
     * {@link Pattern} for every cell. The accepted range is ASCII {@code 0}-{@code 9} only,
     * exactly like {@code [0-9]} without {@code UNICODE_CHARACTER_CLASS}, so digits such as
     * Arabic-Indic ones stay rejected.
     */
    private static boolean isAsciiDigits(String value, int length) {
        if (value.length() != length) {
            return false;
        }
        for (int index = 0; index < length; index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static Set<String> isoCountries() {
        var result = new HashSet<>(List.of(Locale.getISOCountries()));
        result.add("EL");
        result.add("XI");
        return Set.copyOf(result);
    }

    private record NumericParts(int integralDigits, int fractionalDigits, boolean nonZero) {
    }
}
