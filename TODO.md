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

## JDK contract clashes (release=8, so latent for now)

- **`Seq.reversed()`** — `SequencedCollection.reversed()` (Java 21) is a *live
  view*; ours is a snapshot. Would compile as a covariant override and
  silently break the contract if we ever target 21. Tension with 88b6330
  (views → immutable conversions). Decide: rename, document, or conform.
- **`findFirst()`/`findLast()`** — JDK 21 users reach for `getFirst()`/
  `getLast()`, which throw instead of returning `Optional`. Add throwing
  variants?
- **`toList()`** — `Stream.toList()` (Java 16) is unmodifiable *and permits
  nulls*; `List.copyOf()`/`Collectors.toUnmodifiableList()` reject them. Check
  whether `SeqList` allows nulls, then say which contract we match. Same for
  `toSet()`/`toMap()` — and note `toSet()` promises encounter order where
  `Set.copyOf()` doesn't.

## Spliterator characteristics

Thoroughly check `Spliterator` behavior and reported characteristics across the
entire library, including every sequence/view/stream implementation and their
intermediate operations.

## Contract and robustness

- **`limit`/`skip` reject legal `Stream` arguments.** `Split.toInt` throws a
  bare `RuntimeException` above `Integer.MAX_VALUE`, so `limit(Long.MAX_VALUE)`
  fails where `Stream.limit` returns everything. The `int` counters inside
  `Split.limit`/`skip` are the real constraint; widening them to `long` lets
  both clamp instead. Matters most on `SeqStream`, which is lazy and can
  legitimately exceed `int`. `toInt` serves two unrelated purposes —
  validating a caller argument (wants a documented `IllegalArgumentException`)
  and reporting a `Seq` too large to size (genuinely exceptional) — hence the
  vague exception. Split it.
- **`limitLast`/`skipLast` allocate the argument, not the data.** Their
  `new Object[size]` queue means `Seq.of(1, 2, 3).limitLast(200_000_000)`
  throws `OutOfMemoryError` in 40ms. Clamp to the source size where known,
  else grow the buffer lazily. The `// consistency with limit()` comments
  explain why the `int` bound spread here; that argument dissolves once the
  buffer is sized from the data.
- **A reused `SeqStream` silently returns wrong answers.**
  `SpliteratorSeqStream` hands back the same spliterator every call, so a
  second terminal operation yields `[]` where the JDK throws
  `IllegalStateException: stream has already been operated upon or closed`.
  Users get a `Stream` from `Seq.stream()` and will expect that guard. A
  consumed flag is enough.
- **Exceptions carry no messages.** Duplicate key in `toMap` (the JDK names
  the key and both values), `combinations` out of range, `Split.get`'s index,
  and `SeqBuilder.nextLength` *throwing* `OutOfMemoryError`. These are the
  library's main failure mode and messages are nearly free.
- **Raw `Spliterator` returns** from `Split.limitLast`/`skipLast`: four
  unchecked warnings in `Seq`, and no element-type checking inside. One-word
  fix each.
- **`Seq.view(Iterable)` needs a re-traversability warning.**
  `Seq` traverses repeatedly — `equals`, then `toString`, then `size` — so a
  one-shot iterable such as `stream::iterator` works once and then throws from
  inside the JDK.
- **Document `AbstractSeq` as the extension point.** `Seq`'s only abstract
  method is `spliterator()`, so `Seq<E> s = list::spliterator` compiles and
  gets identity `equals`/`hashCode`/`toString`, making equality asymmetric
  against a compliant `Seq`. Caller error, as with `List`/`Set` — but unlike
  them a single method reference reaches it by accident, so it is worth a
  javadoc sentence.
- **`toMap()` throws on any duplicate element** (identity key mapper) where
  `toSet()` de-dupes. Intended? Document either way.

## API gaps

Each forces users back into the `Stream` verbosity `Seq` exists to remove.

- **Grouping** — no `groupBy`, no `toMap(key, value, merge)`, no multimap, so
  `collect(Collectors.groupingBy(...))` is the only route. At least as common
  as `map`/`filter`. A `grouped` existed once (09a0281).
- **Numeric terminals** — summing means `seq.stream().mapToInt(...).sum()`.
  Want `sum(ToIntFunction)`, `average`, or `mapToInt` on `Seq` itself.
- **`min()`/`max()` natural-order overloads**, as `sorted()` already has.
- **`partition(Predicate)`, `chunked(n)`, `windowed(n)`, `sortedBy(Function)`.**
- **Factories** — `rangeClosed`, `repeat(e, n)`, bounded `iterate`.

## Docs and build

- **No CI.** A GitHub Actions matrix (8/11/17/21/25) would make the existing
  guards real: `testStreamMethodsAbsentFromSeqAreOnlyTheKnownExceptions`
  catches `Stream` methods added by later JDKs only if it runs, and JaCoCo
  reports coverage but gates nothing.
- **`SeqStream.indexOfSlice` javadoc links to `Seq#indexesOfSlice`** — the
  singular `Seq#indexOfSlice` is meant.
