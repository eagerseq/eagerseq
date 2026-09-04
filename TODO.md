# TODO

## Done: renamed to `io.github.eagerseq`

Coordinates are `io.github.eagerseq:eagerseq`, package and
`Automatic-Module-Name` both `io.github.eagerseq`. Namespace verified on the
Central Portal on 2026-09-01. `org.bitbucket.seqly:seqly:0.5.0` stays up under
the old coordinates; there is no compatibility shim.

The factory renames described under "Settled: factories" ship in the same
breaking release, 0.6.0.

## Settled: factories

Snapshot factories are named `copyOf` and live factories are named `viewOf`.
The array overloads remain alongside `of(E...)`: `copyOf(E[])` is behaviorally
redundant with `of(array)`, but makes the ownership choice against
`viewOf(array)` explicit and discoverable. `viewOf(E[])` is deliberately not
varargs, because a no-copy call over compiler-created varargs has no useful
external backing array to view.

`copyOf(Optional)` treats the optional like the other sources of elements:
`of(optional)` contains the optional itself, while `copyOf(optional)` contains
its zero or one elements. This also pairs naturally with `toOptional()`.

`viewOf(Collection)` replaces `viewOf(Iterable)`. A collection expresses the
reusable-source requirement in its type, while the runtime `ORDERED` check
enforces a defined encounter order. `copyOf(Iterable)` covers snapshots and
`SeqStream.viewOf` covers one-pass processing. A broader `viewOf(Iterable)`
remains possible as an additive overload if its absence proves painful.

## Settled: combinatorics

The fixed-size operations use the conventional parameter name `k`:
`permutations(k)`, `combinations(k)` and `power(k)`. No-argument
`permutations()` retains its conventional meaning of full-length permutations.
`allPermutations()` and `allCombinations()` cover every `k` from zero through
the receiver size in shortlex order: length first, then lexical source-index
order. The old `powerSet()` name is gone; it implied equality-based set
semantics that the positional combinations do not have.

`product(that, mapper)` is lazy in the receiver and reads the second operand
before producing results, so an infinite receiver works when the second operand
is finite. The asymmetric implementation follows the receiver-method shape and
the nested-loop order: each receiver element is paired with every element of
`that` before advancing the receiver.

The combinatorics methods on `SeqStream` return `SeqStream<Seq<E>>`, not a
stream of streams. Laziness belongs to producing the outer sequence of results;
each individual permutation, combination or power is a finite, reusable value.

## JDK contract clashes

`Seq.reversed()` versus `SequencedCollection.reversed()` is settled: ours stays
a snapshot, documented on the method. Options weighed and rejected were
renaming to `toReversed`, dropping `reversed`, returning a live view, and
forcing immutability on `Seq` to collapse the difference.

`getFirst()`/`getLast()` are now present, so JDK 21 users find the names they
reach for. They clash with nothing: `SequencedCollection` declares them
returning `E`, as ours do.

Still open: **when to raise `release=8`**, which is what makes the `reversed`
semantics live. javac on 25 already warns that `source`/`target` 8 is
"obsolete and will be removed in a future release".

## Settled: spliterator characteristics

Every `Seq` has a stable encounter order and its spliterator reports `ORDERED`.
The `viewOf(Collection)` factory checks the characteristic up front.
Array-backed results also receive `SIZED | SUBSIZED` from the JDK array
spliterator.

`SeqStream` may be unordered. Ordinary intermediate operations preserve the
source's `ORDERED` flag; `zip` and `union` require both sources to be ordered,
and `concat` requires every source to be ordered; index-producing operations
and sorting establish order; reversing, rotating, shuffling and combinatorial
operations preserve the source's order; and `unordered()` removes it.
Materializing into an array is an implementation detail and does not itself
establish order. Custom lazy spliterators report no size, sorted, distinct,
non-null, immutable or concurrent characteristics. Their shared bases also
enforce the `tryAdvance(null)` contract.

Characteristics are not consumed for correctness. The default `count()` and
`size()` implementations traverse, and buffers grow from the data, so a source
that falsely reports `SIZED` cannot change a result or silently drop elements.
`ArraySeq` uses its array directly for constant-observation queries, including
`count()`, `size()`, positional access and the single-element terminals; these
specializations rely on the representation it owns rather than spliterator
characteristics. `CollectionSeq` delegates `size()` and `isEmpty()` to its
backing collection, whose contract directly supplies those observations, but
does not delegate `count()`: `Collection.size()` clamps above
`Integer.MAX_VALUE`, while `count()` returns an exact `long`.

## Settled: validation

Public `Seq` and `SeqStream` methods validate functional, count and index
arguments before obtaining their spliterator, so an invalid intermediate
operation does not claim a stream. `Split` algorithms assume their caller has
performed argument validation. The checks that remain in `Split` protect its
own internal contracts: deferred suppliers, functions and returned
spliterators must be non-null, and custom spliterators must reject a null
`tryAdvance` action.

Secondary `Iterable` and `Stream` arguments are likewise checked before the
receiver's spliterator is obtained. Static `concat` validates every source
before obtaining any spliterators; its `SeqStream` form then claims all known
input streams immediately while deferring their traversal.

`flatten` follows `flatMap`: a null inner source contributes no elements. Its
outer source is still a direct argument and is rejected immediately when null.

A null functional argument is rejected eagerly, matching the JDK, including
the combiners accepted by the inherited three-argument `reduce` and `collect`
signatures. Internal null sentinels implement natural-order `sorted()` and the
throw-on-duplicate `toMap` overloads, but cannot be supplied through the
corresponding public argument-taking overloads.
`collect(Collector)` checks only the collector itself, not the methods it
bundles.

Negative counts and indexes are rejected eagerly. For fixed-size
combinatorics, a negative `k` throws, while `permutations(k)` and
`combinations(k)` return an empty result when `k` exceeds the source length.

## Settled: value factories

`repeat`, `generate` and the bounded JDK `iterate` form now exist on both
types, along with unbounded `repeat` and `generate` on `SeqStream`:

| | `Seq` | `SeqStream` |
|---|---|---|
| `repeat(e, n)` | yes | yes |
| `repeat(e)` | — | yes |
| `generate(s, n)` | yes | yes |
| `generate(s)` | — | yes |
| `iterate(seed, hasNext, next)` | yes | yes |
| `iterate(seed, op)` | — | yes |

Two rules generate that table. A trailing count is what bounds a factory, and
bounded factories are symmetrical between the two types; an unbounded one
exists only on `SeqStream`, since eager `Seq` cannot contain its result.
`iterate` is bounded by a predicate rather than a count because it is the only
stateful source — `hasNext` tests the candidate element, which is meaningful
only when successive elements are related. It follows the JDK exactly,
including testing `seed` first, so rejecting the seed gives an empty result.
There is no `iterate(seed, op, n)`; `limit` covers it.

The `copyOf`/`viewOf` conversion factories remain asymmetrical, because
ownership and one-pass sources differ between the two types.

`repeat(e, n)` is specified as the same reference `n` times, so it is not
merely `generate(() -> e, n)`. Both report `ORDERED`, deviating from
`Stream.generate`, which is documented unordered; the deviation is forced,
since every `Seq` reports `ORDERED` and splitting the pair for JDK parity
would buy nothing.

`Split` holds the algorithms and both interfaces just validate and wrap, as
with `range`. Only `repeat(e)`, `generate(s)` and `iterate(seed, op)` are new
spliterators; the bounded forms delegate — `repeat(e, n)` and
`generate(s, n)` through the existing `Split.limit`, which keeps
`generate(s, n)` lazy rather than calling the supplier `n` times when the
factory is called, and `iterate(seed, hasNext, next)` through
`takeWhile(iterate(seed, next), hasNext)`, which reuses that spliterator's
existing terminal latch and applies `next` exactly as often as a hand-rolled
version would.

Rejected: folding `repeat(e)` into `generate(() -> e)`. It saves six lines and
adds an interface call on a call site shared with every other `generate`
caller, in the pull path, where a plain field read is today. Nothing here was
benchmarked.

## Settled: deferred whole-source operations

The eleven whole-source `SeqStream` intermediate operations are lazy:
`sorted()`, `sorted(Comparator)`, `reversed()`, `rotated(distance)`,
`shuffled(random)`, `permutations()`, `permutations(k)`, `allPermutations()`,
`combinations(k)`, `allCombinations()` and `power(k)`. They validate arguments
and claim the upstream stream when called, but defer reading it until the
result is traversed. Obtaining the result's `iterator()` or `spliterator()`
does not read the source; advancing it does.

`Split.defer` implements this by initializing a delegate spliterator on first
advance. `SeqStream` supplies computations that buffer the input before
constructing either the transformed array spliterator or combinatorial
generator. The underlying `Split` algorithms are shared with eager `Seq`, which
can adopt array results without an additional copy. Failed initialization is
not retried; a later advance reports that the deferred computation failed.

`product(that, mapper)` still buffers `that` when called. Deferring that work
would be consistent but remains optional and outside this change.

## Settled: consumer sinks

`Seq.Builder` extends `Consumer<E>` following `Stream.Builder`: `accept` is the
abstract insertion operation and `add` is the fluent default that delegates to
it. `addAll` and internal spliterator sinks pass the builder directly as a
consumer; fluent caller-facing uses remain `add`. `Consumer.andThen` therefore
joins the builder surface and returns `Consumer<E>`, which is accepted.

`Split` does not create capturing consumers at repeated `tryAdvance` pull
sites. Reference-valued pulls reuse `Box` fields on their returned
spliterators; the terminal `listEquals` uses two local boxes because it has no
wrapper object. `zip` uses two typed boxes, `flatten` keeps its current
spliterator directly in its box, and `map`, `mapMulti` and `peek` buffer their
input in a box before applying their operation. The primitive assignment in
`toMatchIndexes` instead caches its `IntConsumer` as a field. No-op consumers,
whole-traversal accumulators and genuine transformation functions remain
lambdas. No benchmark was taken, so whether escape analysis had already
removed the old allocations is unknown.

## Contract and robustness

- **Serialization is unconsidered.** No `Seq` implementation is
  `Serializable`, and nothing says whether that is deliberate. Decide, and if
  it stays out, say so. Jackson is the other half: check what
  `ObjectMapper.writeValueAsString(seq)` does today (probably fine — `Seq` is a
  `Collection`) and whether reading one back needs a module.
- **The five `BaseStream` methods are unreviewed as a group.**
  `SeqStream` is single-threaded by design, so `isParallel()` returns
  `false`, `sequential()` returns `this`, `parallel()` also returns `this`,
  `unordered()` wraps the spliterator, `onClose` throws and `close()` does
  nothing. The consequence is that `s.parallel().isParallel()` is `false`,
  which contradicts `BaseStream.parallel`'s "returns an equivalent stream
  that is parallel". Options: keep it and document the deviation, make
  `parallel()` throw `UnsupportedOperationException` like `onClose`, or have
  it hand back a real parallel `Stream` (which would have to leave
  `SeqStream`). The `onClose`/`close` pair has the mirror-image question —
  `close()` silently succeeds while registering a handler fails — and
  `unordered()` is the only one of the five that does actual work, so
  it is the only one whose current behaviour is clearly right.
- **`Split`'s dependencies on the rest of the package are unreviewed.**
  `Split` is meant to be the algorithm floor, defined over `Spliterator` and
  arrays, which is why it returns `E[]` and lets `Seq`/`SeqStream` adopt the
  result. It is not actually self-contained. `SeqBuilder` is used in
  `emptySpliterator` (via `SeqBuilder.EMPTY`), both `toArray` overloads, the
  `limitLast`/`skipLast` queues and `groupBy`; `toStream` takes a `SeqStream`
  parameter outright, which is a straight inversion of the layering. Decide
  what the floor is allowed to know about.
  For `SeqBuilder` specifically the case is decent — it is a growable array
  builder, not a `Seq` concept, its `trim()` hands back a bare `E[]`, and it
  is what lets a buffer be "sized from the data" rather than from a claimed
  size (the `limitLast` comment). But it is a second growth policy alongside
  `ArrayList`, which `toList` uses, and no benchmark says the no-copy
  adoption pays for that. Options: keep it and say so; move it to a neutral
  package-private array builder that `SeqBuilder` itself wraps; or drop it
  from `Split` in favour of `ArrayList` and accept a copy per result.
  `toStream` is the separate and clearer problem — it exists only to let
  `SeqStream` bounce off `StreamSupport`, and probably belongs on
  `SeqStream`.

## Settled: index and count conventions

Decided while fixing `slice`, and worth applying to anything new that takes a
number (`chunked`, `windowed`). The combinatoric
operations use an `int k` consistently.

- **`int` unless the JDK forces `long`.** `count`, `limit` and `skip` are
  forced by `Stream`; `limitLast`/`skipLast` match them in case the JDK ever
  adds them. Everything else is `int`, since `Seq` is a `Collection` and
  cannot exceed `Integer.MAX_VALUE` anyway. `slice` was briefly widened and
  reverted: `indexOfSlice` returns the `int` that `slice` consumes
  (`a.slice(i, i + b.size())`), and that pairing cannot move because
  `indexOf` returning `int` is `List` parity.
- **Indexes throw `IndexOutOfBoundsException`, counts throw
  `IllegalArgumentException`**, and `limit`/`skip` are counts because `Stream`
  says so. Same seam as the width rule, so one fact about the JDK boundary
  predicts both. `Split.requireNonNegativeIndex` and
  `requireNonNegativeArgument` are the two entry points.
- **Clamp at the top, throw at the bottom.** Not the arbitrary mix it looks
  like: the lower bound is knowable at the call, the upper bound is not
  knowable until the source is exhausted, so the ends differ in what the
  operation can check when. A strict upper bound would have to be checked
  lazily on `SeqStream`, which makes it conditional on the downstream
  terminal — `slice(2, 10).findFirst()` would succeed where
  `slice(2, 10).toList()` throws. Scala's lazy `slice` clamps; Kotlin's strict
  `slice` exists only on sized types, with clamping `take`/`drop` on
  `Sequence`. Lazy and strict is the combination nobody ships. (Those two
  precedents are from memory and unverified.)

What clamping costs, for the record: `indexesOfSlice(slice(from, to))` still
always finds a match, but it is not necessarily at `from` — the invariant is
"the result is a genuine slice of this `Seq`", not "the slice at `from`",
which is where it is weaker than `get`.

## API gaps

Each forces users back into the `Stream` verbosity `Seq` exists to remove.

- **Numeric averages and statistics** — mapper-based primitive sums and
  products are direct terminals; averages and summary statistics still require
  dropping into a primitive stream.
- **`partition(Predicate)`, `chunked(n)`, `windowed(n)`, `sortedBy(Function)`.**
- **Factories** — `SeqStream.builder()` is the one still missing; see
  "Settled: value factories" for what shipped.
- **`withIndex()`** — pairs each element with its index, returning
  `Seq<IndexedValue<E>>` or `Seq<Entry<Integer, E>>`. Kotlin's name and shape.
  `zip(indexes(), ...)` is equally general but cannot be used mid-chain, since
  `indexes()` needs the stage named; the same objection applies to the offset
  self-zip that `pairwise` would replace. Undecided: whether a new record beats
  a boxed `Entry`.
- **Positional copy-modify** — `updated(i, e)` and friends, the immutable
  answer to the `List` mutators `Seq` inherits only to throw, currently
  spelled `slice`/`sum`; Scala has `updated`/`patch`, but undecided.

## Test depth

Gaps that apply to the whole library, not to any one method, which is why
neither remaining one is patched locally.

- **Cross-checking is now exhaustive for the index arithmetic.**
  `SeqReferenceTest` compares every method that does index arithmetic, plus
  the multiplicity-sensitive comparisons (`listEquals`, `multisetEquals`,
  `setEquals`), against a naive `java.util`-only reference, over every
  sequence of `a`/`b`/`null` up to length 5 (length 4 where a test sweeps
  input pairs) and every argument from `-2` to `length + 2`, across all 14
  factories. The combinatorics references derive index words independently,
  then filter them for permutations and combinations; the all-size operations
  concatenate those fixed-`k` references, while `power` uses the words directly
  and `product` is checked over exhaustive input pairs. The `DelegatingSeq`
  factory routes through `SeqStream`, so every one of those methods is checked
  in its `SeqStream` implementation too, not only its `Seq` one. Verified to
  catch a real bug: swapping `slice` to
  `limit(skip(...))` fails at `[a, a], 1, 1`.
  Not covered there, and still hand-picked in `SeqTest`: the predicate and
  mapping operations (`filter`, `map`, `takeWhile`, `dropWhile`, `distinct`,
  `sorted`), which carry no index arithmetic, and `shuffled`. `reversed` and
  `rotated` are checked, but both they and the reference delegate to
  `Collections`, so those two prove only the array round-trip.

- **Infinite-source and laziness coverage is in place.** `SeqStreamTest` uses
  `SeqStream.iterate` under `@Test(timeout = ...)` for prompt validation,
  finite-prefix intermediate operations and short-circuiting terminals. It
  also checks that `product` streams an infinite receiver when its second
  operand is finite, including the empty-second-operand case. A `peek` counter
  verifies that a representative intermediate pipeline consumes nothing on
  construction and only the required source elements on traversal.

## Docs and build

- **Restructure the `Seq` class javadoc and the README generated from it.**
  The library's focus has shifted subtly over time, and the current opening
  undersells it. Lead with the API story: `map` and `filter` are defined
  eagerly on `Seq`, and the longer-term pitch is that the useful
  collection-like operations live together on one rich type. The latter needs
  some of the API gaps above filled before it can be emphasized honestly.
  Move installation instructions toward the end, after the overview and API
  motivation.
- **The README is generated from `Seq` alone**, but `SeqStream` is just as
  much a part of the library, even if it is the less important of the two.
  Reconsider the generation order and source of truth: the README might be
  assembled by concatenating material from both class javadocs, or generating
  the README directly from javadoc may no longer be the right relationship.
