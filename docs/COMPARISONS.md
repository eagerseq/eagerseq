# Comparison with Guava, lodash and Python

How `Seq`'s surface compares with the libraries people reach for when they want
to manipulate collections without ceremony: Guava, lodash (the `Array` and
`Collection` modules) and Python (sequence syntax, builtins, `itertools`,
`collections`).

This is historical API-comparison research, not required onboarding, a roadmap,
or an authoritative inventory. The Seq entries below have been reconciled
with the implementation during the documentation consolidation; comparisons
with other libraries have not been re-researched. Rankings and judgments record
the original exploration rather than commitments to add methods.

Current design principles live in [DESIGN.md](DESIGN.md#api-design-principles), active
work in [TODO.md](../TODO.md), and search proposal analysis in
[DIRECT_MATCHING.md](DIRECT_MATCHING.md). Read the interfaces for current API
availability.

## Original comparison method

There is no honest per-function popularity data. lodash download counts are
per-package; Python has no equivalent at all; measuring real usage would mean
grepping a corpus.

The original exploration used **independent convergence** as the proxy, over five
API designs: JDK `Stream`/`Collectors`, Kotlin's stdlib, lodash, Python and
Guava. An operation all five ship separately is very likely load-bearing,
because five designs with different tastes each concluded it earned a name.
Convergence is evidence, not proof — a mediocre idea can be copied — so it was
used to *rank* candidates alongside judgments about their suitability for Java.

The reverse also matters: an operation only one library has is usually that
library's hobby. Scala's collections are the standard warning here, and both
lodash (`sortedLastIndexBy`, `flattenDepth`, `zipObjectDeep`) and Guava
(`Iterables.paddedPartition`) have tails of their own.

Guava carries extra weight for one specific question and no weight for another.
It is the strongest available evidence that an operation *belongs in a Java
library specifically*, because Guava's authors already weighed it against Java's
type system, erasure and boxing. It is weak evidence about *naming and shape*,
because Guava predates lambdas and `Stream` and its API reflects that.

## Findings in context

The original comparison highlighted consolidation: familiar operations spread
across utility classes elsewhere are discoverable as methods on `Seq`.
It also identified aggregation and windowing gaps. Several have since been
filled: primitive mapper sums/products, natural-order extrema, `distinctBy`,
fixed/sliding windows and `scan`. Averages, keyed counting and projected sorting
remain subjects for evaluation, not commitments implied by this comparison.

## Coverage tables

Legend: **yes** — direct method. **comp.** — composable in one terse
expression, no `stream()` needed. **no** — requires `stream()`/`Collectors`, a
JDK static, or a manual loop.

### Reduce-to-summary

| Operation | Guava | lodash | Python | `Seq` | |
|---|---|---|---|---|---|
| group by key | `Multimaps.index` | `groupBy` | `defaultdict` | `groupBy` | yes |
| index by key | `Maps.uniqueIndex` | `keyBy` | dict comp. | `toMap(keyMapper)` | yes |
| map from key+value fns | `Maps.toMap` | — | dict comp. | `toMap(k, v)` | yes |
| merge on key collision | — | — | dict comp. | `toMap(k, v, merge)` | yes |
| count occurrences by key | `Multiset` | `countBy` | `Counter` | — | **no** |
| count of one value | `Iterables.frequency` | — | `list.count` | `frequency` | yes |
| numeric sum | — | `sum`, `sumBy` | `sum` | `sumOfInt`, `sumOfLong`, `sumOfDouble` | yes |
| average | — | `mean` | `statistics.mean` | — | **no** |
| min/max by comparator | `Ordering.min/max` | `minBy` | `min(key=)` | `min(Comparator)` | yes |
| min/max natural order | `Ordering.natural().min` | `min`, `max` | `min`, `max` | `min()`, `max()` | yes |
| top / bottom k | `Comparators.greatest` | — | `heapq.nlargest` | `sorted(c).limit(k)` | comp. |
| partition on predicate | — | `partition` | — | `partitionBy` | yes |
| count matching | `Iterables.size(filter)` | — | `sum(1 for ...)` | `filter(p).size()` | comp. |
| join to string | `Joiner` | `join` | `str.join` | `toString(...)` | yes |
| fold / reduce | — | `reduce` | `reduce` | `reduce` | yes |
| running totals | — | — | `accumulate` | `scan` | yes |

`Joiner` is worth a note: Guava built a whole configurable object
(`Joiner.on(",").skipNulls().useForNull("?")`) for what `Seq` does with
`toString(sep, prefix, suffix)`. The `Seq` form covers the common case and
`map(...).toString(...)` covers the rest. No gap.

### Sequence-to-sequence

| Operation | Guava | lodash | Python | `Seq` | |
|---|---|---|---|---|---|
| map / filter | `Iterables.transform`, `filter` | `map`, `filter` | comp. | `map`, `filter` | yes |
| flat map / flatten | `Iterables.concat(transform)` | `flatMap` | `chain` | `flatMap`, `flatten` | yes |
| take / drop n | `Iterables.limit`, `skip` | `take`, `drop` | `s[:n]`, `s[n:]` | `limit`, `skip` | yes |
| take / drop from end | — | `takeRight` | `s[-n:]` | `limitLast`, `skipLast` | yes |
| take / drop while | — | `takeWhile` | `takewhile` | `takeWhile`, `dropWhile` | yes |
| slice | — | `slice` | `s[i:j]` | `slice` | yes |
| slice with step | — | — | `s[i:j:k]` | — | **no** |
| reverse | `Lists.reverse` | `reverse` | `reversed` | `reversed` | yes |
| sort | `Ordering.sortedCopy` | `sortBy` | `sorted` | `sorted(Comparator)` | yes |
| sort by key fn | `Ordering.onResultOf` | `sortBy` | `sorted(key=)` | `Comparator.comparing` | **no**¹ |
| distinct | `ImmutableSet.copyOf` | `uniq` | `dict.fromkeys` | `distinct` | yes |
| distinct by key fn | — | `uniqBy` | — | `distinctBy` | yes |
| concat | `Iterables.concat` | `concat` | `s + t` | `concat`, `sum` | yes |
| zip | `Streams.zip` | `zip` | `zip` | `zip(that, mapper)` | yes |
| enumerate | `Streams.mapWithIndex` | — | `enumerate` | `mapIndexed` | yes |
| shuffle | — | `shuffle` | `random.shuffle` | `shuffled` | yes |
| rotate | — | — | — | `rotated` | yes |
| chunk into blocks of n | `Lists.partition` | `chunk` | `batched` | `windowFixed` | yes |
| sliding window | — | — | `pairwise` | `windowSliding` | yes |
| cartesian product | `Lists.cartesianProduct` | — | `product` | `product(that, mapper)` | yes |
| merge two sorted | `Iterables.mergeSorted` | — | `heapq.merge` | — | no² |
| cycle | `Iterables.cycle` | — | `cycle` | — | **no**³ |
| compact / drop nulls | `filter(notNull)` | `compact` | comp. | `filter(nonNull)` | comp. |
| deep flatten | — | `flattenDeep` | — | — | no⁴ |
| unzip | — | `unzip` | `zip(*xs)` | — | no⁴ |

¹ `sorted(Comparator.comparing(Person::getName))` works. The potential benefit
of `sortedBy(Function)` is discussed in [EQUALITY_AND_ORDERING.md](EQUALITY_AND_ORDERING.md#ordering).
² Needs sortedness the type system cannot express — same reason as binary
search. Guava is the only reference with it and its contract is "results are
undefined if inputs aren't sorted"; the original comparison treated that
precondition as a reason against adding it.
³ `SeqStream` only; an eager `Seq` cannot hold it.
⁴ The original objections concerned erasure and the absence of tuples — see
"Java constraints considered in the comparison" below.

### Search and predicates

| Operation | Guava | lodash | Python | `Seq` | |
|---|---|---|---|---|---|
| contains | `Iterables.contains` | `includes` | `in` | `contains` | yes |
| any / all / none | `Iterables.any`, `all` | `some`, `every` | `any`, `all` | `anyMatch`… | yes |
| index of value | — | `indexOf` | `list.index` | `indexOf`, `lastIndexOf`, `indexesOf` | yes |
| index of match | `Iterables.indexOf(pred)` | `findIndex` | — | — | **no** |
| element at index | `Iterables.get` | `nth` | `s[i]` | `get` | yes |
| first / last element | `getFirst`, `getLast` | `head`, `last` | `s[0]`, `s[-1]` | `getFirst`, `getLast`, `findFirst`, `findLast` | yes |
| first match | `Iterables.find`, `tryFind` | `find` | `next(...)` | `filter(p).findFirst()` | comp. |
| last match | `Streams.findLast` | `findLast` | — | `filter(p).findLast()` | comp. |
| exactly-one element | `getOnlyElement` | — | — | `findOnly`, `getOnly` | yes |
| subsequence search | — | — | — | `indexOfSlice`, `containsSlice`, `lastIndexOfSlice`, `indexesOfSlice` | yes |
| starts / ends with | — | — | `str` only | `startsWith`, `endsWith` | yes |
| elements equal | `Iterables.elementsEqual` | `isEqual` | `==` | `listEquals` | yes |
| is sorted | `Comparators.isInOrder` | — | — | — | no⁵ |
| binary search on sorted | `Ordering.binarySearch` | `sortedIndex` | `bisect` | — | no⁵ |

⁵ Both are cheap and neither needs a sorted *type* — `isInOrder` is just a
predicate. The original comparison favored `isSorted(Comparator)` over
`binarySearch`, because a wrong answer on unsorted input is silent.

### Set and multiset operations

This is where Guava is closest to `Seq` and the comparison is most useful.

| Operation | Guava (sets) | Guava (multisets) | `Seq` |
|---|---|---|---|
| union | `Sets.union` | `Multisets.union` | `union` |
| intersection | `Sets.intersection` | `Multisets.intersection` | `intersection` |
| difference | `Sets.difference` | `Multisets.difference` | `difference` |
| sum / concat | — | `Multisets.sum` | `sum` |
| containment | `containsAll` | `Multisets.containsOccurrences` | `containsMultiset` |
| symmetric difference | `Sets.symmetricDifference` | — | — |

Guava ships both families and `Seq`'s names match the multiset ones exactly,
including `sum` meaning concatenation. That bears directly on the naming
question below.

`Seq`'s advantage over both Guava families is that it needs neither a `Set` nor
a `Multiset` receiver and preserves documented encounter order. Getting
Guava's multiset semantics means constructing `HashMultiset` copies of both
operands and losing order.

`symmetricDifference` has no direct method;
`a.difference(b).sum(b.difference(a))` expresses it through composition.

### Factories

| | Guava | lodash | Python | `Seq` |
|---|---|---|---|---|
| range | `ContiguousSet.create(Range…)` | `range` | `range(a, b)` | `range(from, to)` |
| range with step | — | `range(a,b,step)` | `range(a, b, k)` | — |
| inclusive range | `Range.closed` | — | — | `rangeClosed(from, to)` |
| repeat element n times | `Collections.nCopies` (JDK) | `fill` | `[x] * n` | `repeat(e, n)` |
| n results of a function | — | `times` | comp. | `generate(s, n)` |
| bounded recurrence | — | — | — | `iterate(seed, hasNext, next)` |
| unbounded generate | `Iterables.cycle` | — | `count`, `cycle` | `SeqStream.generate(s)` |
| unbounded repeat | `Iterables.cycle` | — | `repeat(x)` | `SeqStream.repeat(e)` |
| builder | `ImmutableList.Builder` | — | — | `Seq.builder()` |
| collector | `ImmutableList.toImmutableList` | — | — | `Seq.toSeq()` |

Guava's `Range` is a different and larger idea — a first-class interval type
with `RangeSet` and `RangeMap` — and is out of scope. Its existence is not
evidence that `Seq` needs `rangeClosed`; Python and lodash are.

lodash's `times(n, f)` passes the index to its iteratee, so it belongs with
`generate`, not with `repeat`; `fill` is the constant-element one.

The unbounded factories only make sense on `SeqStream`, which now has
`generate`, `repeat` and both forms of `iterate`. Only `cycle` is missing, and
it is a different operation — it repeats a whole sequence rather than one
element.

## Java constraints considered in the comparison

The exploration identified these constraints on API shape:

- **No tuples.** `zip` returning pairs, `unzip`, `starmap`, `fromPairs`,
  `zipObject` and `pairwise`-returning-pairs all depend on a cheap anonymous
  product type. `Seq` instead provides `zip(that, mapper)` and represents
  windows from `windowFixed` and `windowSliding` as reusable `Seq` values.
- **No slice syntax.** `s[i:j:k]` will never be one character. `slice(from, to)`
  is as close as Java gets.
- **Primitive boxing.** Seq uses primitive mapper terminals such as
  `sumOfInt(ToIntFunction)` instead of `stream().mapToInt(f).sum()`.
- **Erasure.** The exploration found no sound typed signature for `flattenDeep`.

## Guava features the comparison did not favor adopting

The original comparison argued against adopting the following features:

- **Type-keyed duplication.** `Lists.partition` / `Iterables.partition` /
  `Iterators.partition`, and the parallel `Sets`/`Lists` cartesian products.
  `Seq` has one type; this whole axis vanishes.
- **`Ordering`.** Almost entirely superseded by `Comparator` since Java 8.
  Only `greatestOf`/`leastOf` (top-k, without a full sort) and `isInOrder`
  survive as ideas.
- **Live views everywhere.** `Sets.union`, `Lists.reverse`,
  `Iterables.transform` return lazy views over the originals, which is a
  frequent source of surprise (`Iterables.transform` re-applies the function on
  every traversal). `Seq`'s snapshot semantics are the better default, and
  `stream()` covers the case where laziness is genuinely wanted.
- **Predicate/Function-based `filterKeys`, `filterEntries`, `transformValues`.**
  Map-shaped, not sequence-shaped. `groupBy` deliberately returns an ordinary
  `Map`, so these remain out of scope.
- **`Table`, `BiMap`, `RangeSet`, `ClassToInstanceMap`, `Multimap` as a public
  type.** New data structures, not operations on existing ones.
- **`paddedPartition`, `consumingIterable`, `mergeSorted`, `Iterators.advance`,
  `peekingIterator`.** Guava's own tail.

## Naming questions from the comparison

`sum(Iterable)` means concatenation, following multiset terminology. Numeric
terminals now use `sumOfInt`, `sumOfLong` and `sumOfDouble`, with matching
primitive product terminals; the earlier suggestion that numeric sums required
renaming concatenation is obsolete.

`count()` retains the JDK stream meaning. Predicate, value and keyed counting
have separate design considerations; see [DIRECT_MATCHING.md](DIRECT_MATCHING.md)
rather than treating this comparison's former gap rankings as an API plan.

## Historical candidates

The exploration also considered top-k selection, symmetric difference,
`isSorted`, stepped ranges/slices and cycling. Their appearance here does not
establish priority or approval. Current candidates are maintained in
[TODO.md](../TODO.md); fulfilled gaps and duplicate rankings have been removed.
