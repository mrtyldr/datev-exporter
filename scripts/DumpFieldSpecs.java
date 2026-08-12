import io.github.mrtyldr.datev.core.DatevField;
import io.github.mrtyldr.datev.core.DatevFieldSpec;
import io.github.mrtyldr.datev.core.DatevSchema;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Dumps the canonical Buchungsstapel field table as TSV so the documentation site can be generated
 * from the library instead of a hand-maintained copy.
 *
 * <p>Run against the compiled core module:
 *
 * <pre>{@code
 * javac -d build/dump -cp datev-exporter-core/build/classes/java/main scripts/DumpFieldSpecs.java
 * java -cp build/dump:datev-exporter-core/build/classes/java/main DumpFieldSpecs > build/fields.tsv
 * }</pre>
 */
public final class DumpFieldSpecs {

    private DumpFieldSpecs() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        List<DatevFieldSpec> specs = DatevSchema.CURRENT_V13.fieldSpecs();
        out.println("number\theading\tconstant\ttype\tcheckerType\tmaxLength\tdecimals\trequired\tv12");
        for (DatevFieldSpec spec : specs) {
            DatevField field = DatevField.fromHeading(spec.canonicalKey()).orElseThrow(
                    () -> new IllegalStateException("no constant for " + spec.canonicalKey()));
            out.println(String.join("\t",
                    Integer.toString(spec.fieldNumber()),
                    spec.canonicalKey(),
                    field.name(),
                    spec.type().name(),
                    spec.type().checkerName(),
                    Integer.toString(spec.maxLength()),
                    Integer.toString(spec.decimalPlaces()),
                    Boolean.toString(spec.required()),
                    Boolean.toString(field.isPresentIn(DatevSchema.LEGACY_V12))));
        }
        out.println("#summary\tv13=" + specs.size()
                + "\tv12=" + DatevSchema.LEGACY_V12.fieldSpecs().size()
                + "\trequired=" + specs.stream().filter(DatevFieldSpec::required).count());
    }
}
