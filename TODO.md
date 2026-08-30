# TODO

## Rename off `org.bitbucket.seqly`

New GitHub org per project, verify `io.github.<org>` on the Central Portal.
artifactId stays `seq`; package matches groupId exactly.

Free as of 2026-08-05 (`seq`, `seqly`, `seqlib` are taken):

- `seqjava` — claims nothing, can't age badly
- `seqcollection` — accurate now, but `SeqStream` isn't a `Collection`
- `seqcollections` — reads best, overclaims (public API is just `Seq`,
  `SeqStream`, `AbstractSeq`)

Touches: pom coordinates + `Automatic-Module-Name`, package move, README,
`ReadmeGenerator`. Verify namespace before first release; 0.5.0 stays up under
the old coordinates.

The factory renames described under "Settled: factories" are already made;
include them in the same breaking release as the package move.

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
source's `ORDERED` flag; `zip` and `union` require both sources to be ordered;
index-producing, combinatorial and materializing operations establish order;
and `unordered()` removes it. Custom lazy spliterators report no size, sorted,
distinct, non-null, immutable or concurrent characteristics. Their shared
bases also enforce the `tryAdvance(null)` contract.

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

## Contract and robustness

- **Should `Seq.Builder` extend `Consumer<E>`?** `Stream.Builder` does
  (`accept` abstract, `add` a default); delegating the other way — `add`
  abstract, `default void accept(E e) { add(e); }` — would leave
  `SeqBuilder` untouched. Four `builder::add` sinks become `builder`
  (`Seq.addAll`, `Split.toArray`, `Split.limitLast`/`skipLast`); the cast
  at `Split.toArray(Spliterator, IntFunction)` stays, since its `A` and
  `E` are unrelated. Cost: `Consumer.andThen` joins the surface returning
  `Consumer<E>`, not `Builder<E>`.
- **Combinatoric range messages remain mechanical.** The fixed-size operations
  now consistently take `k`, but `k 3 was greater than length 2` may still read
  less naturally than `cannot choose 3 from 2 elements`.
- **`combinations` is the one count that throws rather than clamps** when the
  argument exceeds the data, where `limit`, `skip`, `limitLast` and `skipLast`
  all return what is present. Guava's `Sets.combinations` throws too, so this
  is defensible, but it should be a decision rather than an accident.
- **Null argument checks are ad hoc.** `requireNonNull` is used where an
  argument is stored for later (`mapMulti`, `sorted(Comparator)`,
  `toArray(IntFunction)`, `Split.replaceAll`), but most of the surface relies
  on the incidental NPE from the first call instead — so an empty source, a
  short-circuiting terminal, or an operand that empties the result swallows a
  null silently, and on `SeqStream` the failure is deferred to the terminal
  operation. `allMatch` looks like an exception but only throws because
  `Split.allMatch` calls `predicate.negate()`; `anyMatch` has no such
  accident, so the siblings disagree. Decide whether a null functional
  argument is checked eagerly or left to fail on first use, then apply it in
  `Split` where one check covers both `Seq` and `SeqStream`.
- **Three overflow policies for one concept.** `count()` returns `long`,
  `size()` clamps to `Integer.MAX_VALUE` per the `Collection` javadoc, and
  `indexes()` throws when an index cannot be represented as an `int`. Each is
  individually justified; worth stating in one place rather than discovering
  one at a time.
- **The unsupported operations throw bare `UnsupportedOperationException`.**
  The seven inherited `Collection` mutators on `Seq` (`add`, `remove`,
  `addAll`, `removeAll`, `removeIf`, `retainAll`, `clear`) plus
  `SeqStream.onClose`, all message-less. Unlike the other failures the
  mutators are reached through a `Collection`- or `List`-typed reference,
  where the stack trace names `add` but nothing says the receiver is a `Seq`
  and immutable by design. Whether a message is the right answer is undecided:
  the exception type is arguably self-explanatory, and seven near-identical
  strings are their own kind of noise. The javadoc already says it.
- **A dozen spliterators still allocate a lambda per element.** `Box` now
  implements `Consumer`, but the anonymous `AbstractSpliterator`s keep a
  `private E next` field and write `spliterator.tryAdvance(e -> next = e)`,
  which captures `this` and so allocates once per element on the hot path
  (grep `e -> next = e`, 11 sites in `Split`). A `Box<E>` field, or just
  caching the consumer beside the existing one, would remove that and make one
  pattern serve the whole file. Worth measuring before changing anything:
  escape analysis often erases the allocation.
- **Serialization is unconsidered.** No `Seq` implementation is
  `Serializable`, and nothing says whether that is deliberate. Decide, and if
  it stays out, say so. Jackson is the other half: check what
  `ObjectMapper.writeValueAsString(seq)` does today (probably fine — `Seq` is a
  `Collection`) and whether reading one back needs a module.

## Settled: index and count conventions

Decided while fixing `slice`, and worth applying to anything new that takes a
number (`chunked`, `windowed`, `rangeClosed`, `repeat(e, n)`). The combinatoric
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

- **Grouping** — no `groupBy` and no multimap, so
  `collect(Collectors.groupingBy(...))` is the only route. At least as common
  as `map`/`filter`. A `grouped` existed once (09a0281). Maybe
  `Map<K, Seq<E>> groupBy(keyMapper)` plus a per-group
  `Function<Seq<E>, D>` rather than a downstream `Collector`, since the groups
  arrive rich. That rules out a `(keyMapper, valueMapper)` overload, which
  erases the same.
- **Numeric terminals** — summing means `seq.stream().mapToInt(...).sum()`.
  Want `sum(ToIntFunction)`, `average`, or `mapToInt` on `Seq` itself.
- **`min()`/`max()` natural-order overloads**, as `sorted()` already has.
- **`partition(Predicate)`, `chunked(n)`, `windowed(n)`, `sortedBy(Function)`.**
- **Factories** — consider `SeqStream.builder()`, `repeat(e, n)`, the bounded
  JDK `iterate` form, `generate`, `rangeClosed`, and `long` versions of `range`
  and `rangeClosed`. Value-producing factories should generally be symmetrical
  between `Seq` and `SeqStream`, unless their semantics give a specific reason
  not to be. Which potentially non-terminating factories belong on eager `Seq`
  is TBD; so is a bounded `generate(supplier, n)`. The `copyOf`/`viewOf`
  conversion factories need not be symmetrical because ownership and one-pass
  sources differ between the two types. Unbounded `iterate` exists only on
  `SeqStream`, since eager `Seq` cannot contain its result.
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
  input pairs) and every argument from `-2` to `length + 2`, across all 13
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
- **JaCoCo reports coverage but gates nothing.** Decide whether a coverage
  threshold would catch useful regressions or merely create maintenance work.
