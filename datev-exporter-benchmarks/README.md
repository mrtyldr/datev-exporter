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

For a quick runtime check covering every scenario and row-count parameter without warmup:

```shell
./gradlew :datev-exporter-benchmarks:jmhSmoke
```

`CompleteExtfWritingBenchmark` compares the plain retained file, the plain forward-only writer and
the advanced retained file. Every path emits the same complete v13 EXTF bytes with strict semantic
validation. Fixture construction, destination allocation and byte-parity checks are outside the
measured interval. Results are written to
`datev-exporter-benchmarks/build/results/jmh/results.txt`.
