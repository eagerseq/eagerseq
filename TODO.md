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

While breaking anyway, consider `Seq.copy` → `copyOf`, matching `List.copyOf`.
Needs a matching decision for `view`: `viewOf` is clumsy, so perhaps both keep
their current names, or `view` becomes something else. Settle the `view`
question under "Contract and robustness" first — if the factories go, or
narrow to `Collection`, this is just `copy` → `copyOf` and there is nothing to
pair the name with.

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

## Spliterator characteristics

Thoroughly check `Spliterator` behavior and reported characteristics across the
entire library, including every sequence/view/stream implementation and their
intermediate operations.

`Split.count` no longer shortcuts to `estimateSize()` on a `SIZED` source, so
`count()` and `size()` always traverse. `ArraySeq` overrides `size` and
`isEmpty` directly, and `isEmpty` goes through a single `tryAdvance`
everywhere, so `count()` is linear everywhere by design and the only
unintended regression is `size()` on `view(Iterable)`, which was constant
time over an `ArrayList` and is now linear. Reconsider restoring it
here, together with the decision about which characteristics we report: the
policy worth weighing is to be conservative in what we report and liberal in
what we consume, since every bug so far has come from claiming a
characteristic we could not honour rather than from trusting one. The one
case that argues the other way is consuming `SIZED` to *size a buffer*, where
a source's wrong `SIZED` becomes silently dropped elements rather than merely
a wrong count. Nothing does that now: `limitLast`/`skipLast` size their queue
from the data instead.

## Contract and robustness

- **Should `Seq.Builder` extend `Consumer<E>`?** `Stream.Builder` does
  (`accept` abstract, `add` a default); delegating the other way — `add`
  abstract, `default void accept(E e) { add(e); }` — would leave
  `SeqBuilder` untouched. Four `builder::add` sinks become `builder`
  (`Seq.addAll`, `Split.toArray`, `Split.limitLast`/`skipLast`); the cast
  at `Split.toArray(Spliterator, IntFunction)` stays, since its `A` and
  `E` are unrelated. Cost: `Consumer.andThen` joins the surface returning
  `Consumer<E>`, not `Builder<E>`.
- **Consider dropping the `view` factories altogether**, or failing that,
  narrowing `view(Iterable)` to `view(Collection)`. Both hand out a `Seq` that
  its own `Collection` contract cannot honour. `view(Iterable)` accepts a
  one-shot source, which is legal — `Iterable` promises only that it can be
  the target of a for-each — and the resulting `Seq` then survives exactly one
  method call, since `size`, `equals`, `hashCode` and `toString` each traverse
  afresh. Documented for now, but the precondition is unenforceable: nothing
  can ask an `Iterable` whether it is re-traversable, so the failure arrives
  late, from inside the argument, naming something the caller never mentioned.
  `view(E[])` is re-traversable but *mutable*, making it the one `Seq` whose
  `hashCode` can change under a `HashMap`, which sits badly with 88b6330
  having replaced the collection views with immutable conversions.
  Guava is the precedent for narrowing: its views preserve the argument type
  exactly (`Iterables.transform` returns `Iterable`, `Collections2.transform`
  returns `Collection`), and every `Iterable`-to-`Collection` conversion it
  offers is a copy. `copy(Iterable)` already covers the one-shot case, and the
  library copies at every other step anyway, so the lost zero-copy path is a
  re-traversable non-`Collection` such as `Path`.
- **Document `AbstractSeq` as the extension point.** `Seq`'s only abstract
  method is `spliterator()`, so `Seq<E> s = list::spliterator` compiles and
  gets identity `equals`/`hashCode`/`toString`, making equality asymmetric
  against a compliant `Seq`. Caller error, as with `List`/`Set` — but unlike
  them a single method reference reaches it by accident, so it is worth a
  javadoc sentence.
- **`combinations(int size)` takes a size but `permutations()` does not.**
  Consider a `permutations(int size)` overload, i.e. k-permutations. If it
  lands, rename the parameter to `k` in both — the shared domain term, where
  `combinationSize`/`permutationSize` would stutter against the method names.
  Either way the hand-written range message reads better as `cannot choose 3
  from 2 elements` than as `size 3 was greater than length 2`, which reaches
  for *length* only because the parameter took the word *size*.
- **`combinations` is the one count that throws rather than clamps** when the
  argument exceeds the data, where `limit`, `skip`, `limitLast` and `skipLast`
  all return what is present. Guava's `Sets.combinations` throws too, so this
  is defensible, but it should be a decision rather than an accident.
- **Three overflow policies for one concept.** `count()` returns `long`,
  `size()` clamps to `Integer.MAX_VALUE` per the `Collection` javadoc, and
  `indexes()` throws from `Math.toIntExact`. Each is individually justified;
  worth stating in one place rather than discovering one at a time.
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
number (`chunked`, `windowed`, `rangeClosed`, `repeat(e, n)`, a `permutations`
overload).

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
- **Factories** — `rangeClosed`, `repeat(e, n)`, bounded `iterate`.
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
  factories. 2.1s. The `DelegatingSeq` factory routes through `SeqStream`,
  so every one of those methods is checked in its `SeqStream` implementation
  too, not only its `Seq` one. Verified to catch a real bug: swapping `slice` to
  `limit(skip(...))` fails at `[a, a], 1, 1`.
  Not covered there, and still hand-picked in `SeqTest`: the predicate and
  mapping operations (`filter`, `map`, `takeWhile`, `dropWhile`, `distinct`,
  `sorted`), which carry no index arithmetic, and `shuffled`. `reversed` and
  `rotated` are checked, but both they and the reference delegate to
  `Collections`, so those two prove only the array round-trip.

- **No test uses an infinite source.** `testGet` covers only finite ones, so
  neither `Split.get`'s hang on a negative index nor `ArraySeq.get` bypassing
  `Split.get` entirely was visible to it. `SeqStream` operations should be
  exercised against `Stream.iterate`, under `@Test(timeout = ...)`.
- **Laziness is never asserted.** `SeqStream`'s intermediate operations are
  lazy by design and no test says so for any of them. Nothing would catch an
  operation that drained its source on construction, or drained it twice, as
  long as the elements came out right. `peek` with a counter is enough.

## Docs and build

- **No CI.** A GitHub Actions matrix (8/11/17/21/25) would make the existing
  guards real: `testStreamMethodsAbsentFromSeqAreOnlyTheKnownExceptions`
  catches `Stream` methods added by later JDKs only if it runs, and JaCoCo
  reports coverage but gates nothing.
  Low urgency: `mvn verify` on 25 passes clean today (1553 tests, both
  reflection guards green — `gather` is already exempt), and development
  happens on 25, so the matrix is a ratchet against future JDKs rather than a
  hunt for present failures. `release=8` also means every row compiles
  against the same JDK 8 API, so the rows carrying new information are 8 and
  11, not the new ones. Unchased: test compilation warns that `SeqTest` uses
  a deprecated API, with no detail without `-Xlint:deprecation`.
- **GPG signing is unconditional**, so `mvn verify` needs the private key
  (`pom.xml:125`). The `verify` phase is correct; being outside a profile is
  not, and it blocks the CI matrix above. Move `maven-gpg-plugin` into a
  `release` profile, as Sonatype's guide does.
- **`SeqStream.indexOfSlice` javadoc links to `Seq#indexesOfSlice`** — the
  singular `Seq#indexOfSlice` is meant.
