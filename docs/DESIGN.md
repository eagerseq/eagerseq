# Design rationale

This note records choices whose reasons are not obvious from the implementation.
It is not a second specification: inspect the current code and tests for exact
behavior. [CONTRIBUTING.md](../CONTRIBUTING.md) provides the architectural overview.

## API design principles

The library values both concise expressions and a useful collection vocabulary.
An operation can earn a place even when it is already expressible through
composition. Conversely, filling out a symmetrical family of overloads is not
sufficient justification, nor is avoiding an eager intermediate when `stream()`
already provides lazy composition.

JDK names and signatures provide familiar starting points, but the eager and
lazy APIs have different constraints. Naming questions involving equality,
ordering and search are explored in [EQUALITY_AND_ORDERING.md](EQUALITY_AND_ORDERING.md)
and [DIRECT_MATCHING.md](DIRECT_MATCHING.md). Those analyses help evaluate
concrete proposals; they are not requirements to complete an API matrix.

## Traversal callbacks

`Source.forEachWhile` uses the JDK `Predicate` interface rather than a separate
public `Sink` interface. Its method name and contract give the boolean its
continuation meaning; a dedicated callback type would add vocabulary without
adding capability. Side effects are intentional here, as they are in the
`BiPredicate` accumulator accepted by `collectWhile`.

The callback parameter is named `action`, consistent with `forEachRemaining`.
Stages retain that name for the callback field, binding it with
`this.action = requireNonNull(action)`; `upstream` names the source feeding
the stage. The implementation protocol is documented in
[`Stage.java`](../src/main/java/io/github/jancellor/seq/Stage.java); the traversal
return contract belongs to
[`Source.forEachWhile`](../src/main/java/io/github/jancellor/seq/Source.java).

## Ownership choices

`copyOf(array)` deliberately exists alongside `of(array)`: the explicit name
makes the ownership choice against `viewOf(array)` visible. The array view
factory is not varargs because a compiler-created array has no useful external
owner to view. Similarly, accepting a `Collection` for a live collection view
expresses the need for a reusable source, rather than an arbitrary `Iterable`
that might only support one traversal.

`Seq.reversed()` remains a snapshot, consistent with ordinary eager
transformations. Adopting the live-view semantics of newer JDK sequenced
collections would make this operation behave differently from its neighbors.
Refusing collection mutations does not imply immutable backing data, since
explicit views remain supported.

## Validation and bounds

Public argument checks before stream acquisition are intentional: an invalid
intermediate operation should not consume the caller's opportunity to use the
stream. Explicit null checks also keep the public contract independent of
whether a particular internal delegate happens to dereference an argument.

Slicing rejects negative bounds but clamps at the upper end. A negative bound
is knowable immediately; an excessive upper bound may only become apparent
when a lazy source is exhausted. Strict upper bounds would make failure depend
on how far downstream evaluation proceeds. Clamping gives eager and lazy
slicing a consistent meaning without requiring full traversal.

## Deliberate API limits

A separate `Seq`/`SeqStream.forEachWhile` was rejected because `allMatch`
already provides the corresponding boolean traversal; `takeWhile(p).forEach(a)`
handles a separate condition and action. This is distinct from the lower-level
`Source.forEachWhile` traversal primitive.

`reduceWhile` was not added: returning both a new accumulator and a continuation
decision would require another public carrier type, while alternative shapes
cover much of the ground already served by `collectWhile` or scan-based
composition. `collectWhile` has its own name to avoid ambiguity with
void-compatible `collect` lambdas.

A general `gather` is deferred, not ruled out. Stateful, one-to-many,
cancelable operations need decisions about per-traversal state, completion and
cancellation. Exposing that machinery would commit to more public protocol and
vocabulary without established demand. A source-transform escape hatch was
likewise left out. These decisions can be revisited for concrete use cases;
the existence of internal machinery alone does not justify a public API.
