/**
 * Buffered and forward-only serialization of the fixed DATEV Buchungsstapel schemas without
 * third-party runtime dependencies.
 *
 * <p>{@link io.github.mrtyldr.datev.plain.DatevFile} and
 * {@link io.github.mrtyldr.datev.plain.DatevStreamWriter} emit a complete EXTF file when configured
 * with {@link io.github.mrtyldr.datev.core.DatevMetadata}. Without metadata they emit the official
 * column heading and booking rows only.
 */
package io.github.mrtyldr.datev.plain;
