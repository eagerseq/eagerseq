# Benchmark results

2026-09-18, Temurin 25.0.1, one core (`taskset -c 5`), 2g fixed heap.
`OperationBench`, sequential only, `SeqStream` against the equivalent JDK
`Stream`. A ratio above 1 means the library is slower.

Every number below comes from a 3-fork run unless marked otherwise, and
carries the worst of the two sides' relative error. **Ignore any row whose
error is above about 10%.** The first exploratory pass used a single fork
and produced ratios that later runs did not reproduce, so single-fork
numbers are not kept here.

## Headline

The push chain is not the problem. When a pipeline is drained with
`forEach`, the library beats JDK streams at every depth measured. The
losses are concentrated in three places: terminals that buffer, whole-source
operations that ignore a sized source, and `count`. The first and third
have since been addressed.

## Confirmed divergences

| case | JDK us/op | Seq us/op | time x | +/- | alloc x |
|---|---:|---:|---:|---:|---:|
| countMapped | 0.01 | 2.09 | 140.20 | 2% | 1.05 |
| limitLast | 0.52 | 4.44 | 8.61 | 7% | 4.35 |
| reversed | 1.03 | 4.66 | 4.52 | 13% | 6.46 |
| skip | 1.08 | 4.54 | 4.21 | 1% | 3.91 |
| peek | 1.46 | 4.44 | 3.04 | 3% | 3.56 |
| concat | 0.76 | 2.05 | 2.70 | 2% | 2.61 |
| dropWhile | 1.67 | 4.46 | 2.67 | 4% | 1.85 |
| map | 2.13 | 4.86 | 2.28 | 1% | 1.60 |
| toArrayGenerator | 1.15 | 2.54 | 2.21 | 1% | 2.95 |
| filter | 1.25 | 2.66 | 2.13 | 2% | 1.53 |
| toList | 0.94 | 1.87 | 1.99 | 2% | 3.62 |
| toArray | 0.92 | 1.51 | 1.64 | 1% | 2.95 |
| zip | 1.70 | 2.60 | 1.53 | 8% | 1.86 |
| flatMap | 9.35 | 13.69 | 1.46 | 5% | 1.46 |
| toMap | 8.74 | 11.07 | 1.27 | 3% | 1.13 |
| sorted | 20.83 | 26.31 | 1.26 | 6% | 2.67 |
| iterator | 0.50 | 0.64 | 1.28 | 3% | 0.22 |
| toSet | 8.25 | 9.55 | 1.16 | 2% | 1.16 |

Size 1,000. Rows omitted where the error exceeded 10% and the direction was
not reproduced: `limit`, `scan`, `windowSliding`, `chain`, `deepChain`.

## Where the library wins

| case | JDK us/op | Seq us/op | time x | +/- | alloc x |
|---|---:|---:|---:|---:|---:|
| drainFilter | 1.44 | 0.55 | 0.38 | 36% | 0.66 |
| summingCollector | 0.98 | 0.46 | 0.47 | 7% | 0.09 |
| iteratorMapped | 8.36 | 4.15 | 0.50 | 2% | 0.98 |
| drain1 | 2.66 | 1.49 | 0.56 | 6% | 0.01 |
| min | 0.89 | 0.50 | 0.57 | 2% | 0.09 |
| drain2 | 8.88 | 3.96 | 0.45 | 20% | 1.00 |
| max | 1.00 | 0.69 | 0.69 | 2% | 0.11 |
| drain4 | 17.09 | 11.88 | 0.70 | 20% | 1.00 |
| range | 2.77 | 2.21 | 0.80 | 1% | 0.01 |
| groupBy | 8.67 | 7.30 | 0.84 | 8% | 0.68 |

The `drainN` cases are `N` chained `map` stages drained with `forEach`, so
the difference between them is stage cost with no terminal buffering.

## At 100,000 elements

| case | time x | +/- | alloc x |
|---|---:|---:|---:|
| limitLast | 11.47 | 6% | 8.44 |
| reversed | 5.13 | 27% | 6.82 |
| skip | 2.42 | 2% | 3.56 |
| toArray | 1.86 | 1% | 3.62 |
| toList | 1.80 | 2% | 3.20 |
| filter | 1.80 | 1% | 1.85 |
| sorted | 1.09 | 7% | 2.53 |
| toSet | 0.96 | 2% | 1.15 |
| toMap | 0.92 | 3% | 1.12 |
| drain2 | 0.59 | 6% | 1.00 |

Two forks. The shape is the same as at 1,000, so these are per-element
costs and not fixed pipeline overhead. `toMap` and `toSet` cross over and
win at this size.

# What the code says

## Buffering terminals: the largest and most deterministic gap

`Sources.toArray(Spliterator)` builds through `ArrayBuilder`, which starts
at length 0 and grows by `length * 2 + 4`, copying each time, then copies
once more in `buildArray` when the final size is not the array length. For
1,000 elements that is lengths 4, 12, 28, 60, 124, 252, 508, 1020, plus the
final exact copy: 3,008 references, about 12,032 bytes. The measured figure
is 12,200 B/op. The JDK allocates once from `estimateSize` when the source
reports `SIZED` and measures 4,136 B/op.

`Sources.toList` has the same problem through a different container. It
appends to an unsized `ArrayList` and wraps the result, so it pays
`ArrayList`'s 1.5x growth chain and measures 15,048 B/op against the JDK's
4,160.

The source in all these cases is an `ArraySource`, which reports `SIZED`
and returns an exact `estimateSize`. Nothing reads it. A presized builder
would close a gap that is 3.2x to 3.6x in allocation and about 1.8x to 2x
in time, and it would improve every case whose terminal is `toList`,
`toArray` or `sorted`. This is the single highest-value change the
benchmarks point to. There is already a comment in `Sources` acknowledging
it, just above `toArray(Iterable)`.

Note that `toArray(Iterable)` and `toArray(Iterator)` do not go through
`ArrayBuilder` at all. They delegate to `StreamSupport`, so they already
get the JDK's exact sizing. Only the spliterator overload grows.

## Stages are fine

`Sources.map` and `Sources.filter` are a single `test` each, with no
per-element object, and `Stage` binds `action` once per traversal rather than
per element. The `drainN` rows show the result: at one, two and four map
stages the library is faster than the JDK, and at one stage it allocated
152 B/op against the JDK's 14,200 because escape analysis removed the
boxing on the library's side and not on the JDK's.

That last point cuts both ways, and it is the main reason to distrust small
time ratios here. Whether C2 inlines a whole pipeline and scalar-replaces
the boxes flips several of these cases by more than the library design
does. It is why `chain` and `deepChain` would not reproduce.

## Whole-source operations ignore SIZED

- `skip` is a `Stage` that tests `index++ < size` for every element,
  including the ones it discards. The JDK slices a `SIZED` and `SUBSIZED`
  source by moving the index, so it never visits them. At 100,000 elements
  this is 2.4x with a 2% error.
- `limitLast` fills an `ArrayBuilder`, converts it to a circular queue and
  takes the modulus per element. On a source that knows its size this is
  the same answer as `skip(size - n)`. It is 8.6x at 1,000 and 11.5x at
  100,000, the worst confirmed ratio that is not `count`.
- `reversed` calls `toArray` and then `Collections.reverse` through an
  `Arrays.asList` view. It inherits the unsized `toArray` growth, which is
  most of its 6.5x allocation.

These three share a cause with the terminals: `estimateSize` and the
`SIZED` characteristic are available and unused.

## count traverses, and that is a semantic difference too

**Settled since: it now answers from the size, as the JDK does.**

`SeqStream.count` calls `Sources.count`, which traverses and increments.
The JDK answers from the source size without running the pipeline. The
140x on `countMapped` is real but it understates the point, because the
behaviours differ as well as the speeds. Verified directly:

```
JDK  count=5 mapper called 0 times
Seq  count=5 mapper called 5 times
```

The JDK behaviour is the documented one and is a known trap for callers who
put side effects in `map`. The library's is arguably the better contract,
but not one worth an undocumented divergence.

## Smaller, explainable gaps

- **concat, 2.9x, and the varargs passes are not the cause.** The first
  guess here was that the three `Arrays.stream` passes over the varargs
  cost something. They do not. Replacing all three with plain loops was
  measured against the original and the two are identical in time and, to
  the byte, in allocation, both with two large parts and with fifty small
  ones. C2 inlines and scalar-replaces a stream over a small local array
  completely. The change was reverted.

  The real cost is per element, not per part, which the two shapes show:
  two parts of 1,000 elements is 2.9x while fifty parts of two elements is
  1.34x. `Sources.concat` wraps each part with `Sources.toSource`, and for
  a foreign stream that is a `SpliteratorSource`, whose `forEachWhile`
  pulls one element at a time through `tryAdvance` into a `Box`. Its own
  javadoc says so. The JDK's concat spliterator hands the whole of each
  side to `forEachRemaining` instead.

  This is the same root cause as `zip` and part of `flatMap`, and it is
  not obviously fixable: a cancellable push cannot use `forEachRemaining`,
  because there is no way to stop it. It is the price of accepting a
  foreign `Spliterator`, and it is paid by every entry point that does,
  including `SeqStream.viewOf(Stream)`.

  It is also not the price of `concat` itself. `Sources.toSource` hands
  back a `Source` unchanged, so concatenating the library's own streams
  never builds the adapter. The `concatNative` case measures that and
  comes out at 0.95x, against 2.72x for the same shape fed JDK streams.
  The original `concat` row was measuring the adapter, not `concat`.
- **dropWhile, 2.7x.** Every element after the predicate first fails still
  reads the `found` field on the anonymous `Stage` before pushing.
- **zip, 1.5x.** `Sources.zip` pulls the second side with `tryAdvance` and
  a `Box` per element. The JDK has no `zip`, so the comparison is against
  hand-written index arithmetic, which is the floor rather than a fair peer.
- **toSet, 1.16x.** `Sources.toSet` uses `LinkedHashSet` where the JDK uses
  `HashSet`. The extra 16% of allocation is the before and after pointers
  in each node. That buys encounter order, which is a deliberate API
  promise, so this one is a cost worth paying rather than a defect.
- **toMap, 1.27x at 1,000.** `Sources.toMap` does `containsKey`, then
  `get`, then `put`: three hash lookups per element where the JDK's
  `Collectors.toMap` does one `merge`. It still wins at 100,000, so the
  triple lookup is not the dominant term there.
- **iterator, 1.28x.** Only when the iterator is taken directly from the
  source. Over a `map` stage the library is 2x faster, because
  `AbstractSource.tryAdvance` reuses one `Once` sink instead of allocating.

## What was measured badly, and how

Two traps were found in the harness itself and are worth recording.

`count()` cannot be used to drain a pipeline in a comparison, because the
JDK skips the pipeline entirely on a sized source. Fifteen cases were
originally written that way and were rewritten to drain with `forEach`.

Short warmup at 100,000 elements is not enough. An earlier pass with 350ms
of warmup reported `strLenSum` at 2.6x; with 5s of warmup the same case is
0.6x. Nothing in the library is size dependent there. It was the JIT.

# Applied since these measurements

Exact sizing from a `SIZED` source in `toArray(Spliterator)`, both
generator overloads and `toList`; a single hash lookup in `toMap`; and an
internal size hint that sees through the size-preserving stages. The
measured `toMap` version also presized its `LinkedHashMap`, but that was
subsequently removed to avoid duplicating hash-table sizing policy. The `concat` change
listed below was tried, measured to make no difference, and reverted.

The size travels through `Spliterator.SIZED` rather than any private
channel, because `Source` is a `Spliterator` and has to keep its rules.
`MappingStage` is a `Stage` that pushes exactly one element for each it
receives; it reports `SIZED` and `SUBSIZED` from upstream and delegates
`estimateSize`, so a partly traversed stage reports what remains.
`map`, `peek`, `mapIndexed` and `scan` are built on it. The distinction
is the type rather than a flag, so a stage that drops, adds or buffers
cannot claim a count it does not have by forgetting to say otherwise.

`SourcesTest` pins it by traversing each shape, counting what it pushes,
and comparing with the count reported beforehand, including the negative
cases that must report unknown. The existing assertion that stages
propagate order was narrowed to exempt the size bits, which these tests
now cover precisely.

| case | time x before | after | alloc x before | after |
|---|---:|---:|---:|---:|
| toList | 1.99 | 1.25 | 3.62 | 0.98 |
| toArray | 1.64 | 0.69 | 2.95 | 0.97 |
| toArrayGenerator | 2.21 | 1.00 | 2.95 | 0.98 |
| toMap | 1.27 | 1.03 | 1.13 | 0.99 |
| map | 2.28 | see below | 1.60 | 1.00 |
| peek | 3.04 | see below | 3.56 | 1.00 |
| sorted | 1.26 | 1.23 | 2.67 | 1.96 |
| reversed | 4.52 | 3.25 | 6.46 | 4.53 |

The `toMap` row measures the single-lookup and presizing changes together;
it is retained as historical data and does not isolate the current
single-lookup implementation.

`sorted` and `reversed` improved only part way in this run: their internal
`toArray` was exact, but they publish through a deferred source rather than
a `Stage`, so the size did not reach the terminal. `Sources.defer` has
since been given a size parameter, so these rows predate the fix and need
re-measuring.

`filter` is unchanged, correctly: it is not size preserving, so the size
stops there.

The time column for `map` and `peek` is not reported because this machine
stopped being quiet enough to measure it. Successive runs of the same
build gave `map` between 1.74 and 1.94 with errors from 1% to 25%, and
`peek` reached a 40% error. The allocation column is deterministic and did
reproduce exactly, so it is the one to trust here. Nothing in the
per-element path changed in any case: the size is read once per pipeline
when the terminal sizes its buffer, so a per-element time difference
between these designs is not possible by construction.

`toSet` was left alone deliberately. See the note in `Sources.toSet`.

## count, size and isEmpty answer from SIZED

`Sources.count` and `Sources.isEmpty` answer from a sized spliterator
without traversing it, and traverse only where the size is unknown; `size`
follows `count`. `CollectionSeq` delegates all three to the collection.
`countMapped` above is stale as a result, and `SourcesTest` now pins the
behaviour rather than the timing. `Seq` is unaffected, as its intermediates
are eager.

# Suggested order of work

1. Presize from `estimateSize` when the source reports `SIZED`, in
   `ArrayBuilder` or at its call sites. Fixes `toList`, `toArray`,
   `toArrayGenerator`, `sorted` and `reversed` at once.
2. Slice rather than traverse in `skip`, and delegate `limitLast` to it
   when the source is `SIZED`.
3. Decide and document the `count` contract.
4. Nothing for `concat` on its own. If the one-at-a-time pull in
   `SpliteratorSource` can be avoided for non-cancelling terminals, that
   would pay across `concat`, `zip`, `flatMap` and every foreign-stream
   entry point at once.

Items 1, 3 and 4 are done, as is the SIZED propagation through
`Sources.defer` that finishes `sorted` and `reversed`. Item 2 is not.
