# Stream semantics

Detailed reference for contributors changing stream traversal or lifecycle.
Start with [CONTRIBUTING.md](../CONTRIBUTING.md) for the project overview.

`SeqStream` is a sequential implementation of `Stream` built on a
cancelable push traversal. Its `Source` spliterators push elements into a
downstream `Predicate` until it returns `false` or the source is exhausted,
while still exposing the ordinary `Spliterator` pull operations for interoperability.
Check the relevant source implementation for its size and ordering
characteristics. A size-based terminal may answer without traversing the pipeline.

## Shared pipeline

All ordinary one-to-one derived streams share a `SeqStream.Pipeline`,
implemented by `SeqStreamPipeline`, containing:

- whether the pipeline is closed;
- its composed close handlers; and
- its current parallel mode.

`SourceSeqStream.spliterator()` is the single acquisition point. It
rejects a consumed stage or a stage whose shared pipeline is closed, then
returns and clears the stage's source. A source already returned to
a caller does not keep checking the pipeline:
after `spliterator()` has returned, traversal is an operation on that cursor
rather than on the stream. Closing an underlying resource may nevertheless
make such a cursor fail naturally.

`onClose` adds a handler to the shared pipeline, provided that the stage on
which it is called has not already been linked or consumed and the pipeline
has not been closed. `closes(stream)` is the corresponding ownership helper:
it registers the given stream's `close` method without traversing or closing
it immediately. `close` closes the pipeline and runs its handlers once, in
registration order; all handlers run, with later exceptions suppressed onto
the first as specified by `BaseStream`. Closing is idempotent and works after
a terminal operation.

Intermediate operations construct a stream over their new source and the
existing pipeline with `viewOf(source, pipeline())`. The sources
themselves carry no parallel flag. `parallel()` and `sequential()` only mutate
the shared pipeline, and `isParallel()` reads it. Parallel evaluation is
intentionally not implemented: `SeqStream`'s own operations always run on the
calling thread, whatever the mode. The flag is tracked so that
`parallel().isParallel()` does not confusingly return `false`, and so that the
mode survives conversion to and from `Stream`. The sources are
nonetheless splittable, so `toStream()` and the primitive bridges built on it
do evaluate in parallel when the mode is parallel.

Factories create new pipelines. `viewOf(Stream)` initializes its pipeline from
the source mode and closes the source from its close action. The primitive
stream bridges in `toStream()` carry the current mode and close the
`SeqStream`. `concat` creates a new pipeline which closes all inputs; as in
`Stream.concat`, its initial mode is parallel when any input is parallel.
`flatten` creates a new pipeline which closes its outer stream and whichever
inner stream is current when the pipeline is closed. `flatMap` uses the
receiver's existing pipeline and registers its current mapped stream with it.
Both close inner streams as their exhaustion is observed. Unlike other
cursors, a flattening cursor is exhausted once its pipeline is closed, so it
never opens an inner stream that nothing could close.

Every operation taking another `Stream` directly adopts it into the receiver's
pipeline by calling `closes(stream)` after validating all arguments and before
obtaining either spliterator. This applies equally to intermediate operations
such as `zip`, `product` and `indexesOfSlice` and terminal operations such as
`listEquals`, `startsWith` and `containsAll`: the receiver's pipeline remains
closeable after a terminal operation. Traversal does not itself close either
stream; explicitly closing any stage in the pipeline closes every adopted
stream.

Because the traversal action can return `false` after receiving an element, an inner
stream that produced the last requested element remains open until the next
traversal attempt observes its exhaustion or until the pipeline is closed.
Short-circuiting and traversal failure are therefore partial traversal:
callers must close the returned stream to guarantee that the current inner
stream is closed. This differs from JDK `flatMap`, but allows an inner stream
to be infinite.

## Laziness and special operations

Deferring whole-source work until the first traversal attempt is valid. For
ordinary terminals that attempt occurs inside the terminal call; a cursor
terminal such as `iterator()` or `spliterator()` may defer it until the cursor
is first advanced. `Sources.defer` is therefore the mechanism for `sorted` and
the other buffering operations when traversal is required. A sized `count()`
can answer without initializing the deferred computation. Failed initialization
is not retried; subsequent traversal reports the failure.

Count-preserving deferred operations (`sorted`, `reversed`, `rotated`,
`shuffled` and `scan`) retain the input's `SIZED` characteristic without
querying its size during construction or cursor acquisition. Before traversal,
size queries read the input's current estimate; after initialization they read
the delegate's remaining estimate. This preserves allocation and `count()`
optimizations without prematurely binding late-binding inputs such as an
`ArrayList` view. Changes to such backing collections before evaluation (or
before the acquired cursor is first advanced or queried for size) are observed.
A size query may itself bind the input; this does not promise support for
mutation after binding or during traversal.

Operations that needed individual changes beyond sharing a pipeline:

- `product` defers buffering its right operand to first traversal. It still
  claims the operand's spliterator when the intermediate result is
  constructed.
- `indexesOfSlice` likewise defers consuming and preprocessing its secondary
  stream.
- `scan` invokes its initial supplier on the first traversal attempt rather
  than immediately.

The last three are custom APIs rather than violations of a specific JDK
operation, but deferring their setup makes `SeqStream`'s intermediate
operations consistently lazy.
