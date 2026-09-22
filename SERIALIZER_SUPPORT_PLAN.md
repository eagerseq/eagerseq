# Serialization support plan

> Status: implemented for Jackson 2 and Jackson 3. The proposed JSON-B module
> was rejected after implementation testing showed that standard global
> registration cannot portably select one deserializer for arbitrary
> parameterized `Seq<E>` targets. The settled contract and rationale are in
> [docs/DESIGN.md](docs/DESIGN.md#serialization); the JSON-B sections below are
> retained as the original investigation proposal, not current project policy.

## Objective

Add optional, dependency-isolated support for deserializing `Seq<E>` with
Jackson 2, Jackson 3 and Jakarta JSON Binding (JSON-B). A sequence has one
portable serialized meaning: an ordered array of its elements. Deserialization
always creates a shallow snapshot; it never attempts to preserve `ArraySeq`,
`CollectionSeq`, `viewOf(...)` backing relationships or another concrete
implementation detail.

The core `seq` artifact must remain Java 8 compatible and have no serialization
framework dependencies or framework annotations.

## Published artifacts

Publish these modules under the existing `io.github.jancellor.seq` group and
the same project version as `seq`:

| Artifact | Java release | Framework dependency |
|---|---:|---|
| `seq` | 8 | None; unchanged core library |
| `seq-jackson2` | 8 | Jackson 2.21.6 |
| `seq-jackson3` | 17 | Jackson 3.1.5 |
| `seq-jsonb2` | 8 | JSON-B API 2.0.0, `provided` |

Jackson 2.21 and 3.1 are the selected LTS lines. Use only the fixed versions
above: do not introduce version ranges, compatibility-floor profiles or a
multi-version test matrix. An application can still select another patch or
minor through an explicit dependency or dependency management in the normal
Maven manner.

The repository is already a reactor with `seq` as a child module. Add
`seq-jackson2` and `seq-jsonb2` to the unconditional module list. Add
`seq-jackson3` to the existing `jdk17-or-newer` profile so that the Java 8
reactor remains buildable; give that module an explicit compiler release of
17. It must be included in JDK 17+ releases. Keep `seq-bench` as the only
artifact excluded from deployment.

Use distinct implementation packages so Jackson 2 and 3 integrations can
coexist:

```text
io.github.jancellor.seq.jackson2
io.github.jancellor.seq.jackson3
io.github.jancellor.seq.jsonb2
```

Give each artifact an appropriate stable `Automatic-Module-Name` derived from
those package names.

## Shared contract

All integrations must follow these rules:

- Serialize a `Seq<E>` exactly like an ordinary ordered collection/array.
- Deserialize an array to a snapshot, using a public core construction path
  such as `Seq.copyOf(...)` or `Seq.builder()`.
- Preserve the contextual generic element type. For example, `Seq<Person>`
  must contain `Person` instances rather than untyped maps.
- Preserve encounter order, empty sequences and `null` elements.
- Follow the host framework's ordinary handling of a `null` sequence value;
  do not reinterpret it as an empty sequence.
- Reject incompatible non-array input in the framework's conventional way.
- Do not expose, inspect or depend on package-private concrete Seq classes.
- Do not serialize implementation names, backing collections, view status or
  type metadata beyond what the host framework normally applies to elements.
- Do not add Jackson or JSON-B annotations to `Seq`, `AbstractSeq` or any other
  core type.
- `SeqStream` is single-use execution state and receives no serialization
  support.

## Jackson 2 module

Create `seq-jackson2` against Jackson 2.21.6 and Java 8. It has normal compile
dependencies on `seq` and Jackson Databind. Use the matching 2.21.6 Jackson XML
data-format dependency only for tests.

Provide a public Jackson module, preferably:

```text
io.github.jancellor.seq.jackson2.SeqModule
```

The module must register a contextual `Seq` deserializer. The deserializer must
obtain the contained `JavaType` from the contextual target type and delegate
element decoding back to Jackson so that all ordinary element configuration,
polymorphism and custom element deserializers continue to work.

Do not initially register a serializer. Jackson already recognizes `Seq` as a
`Collection` and writes it as an array. Add a serializer only if the required
JSON or XML tests demonstrate that Jackson's standard collection serializer is
insufficient. If one proves necessary, it must write ordinary array tokens and
remain data-format-neutral.

Support both explicit registration:

```java
mapper.registerModule(new SeqModule());
```

and opt-in Jackson discovery by including:

```text
META-INF/services/com.fasterxml.jackson.databind.Module
```

whose provider is the Jackson 2 `SeqModule`. Plain Jackson does not discover
modules merely because they are on the classpath; users opt in with
`findAndRegisterModules()`, while frameworks may elect to discover service
providers.

## Jackson 3 module

Create `seq-jackson3` against Jackson 3.1.5 and Java 17. It has normal compile
dependencies on `seq` and Jackson Databind. Use the matching 3.1.5 Jackson XML
data-format dependency only for tests.

Provide the corresponding public module:

```text
io.github.jancellor.seq.jackson3.SeqModule
```

Its behavior must match the Jackson 2 module, but implement the Jackson 3 APIs
directly. Do not try to share compiled adapter classes between the modules.
Jackson 3 moved packages from `com.fasterxml.jackson.*` to `tools.jackson.*`
(apart from the shared annotations artifact), raised its Java baseline to 17
and renamed extension types including `Module` to `JacksonModule` and
`JsonDeserializer` to `ValueDeserializer`. Contextual-deserializer APIs also
changed. These are genuinely different binary integrations.

Include the Jackson 3 service-provider file for its actual module service type
(`tools.jackson.databind.JacksonModule`) and provider class. Confirm the exact
path against Jackson 3.1.5 rather than copying the Jackson 2 filename.

## Jackson format neutrality and tests

Both Jackson modules are Databind modules, not JSON-only modules. Their
production code must use format-neutral parser, context and token APIs.

Each module must test all of the following with both a JSON mapper and the
matching `XmlMapper`:

- Default serialization of a root `Seq<String>` without a custom serializer.
- Deserialization of a root sequence with an explicit generic target type.
- A `Seq<Person>` property in a containing object, proving contextual element
  typing rather than `Map` fallback.
- Empty sequences.
- Sequences containing a `null` element.
- Framework-standard handling of a `null` sequence value.
- Rejection of a clearly incompatible scalar/object input.
- Explicit module registration.
- ServiceLoader discovery through the framework's module-discovery API.

XML tests may use test-only DTOs and Jackson XML annotations to define wrapper
and item names. No particular XML vocabulary, namespace or arbitrary XSD is a
Seq contract. The tests exist to prove that the deserializer is genuinely
format-neutral and works with an `XmlMapper` when that mapper exposes the
sequence as array tokens.

If default Jackson serialization passes these tests, keep the artifacts
deserializer-only. Do not add symmetry code merely because a deserializer
exists.

## JSON-B module

Create `seq-jsonb2` against `jakarta.json.bind:jakarta.json.bind-api:2.0.0`.
Declare that API dependency with Maven `provided` scope:

```xml
<dependency>
    <groupId>jakarta.json.bind</groupId>
    <artifactId>jakarta.json.bind-api</artifactId>
    <version>2.0.0</version>
    <scope>provided</scope>
</dependency>
```

This artifact targets the `jakarta.json.bind.*` compatibility family that
starts at JSON-B 2. It is named `seq-jsonb2` so that a future incompatible
generation can receive a clean name such as `seq-jsonb4`. It does not support
JSON-B 1's old `javax.json.bind.*` namespace. Do not add `seq-jsonb-javax`
unless real demand appears.

The JSON-B API is required to compile and use this integration, but the
application or runtime chooses and supplies it. A Jakarta EE runtime may supply
JSON-B directly; a standalone application may use Yasson or another conforming
provider. The integration must not bring an API version into the consuming
dependency graph and must remain provider-neutral.

Do not depend on Yasson at production scope. Use Yasson 2.0.4 and 3.0.4 only in
tests to verify the same adapter source against JSON-B 2 and JSON-B 3. JSON-B 2
and 3 share the relevant `jakarta.json.bind` serializer/deserializer SPI and
the same Maven API coordinate; this is unlike the Jackson 2/3 split.

Provide one public deserializer, preferably:

```text
io.github.jancellor.seq.jsonb2.SeqDeserializer
```

It must implement the standard JSON-B deserializer SPI, inspect the supplied
runtime `Type` to recover `E`, delegate element deserialization through the
`DeserializationContext`, and return a snapshot `Seq`.

Register it only through JSON-B's standard configuration API:

```java
JsonbConfig config = new JsonbConfig()
        .withDeserializers(new SeqDeserializer());
Jsonb jsonb = JsonbBuilder.create(config);
```

Do not add annotations to core types and do not add a `SeqJsonb.configure(...)`
helper. JSON-B has no standard datatype-module abstraction, and a helper around
one `withDeserializers(...)` call would create a redundant second integration
idiom.

Do not initially provide a JSON-B serializer. The JSON-B specification requires
collection implementations to serialize as collections, and serialization of
an interface-typed property is resolved from its runtime type. Since `Seq`
extends `Collection`, default writing should already produce a JSON array. Add
a serializer only if provider-neutral tests prove it necessary.

Test with both Yasson generations:

- Root and property serialization produces an ordinary JSON array without a
  custom serializer.
- Root `Seq<String>` and property `Seq<Person>` deserialization preserves the
  declared element type.
- Empty, `null`-element and `null`-sequence cases follow the shared contract.
- Incompatible non-array input fails conventionally.
- Direct `JsonbConfig.withDeserializers(new SeqDeserializer())` registration
  works.

Because the two Yasson generations cannot be active in one test classpath, use
separate test executions, profiles or small integration-test modules while
keeping one published `seq-jsonb2` artifact. Do not expose Yasson transitively.

## Explicitly out of scope

- Java native serialization and implementing `java.io.Serializable`.
- Serialization of `SeqStream`.
- JAXB or a `seq-jaxb` artifact.
- Spring or Spring Boot dependencies, annotations, auto-configuration or a
  starter. Spring users can register the ordinary Jackson module as a bean when
  their Boot generation does not discover ServiceLoader modules.
- Gson, Moshi, Kryo, Protobuf, Avro, Thrift and other framework integrations.
- Framework annotations on core Seq types.
- Preserving view/backing-object identity through serialization.

## Build, quality and documentation work

- Add all published artifacts to the reactor, source and Javadoc attachment,
  signing and Central Portal release flow already used by `seq`.
- Apply the repository formatting configuration to every new module.
- Maintain the existing 100% line-coverage requirement for production code in
  each new module; tests must exercise error and contextual-type paths as well
  as happy paths.
- Run the complete JDK 17+ reactor with `mvn verify`. Also preserve the existing
  Java 8 build path for `seq`, `seq-jackson2` and `seq-jsonb2`.
- Update `README.md` with separate dependency and registration examples for
  all three integrations. Present exactly one normal registration path per
  framework, while mentioning Jackson discovery as the alternative already
  defined by Jackson itself.
- Update `CONTRIBUTING.md` and `docs/DEVELOPMENT.md` wherever their build,
  module or verification descriptions become incomplete.
- Once implementation and documentation are complete, remove the serialization
  investigation item from `TODO.md`; do not leave the settled decision copied
  there.
- Record any enduring serialization contract in the appropriate existing
  public documentation and link to it rather than duplicating it across files.

## Verification outcome

The work is complete when a clean build proves that:

1. Core `seq` remains dependency-free and Java 8 compatible.
2. Jackson 2.21.6 and Jackson 3.1.5 each discover and explicitly register their
   own module, serialize through standard collection behavior, and deserialize
   typed sequences from both JSON and XML.
3. `seq-jsonb2` has only a provided JSON-B API dependency, no provider
   dependency, uses the standard registration call, and works with the chosen
   Yasson 2 and 3 test providers.
4. All deserialized results are snapshot `Seq` values with preserved order and
   contextual element types.
5. No Java serialization, JAXB or Spring integration has been introduced.

## Implementation notes

The plan was implemented in September 2026 with the following results and
deliberate omissions:

- `seq-jackson2` and `seq-jackson3` were implemented with the versions, Java
  releases, packages, automatic module names and reactor placement specified
  above. Both support explicit registration and ServiceLoader discovery.
- Both Jackson modules are deserializer-only. Jackson's standard collection
  serializer produced the required array representation, so adding a custom
  serializer would only have duplicated framework behavior.
- JSON and XML tests cover root and property values, contextual element types,
  empty sequences, null sequence values, invalid inputs and both registration
  paths. Deserialized sequences are shallow snapshots created through the
  public core API.
- The proposed `seq-jsonb2` artifact was not implemented. Yasson 2.0.4 and
  3.0.4 match a configured `JsonbDeserializer` by its declared generic binding:
  a deserializer registered for raw `Seq` is not selected for `Seq<String>` or
  `Seq<Person>`, while one registered for `Seq<?>` also does not match concrete
  element arguments. JSON-B exposes no portable family-wide registration hook
  that would both match every `Seq<E>` and retain `E`. Per-property annotations
  would not satisfy the proposed one-time configuration or typed-root contract.
- XML null elements have a host-format limitation. Jackson's XML parser exposes
  an empty collection item as an empty string rather than a null token in the
  tested root-array shape, including when `xsi:nil` processing is enabled.
  Treating every empty string as null in the Seq deserializer would corrupt
  valid empty-string elements, so no Seq-specific coercion was added. Ordinary
  null sequence values still follow Jackson's standard handling, and null
  elements are preserved whenever the parser supplies a null token, including
  normal JSON input.
- The README therefore documents the two Jackson artifacts rather than the
  originally proposed three integrations. The settled contract and the JSON-B
  decision are recorded in `docs/DESIGN.md`.
- Clean `mvn verify` builds passed on both Java 8 and Java 25. The Java 8 reactor
  contains `seq` and `seq-jackson2`; the Java 25 reactor additionally contains
  `seq-jackson3` and `seq-bench`. All production modules retained the 100% line
  coverage requirement.
