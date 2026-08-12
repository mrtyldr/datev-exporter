# Internal JMH benchmarks

This module contains permanent performance benchmarks for the exporter write paths. It is part of
the Gradle build so the generated JMH harness is compiled by `check`, but benchmarks are not run on
shared CI hardware where timing results would be unstable.

The module is intentionally internal: it does not apply the publication conventions, is absent
from the BOM and release workflow, and its build fails if a Maven publishing plugin is applied.

Run the complete benchmark matrix locally:

```shell
./gradlew :datev-exporter-benchmarks:jmh
```

Run the reproducible maximum-file scenario with JMH's allocation profiler:

```shell
./gradlew --no-daemon :datev-exporter-benchmarks:jmhMaxRows
```

That task writes 99,999 rows per operation with one thread, two forks, three one-second warmup
iterations and five one-second measurement iterations per fork. It uses the Adoptium Java 17
toolchain and pins each fork to a pre-touched 1 GiB heap. It stores JSON output at
`datev-exporter-benchmarks/build/results/jmh/max-rows-gc.json`. Keep the machine idle while it runs
and compare results only when the JDK, hardware and command are equivalent.

For a quick runtime check covering every scenario and row-count parameter without warmup:

```shell
./gradlew :datev-exporter-benchmarks:jmhSmoke
```

`CompleteExtfWritingBenchmark` compares the plain retained file, the plain forward-only writer and
the advanced retained file at 1, 1,000 and the DATEV limit of 99,999 booking rows. Every path emits
the same complete v13 EXTF bytes with strict semantic validation. Fixture construction,
destination allocation and byte-parity checks are outside the measured interval. Results are
written to
`datev-exporter-benchmarks/build/results/jmh/results.txt`.
