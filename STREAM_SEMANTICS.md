# Stream semantics

`SeqStream` is a sequential implementation of `Stream` built on a
cancellable push traversal. Its `Source` spliterators push elements into a
`Sink` until the sink cancels or the source is exhausted, while still exposing
the ordinary `Spliterator` pull operations for interoperability.
Spliterator characteristics are deliberately outside the scope of this note:
under-reporting them primarily loses optimisations, and common cases such as
`ArraySeq` can be improved independently if useful.

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

Factories create new pipelines. `viewOf(Stream)` initialises its pipeline from
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

Because a sink can cancel immediately after accepting an element, an inner
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
the other buffering operations.

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
