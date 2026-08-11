/**
 * Lean fixed-schema DATEV Buchungsstapel export backed by Univocity Parsers.
 *
 * <p>This package intentionally has no custom-heading API and no built-in semantic validator.
 * Supply a {@link java.util.function.BiConsumer} of format version and immutable row explicitly
 * when semantic field checks are desired.
 */
package io.github.mrtyldr.datev.univocity;
