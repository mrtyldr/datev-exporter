package io.github.mrtyldr.datev.benchmark;

import io.github.mrtyldr.datev.core.DatevMetadata;
import io.github.mrtyldr.datev.core.DatevValidationContext;
import io.github.mrtyldr.datev.core.DatevValidationMode;
import io.github.mrtyldr.datev.plain.DatevFile;
import io.github.mrtyldr.datev.plain.DatevStreamWriter;
import io.github.mrtyldr.datev.validation.DatevValidator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

/**
 * Compares complete, strictly validated EXTF v13 output through the retained and forward-only
 * exporter paths. Fixture construction and destination allocation stay outside the measured code.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@Threads(1)
public class CompleteExtfWritingBenchmark {

    /** Number of booking rows written in one benchmark invocation. */
    @Param({"1", "1000"})
    public int rowCount;

    private DatevMetadata metadata;
    private DatevValidator validator;
    private Map<String, Object> bookingRow;
    private byte[] expectedOutput;
    private ByteArrayOutputStream output;

    /** Prepares immutable inputs and verifies all measured paths produce identical bytes. */
    @Setup(Level.Trial)
    public void prepareTrial() {
        LocalDate periodStart = LocalDate.of(2026, 8, 1);
        LocalDate periodEnd = LocalDate.of(2026, 8, 31);
        metadata = DatevMetadata.bookingBatchV13()
                .createdAt(LocalDateTime.of(2026, 8, 12, 9, 30))
                .origin("RE")
                .exportedBy("benchmark")
                .advisorNumber(1001)
                .clientNumber(1)
                .fiscalYearStart(LocalDate.of(2026, 1, 1))
                .accountLength(4)
                .period(periodStart, periodEnd)
                .description("JMH benchmark")
                .applicationInformation("jmh-benchmark")
                .build();

        DatevValidationContext context = DatevValidationContext.builder()
                .accountLength(metadata.accountLength())
                .period(periodStart, periodEnd)
                .build();
        validator = DatevValidator.builder().context(context).build();

        bookingRow = Map.of(
                "Umsatz (ohne Soll/Haben-Kz)", "1250,00",
                "Soll/Haben-Kennzeichen", "S",
                "Konto", "1000",
                "Gegenkonto (ohne BU-Schlüssel)", "8400",
                "Belegdatum", "1208",
                "Buchungstext", "Müller; Beleg \"42\" €"
        );

        byte[] plainRetained = capture(this::writePlainRetainedFile);
        assertEquivalent(plainRetained, capture(this::writePlainForwardOnlyStream),
                "plain forward-only stream");
        assertEquivalent(plainRetained, capture(this::writeAdvancedRetainedFile),
                "advanced retained file");

        expectedOutput = plainRetained;
        output = new ByteArrayOutputStream(expectedOutput.length);
    }

    /** Reuses capacity while keeping destination reset work outside each measured invocation. */
    @Setup(Level.Invocation)
    public void resetDestination() {
        output.reset();
    }

    /** Verifies that the final invocation still emitted the expected complete file. */
    @TearDown(Level.Trial)
    public void verifyFinalOutput() {
        assertEquivalent(expectedOutput, output.toByteArray(), "final benchmark invocation");
    }

    /** Measures the plain exporter that retains rows before writing them. */
    @Benchmark
    public int plainRetainedFile() {
        return writePlainRetainedFile(output);
    }

    /** Measures the plain exporter that writes and discards each row as it is appended. */
    @Benchmark
    public int plainForwardOnlyStream() {
        return writePlainForwardOnlyStream(output);
    }

    /** Measures the advanced exporter with its built-in strict validation path. */
    @Benchmark
    public int advancedRetainedFile() {
        return writeAdvancedRetainedFile(output);
    }

    private int writePlainRetainedFile(ByteArrayOutputStream destination) {
        DatevFile file = DatevFile.builder()
                .metadata(metadata)
                .validator(validator)
                .build();
        for (int row = 0; row < rowCount; row++) {
            file.append(bookingRow);
        }
        file.writeTo(destination);
        return destination.size();
    }

    private int writePlainForwardOnlyStream(ByteArrayOutputStream destination) {
        try (DatevStreamWriter writer = DatevStreamWriter.builder()
                .metadata(metadata)
                .validator(validator)
                .build(destination)) {
            for (int row = 0; row < rowCount; row++) {
                writer.append(bookingRow);
            }
        }
        return destination.size();
    }

    private int writeAdvancedRetainedFile(ByteArrayOutputStream destination) {
        io.github.mrtyldr.datev.advanced.DatevFile file =
                io.github.mrtyldr.datev.advanced.DatevFile.builder()
                        .metadata(metadata)
                        .validationMode(DatevValidationMode.STRICT)
                        .build();
        for (int row = 0; row < rowCount; row++) {
            file.append(bookingRow);
        }
        file.writeTo(destination);
        return destination.size();
    }

    private byte[] capture(ToIntFunction<ByteArrayOutputStream> renderer) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        renderer.applyAsInt(captured);
        return captured.toByteArray();
    }

    private static void assertEquivalent(byte[] expected, byte[] actual, String path) {
        if (!Arrays.equals(expected, actual)) {
            throw new IllegalStateException(path + " produced different EXTF bytes: expected "
                    + expected.length + " bytes but received " + actual.length + '.');
        }
    }
}
