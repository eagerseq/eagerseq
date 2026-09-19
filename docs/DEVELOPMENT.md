# Development workflow

Read [CONTRIBUTING.md](../CONTRIBUTING.md) for project orientation. This reference
covers build, verification and documentation maintenance.

## Build and test

The library targets Java 8 and has no runtime dependencies. The Maven build
and CI configuration are authoritative for tool versions and build rules.
CI currently verifies on JDK 8 and 25. Use a modern JDK (17 or newer) locally
to include the formatting check, which is enabled by a Maven profile.

```sh
mvn test             # behavioural tests
mvn verify           # tests, packaging, coverage and active formatting checks
mvn spotless:apply   # format Java using the checked-in Eclipse configuration
```

`verify` requires 100% line coverage through JaCoCo. Tests live in
`src/test/java/io/github/eagerseq`:

- `SeqTest` checks shared behaviour through `Factory`, covering multiple
  implementations including interface defaults and stream delegation.
- `SeqReferenceTest` compares exhaustive short inputs against independent
  `java.util` references.
- `SeqStreamTest` covers stream-specific behaviour, including lifecycle and
  laziness; `SourcesTest` exercises traversal machinery directly.
- `ApiShapeTest` checks API structure and test naming coverage.

Benchmarks are a separate Maven project. Follow [bench/README.md](../bench/README.md)
for building and running them and interpreting results. CI compiles them; timing
ratios are not CI assertions. Keep measurements and experiment reports there.

## Updating the README

To change the user introduction, edit the `Seq` class comment (installation
boilerplate lives in `ReadmeGenerator`), then regenerate:

```sh
mvn test-compile
java -cp target/test-classes:target/classes io.github.eagerseq.ReadmeGenerator
mvn -Dtest=ReadmeTest test
```

## Releases

Release configuration lives in the `release` Maven profile. It requires an
external signing key and Central Portal credentials; uploading with that
profile does not automatically publish. See the comments in `pom.xml` when
performing a release rather than duplicating credential/setup instructions here.
