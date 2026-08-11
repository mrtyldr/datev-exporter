package io.github.mrtyldr.datev.core;

/** Logical types used by DATEV's Buchungsstapel field definition. */
public enum DatevFieldType {
    /** A quoted textual value. */
    TEXT("Text"),

    /** An unquoted numeric value. */
    NUMBER("Zahl"),

    /** A positive monetary value. */
    AMOUNT("Betrag"),

    /** A numeric account identifier. */
    ACCOUNT("Konto"),

    /** A DATEV-formatted date. */
    DATE("Datum");

    private final String checkerName;

    DatevFieldType(String checkerName) {
        this.checkerName = checkerName;
    }

    /**
     * Returns the name used in the official DATEV checker definition.
     *
     * @return checker type name
     */
    public String checkerName() {
        return checkerName;
    }
}
