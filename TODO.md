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

## JDK contract clashes (release=8, so latent for now)

- **`Seq.reversed()`** — `SequencedCollection.reversed()` (Java 21) is a *live
  view*; ours is a snapshot. Would compile as a covariant override and
  silently break the contract if we ever target 21. Tension with 88b6330
  (views → immutable conversions). Decide: rename, document, or conform.
- **`findFirst()`/`findLast()`** — JDK 21 users reach for `getFirst()`/
  `getLast()`, which throw instead of returning `Optional`. Add throwing
  variants?

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
- **Exceptions carry no messages.** `combinations` out of range, `Split.get`'s
  index, and `SeqBuilder.nextLength` *throwing* `OutOfMemoryError`. These are
  the library's main failure mode and messages are nearly free. The negative
  `size` arguments now name the offending value, via `Split.checkSize`, which
  is the shape the rest should follow.
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

## Test depth

Two gaps that apply to the whole library, not to any one method, which is why
neither is patched locally.

- **Nothing is cross-checked exhaustively.** Every case is hand-picked, so
  boundaries are covered only where someone thought of them. Rewriting
  `limitLast` found a bug at a queue growth step that all six existing cases
  missed. The index arithmetic in `slice`, `rotated`, `indexesOfSlice`,
  `combinations`, `powerSet` and the set operations is the same shape of risk.
  A single test comparing each against a naive reference over all small
  lengths and arguments would cover more than any amount of case-picking.
- **Laziness is never asserted.** `SeqStream`'s intermediate operations are
  lazy by design and no test says so for any of them. Nothing would catch an
  operation that drained its source on construction, or drained it twice, as
  long as the elements came out right. `peek` with a counter is enough.

## Docs and build

- **No CI.** A GitHub Actions matrix (8/11/17/21/25) would make the existing
  guards real: `testStreamMethodsAbsentFromSeqAreOnlyTheKnownExceptions`
  catches `Stream` methods added by later JDKs only if it runs, and JaCoCo
  reports coverage but gates nothing.
- **`SeqStream.indexOfSlice` javadoc links to `Seq#indexesOfSlice`** — the
  singular `Seq#indexOfSlice` is meant.
