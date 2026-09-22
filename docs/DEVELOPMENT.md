# Development workflow

Read [CONTRIBUTING.md](../CONTRIBUTING.md) for project orientation. This reference
covers build, verification and documentation maintenance.

## Build and test

The core library targets Java 8 and has no runtime dependencies. The Maven
reactor and CI configuration are authoritative for tool versions and build rules.
Run Maven from the repository root. CI currently verifies on JDK 8 and 25.
On JDK 8 the reactor contains the core library and Jackson 2 integration. On
JDK 17 or newer it also contains the Jackson 3 and benchmark modules and
enables the formatting check.

```sh
mvn test             # behavioral tests; also compiles benchmarks on JDK 17+
mvn verify           # tests, packaging, coverage and formatting on JDK 17+
mvn spotless:apply   # format all included Java modules on JDK 17+
```

`verify` requires 100% line coverage for production code in the core and both
integration modules through JaCoCo. Core tests live in
`seq/src/test/java/io/github/jancellor/seq`:

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

The Jackson modules test explicit and service-loaded registration, contextual
element types, null and empty values, invalid input, and default collection
serialization with both JSON and XML mappers. Additional tests cover nested
generics, polymorphic elements, content deserializers and converters, null
policies, configured coercions, and ownership of the decoded arrays.

The benchmark harness is a reactor module on JDK 17 or newer. Follow
[seq-bench/README.md](../seq-bench/README.md) for building and running it and
interpreting results. CI packages it but does not run JMH; timing ratios are not
CI assertions. Keep measurements and experiment reports there.

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

Release configuration lives in the root `release` Maven profile. It requires
an external signing key and Central Portal credentials; uploading with that
profile does not automatically publish. The parent POM, core library and
Jackson integration children are released, while `seq-bench` is excluded from
installation, signing and deployment. See the comments in `pom.xml` when
performing a release rather than duplicating credential/setup instructions
here.
