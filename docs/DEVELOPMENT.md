# Development workflow

Read [CONTRIBUTING.md](../CONTRIBUTING.md) for project orientation. This reference
covers build, verification and documentation maintenance.

## Build and test

The library targets Java 8 and has no runtime dependencies. The Maven build
and CI configuration are authoritative for tool versions and build rules.
CI currently verifies on JDK 8 and 25. Use a modern JDK (17 or newer) locally
to include the formatting check, which is enabled by a Maven profile.

```sh
mvn test             # behavioral tests
mvn verify           # tests, packaging, coverage and active formatting checks
mvn spotless:apply   # format Java using the checked-in Eclipse configuration
```

`verify` requires 100% line coverage through JaCoCo. Tests live in
`src/test/java/io/github/eagerseq`:

- `SeqTest` checks shared behavior through `Factory`, covering multiple
  implementations including interface defaults and stream delegation.
- `SeqReferenceTest` compares exhaustive short inputs against independent
  `java.util` references.
- `SeqStreamTest` covers stream-specific behavior, including lifecycle and
  laziness; `SourcesTest` exercises traversal machinery directly.
- `TraversalTest` checks exact consumption and
  independent references across mixed push, pull and split traversal.
- `PipelineReferenceTest` checks deterministic generated pipelines against
  JDK and list-based references. `GroupingReferenceTest` checks grouping and
  multiset representative identity with nulls and hash collisions.
- `CollectionViewTest` checks collection-view cardinality at the integer limit.
- `ApiShapeTest` checks API structure and test naming coverage.

Benchmarks are a separate Maven project. Follow [bench/README.md](../bench/README.md)
for building and running them and interpreting results. CI compiles them; timing
ratios are not CI assertions. Keep measurements and experiment reports there.

## Documentation

Edit `README.md` directly. It is the independently authored user introduction,
with examples and installation instructions; it is not generated from Javadoc.

The `Seq` class Javadoc provides a self-contained API overview, including
ownership, ordering, equality, performance and custom implementation guidance.
Individual method Javadoc defines the detailed contracts. Keep the README and
Javadoc consistent when behavior changes without duplicating their full text.
Architecture and implementation details belong in the contributor references
linked from [CONTRIBUTING.md](../CONTRIBUTING.md).

`mvn verify` builds the Javadoc JAR as well as checking the library. Inspect
the generated API documentation after changing Javadoc; repository-relative
Markdown links are not a substitute for Javadoc links to API contracts.

## Releases

Release configuration lives in the `release` Maven profile. It requires an
external signing key and Central Portal credentials; uploading with that
profile does not automatically publish. See the comments in `pom.xml` when
performing a release rather than duplicating credential/setup instructions here.
