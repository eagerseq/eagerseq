# Developer Guide

Start here when working on EagerSeq. This is the essential orientation; read
linked references as needed for the task rather than loading every document.

## What EagerSeq does

EagerSeq puts common collection operations directly on the collection. `Seq`
extends Java's `Collection` with eager methods such as `map`, `filter` and
`groupBy`: `words.map(String::length)` immediately returns a materialised,
reusable sequence without stream-and-collector ceremony.

The API also extends beyond the operations available on `Stream`, gathering
useful methods otherwise found in `Collections` or libraries such as Guava:
`reversed`, `intersection`, `zip`, `windowFixed` and `combinations`, for example.
Keeping these methods on the collection makes them easier to discover and
combine without searching through separate utility classes.

For lazy composition, `seq.stream()` returns `SeqStream`, a specialisation of
JDK `Stream` retaining the library's additional operations; `toSeq()` materialises
the result. A `Seq` is reusable; a `SeqStream` is single-use. Keep both APIs in
mind when changing an operation.

## Core model and architecture

Both APIs express iteration through `Source`, a small extension of
`Spliterator` that adds `forEachWhile`. Like `forEachRemaining`, it can traverse
many elements in one call, but its callback returns a boolean to request
continuation or stop early. This lets algorithms combine bulk traversal with
short-circuiting without repeatedly calling `tryAdvance` for individual
elements. The callback uses the JDK `Predicate` interface; ordinary
`Spliterator` operations remain available for interoperability.

`Seq` and `SeqStream` delegate common implementations to `Sources`. Their public
methods generally validate arguments, acquire a source, call the shared
algorithm and wrap its result. The key difference is evaluation: `Seq`
generally materialises into `ArraySeq`, while `SeqStream` retains a lazy source
pipeline. For example, their default `map` methods both use `Sources.map`, but
only the eager version immediately collects the mapped elements.

All library code lives in `src/main/java/io/github/eagerseq`. The main pieces are:

| Component | Role |
|---|---|
| `Seq`, `SeqStream` | Public eager and lazy APIs. |
| `AbstractSeq` | Shared value equality, hashing and string representation for sequences. |
| `Sources` | Shared algorithms, terminals, buffering and adapters. |
| `Source`, `Stage` | Traversal and composition of operations. |
| `ArraySeq`, `CollectionSeq` | Array-backed sequences and collection-backed views. |
| `SourceSeqStream`, `SeqStreamPipeline` | Single-use stage acquisition and shared lifecycle state. |
| `ArrayBuilder` | Accumulation into arrays for results and builders. |

When implementing a stage, consult the class Javadoc in
[`Stage.java`](src/main/java/io/github/eagerseq/Stage.java) for callback binding
and overriding `forEachWhile`. The callback interface choice is explained
in [the design rationale](docs/DESIGN.md#traversal-callbacks).

`Seq` has a defined encounter order. Its collection mutators throw, but it is
not necessarily backed by immutable data: `copyOf` creates a snapshot, while
`viewOf` retains backing data and reflects external changes. Ordinary eager
transformations produce snapshots. An `ArraySeq` may itself be a view, so its
backing array must not be mutated or exposed by a copying conversion.

For custom sequences, follow the implementation guidance in
[`Seq.java`](src/main/java/io/github/eagerseq/Seq.java): extend `AbstractSeq` to
inherit the value implementations that interface defaults cannot supply.

Specialised implementations optimise common cases but must preserve the shared
contracts. For example, `ArraySeq` can answer positional and size queries
without general traversal. Stream operations also have lifecycle obligations:
claiming an input is distinct from traversing it, and derived stages share
pipeline state. Consult the references below before changing these details.

## Working here

The library targets Java 8 and has no runtime dependencies. Run `mvn verify`
for the build checks; use JDK 17 or newer locally to include the formatting
check. Build configuration and CI define the precise checks and supported
verification environments. [DEVELOPMENT.md](docs/DEVELOPMENT.md) explains commands,
test organisation and how to validate changes.

For README and API documentation maintenance, follow
[the development reference](docs/DEVELOPMENT.md#documentation). Follow
[AGENTS.md](AGENTS.md) for documentation maintenance and handling disagreements
between code and documentation.

## Read further when relevant

| When working on… | Read |
|---|---|
| Design choices and their rationale | [DESIGN.md](docs/DESIGN.md) |
| Stream lifecycle, laziness, closure or parallel bridges | [STREAM_SEMANTICS.md](docs/STREAM_SEMANTICS.md) |
| Build, tests, formatting or documentation | [DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| Equality, ordering or related API proposals | [EQUALITY_AND_ORDERING.md](docs/EQUALITY_AND_ORDERING.md) |
| Search and counting proposals | [DIRECT_MATCHING.md](docs/DIRECT_MATCHING.md) |
| Benchmarks and performance evidence | [bench/README.md](bench/README.md) |
| Outstanding work | [TODO.md](TODO.md) |

[COMPARISONS.md](docs/COMPARISONS.md) is optional historical research, not a roadmap
or required onboarding. Method Javadoc and tests provide the individual API
contracts; proposal examples in design notes do not establish existing methods
or commitments to implement them.
