import io.github.mrtyldr.datev.core.DatevCsv;
import io.github.mrtyldr.datev.core.DatevMetadata;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;

/**
 * Prints a real EXTF management record so the documentation can quote the library's own output
 * instead of a hand-typed approximation.
 */
public final class DumpHeaderRecord {

    private DumpHeaderRecord() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.println("headerVersion\t" + DatevMetadata.HEADER_VERSION);
        out.println("formatCategory\t" + DatevMetadata.FORMAT_CATEGORY);
        out.println("formatName\t" + DatevMetadata.FORMAT_NAME);
        out.println("formatVersion\t" + DatevMetadata.FORMAT_VERSION);
        out.println("legacyFormatVersion\t" + DatevMetadata.LEGACY_FORMAT_VERSION);
        out.println("delimiter\t" + DatevCsv.DELIMITER);
        out.println("charset\t" + DatevCsv.CHARSET);
        out.println("lineSeparator\t"
                + DatevCsv.LINE_SEPARATOR.replace("\r", "\\r").replace("\n", "\\n"));

        DatevMetadata v13 = sample(DatevMetadata.bookingBatchV13());
        DatevMetadata v12 = sample(DatevMetadata.bookingBatchV12());
        out.println("v13Record\t" + v13.toCsvLine());
        out.println("v12Record\t" + v12.toCsvLine());
        out.println("v13Fields\t" + (v13.toCsvLine().split(";", -1).length));
        out.println("v12Fields\t" + (v12.toCsvLine().split(";", -1).length));
    }

    private static DatevMetadata sample(DatevMetadata.Builder builder) {
        return builder
                .createdAt(LocalDateTime.of(2026, 8, 12, 9, 30, 0))
                .origin("RE")
                .exportedBy("my_application")
                .advisorNumber(1001)
                .clientNumber(1)
                .fiscalYearStart(LocalDate.of(2026, 1, 1))
                .accountLength(4)
                .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .description("August 2026")
                .currency(Currency.getInstance("EUR"))
                .applicationInformation("my-application")
                .build();
    }
}
