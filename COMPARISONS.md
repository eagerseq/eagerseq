# Comparison with Guava, lodash and Python

How `Seq`'s surface compares with the libraries people reach for when they want
to manipulate collections without ceremony: Guava, lodash (the `Array` and
`Collection` modules) and Python (sequence syntax, builtins, `itertools`,
`collections`).

`Seq` has two goals. The first is terseness — can a user do the ordinary thing
without dropping back into `stream()` and `Collectors`, which is the verbosity
`Seq` exists to remove. The second is consolidation: putting the common
operations on collection-like things on a single type, even when the JDK
already has them somewhere. `reversed()` exists despite `Collections.reverse`;
`intersection` exists despite `retainAll`; `indexOfSlice` exists despite
`Collections.indexOfSubList`. Any operation common enough earns a place on
`Seq` regardless of whether it is reachable some other way.

The question this answers is not "does `Seq` have every function these
libraries have" — it shouldn't.

Guava is the reference that speaks to the second goal. It is the most
exhaustive Java collection library there is, so it answers two questions at
once: which operations are load-bearing enough that a serious Java library
shipped them, and what the cost is of shipping them the way Guava does.

## Method of ranking

There is no honest per-function popularity data. lodash download counts are
per-package; Python has no equivalent at all; measuring real usage would mean
grepping a corpus.

So the ranking below uses **independent convergence** as the proxy, over five
API designs: JDK `Stream`/`Collectors`, Kotlin's stdlib, lodash, Python and
Guava. An operation all five ship separately is very likely load-bearing,
because five designs with different tastes each concluded it earned a name.
Convergence is evidence, not proof — a mediocre idea can be copied — so it is
used to *rank* candidates, and judgement is used to reject them. Where the two
disagree the disagreement is stated, not hidden.

The reverse also matters: an operation only one library has is usually that
library's hobby. Scala's collections are the standard warning here, and both
lodash (`sortedLastIndexBy`, `flattenDepth`, `zipObjectDeep`) and Guava
(`Iterables.paddedPartition`) have tails of their own.

Guava carries extra weight for one specific question and no weight for another.
It is the strongest available evidence that an operation *belongs in a Java
library specifically*, because Guava's authors already weighed it against Java's
type system, erasure and boxing. It is weak evidence about *naming and shape*,
because Guava predates lambdas and `Stream` and its API reflects that.

## Verdict

**`Seq` covers the sequence-shaped operations well and the aggregation-shaped
operations badly. Guava confirms the consolidation thesis and shows where `Seq`
is short.**

Anything that takes a sequence and returns a sequence of the same elements —
filter, slice, take, drop, reverse, rotate, dedupe, set operations, subsequence
search — is present, usually with a better name than lodash's and with multiset
semantics that lodash and Python's `set` both lack. In this half of the space
`Seq` is *more* complete than either reference, and materially terser than
`Stream`.

Anything that reduces a sequence to a summary keyed or numbered by something —
`sum`, `average`, `countBy`, `partition` — is largely absent. `groupBy` was the
worst of these and is now filled; the rest are not exotic either. A user hitting
one of them today writes:

```java
seq.stream().mapToInt(String::length).sum();
```

which is exactly the `Stream` boilerplate the README opens by rejecting. So the
gap is not "a few missing conveniences"; it is a category of operation where the
library still does not fully deliver its stated benefit.

Beyond that, three findings from the Guava comparison.

1. **Guava has nearly everything, spread across a dozen static utility
   classes.** `Iterables`, `Iterators`, `Lists`, `Sets`, `Maps`, `Multimaps`,
   `Multisets`, `Collections2`, `Streams`, `Comparators`, `MoreCollectors`,
   `Ordering`. Finding an operation means first guessing which class holds it,
   and the answer is keyed off the receiver's *type*, not off what you want to
   do — `Lists.partition` chunks a list, `Iterables.partition` chunks an
   iterable, `Sets.cartesianProduct` and `Lists.cartesianProduct` are separate
   methods with different return types. That fragmentation is exactly the
   problem `Seq` solves by being one type with instance methods. This is the
   strongest argument for the library that exists, and the README does not make
   it.

2. **Guava independently validates several operations that otherwise look like
   `Seq`'s idiosyncratic tail.** `getSingle`/`findSingle` is
   `Iterables.getOnlyElement` and `MoreCollectors.onlyElement`.
   `containsMultiset` is `Multisets.containsOccurrences`. Multiset
   `intersection`/`difference`/`union`/`sum` are the four `Multisets` statics,
   with the same names. Combinatorics are `Sets.powerSet`,
   `Sets.combinations(k)`, `Collections2.permutations` and
   `orderedPermutations`. None of these are hobbies; a second serious library
   shipped each one.

3. **The remaining Tier 1 gaps get worse, not better.** Guava has
   `Multimaps.index` (group by), `Multiset` (count by),
   `Iterables.frequency` (count of a value), `Ordering.min`/`max` natural
   order, `Ordering.onResultOf` (sort by key), and `Lists.partition` (chunk).
   `Seq` now fills the grouping gap with `groupBy`; chunking remains present in
   all five reference libraries. Numeric `sum` is the one Tier 1 item Guava
   does *not* endorse — it has `Ints.max`/`min` on primitive arrays and nothing
   sequence-shaped — which is a mild argument that Java's boxing makes the
   feature less obviously worth it than lodash and Python suggest.

## Coverage tables

Legend: **yes** — direct method. **comp.** — composable in one terse
expression, no `stream()` needed. **no** — requires `stream()`/`Collectors`, a
JDK static, or a manual loop.

### Reduce-to-summary (the weak area)

| Operation | Guava | lodash | Python | `Seq` | |
|---|---|---|---|---|---|
| group by key | `Multimaps.index` | `groupBy` | `defaultdict` | `groupBy` | yes |
| index by key | `Maps.uniqueIndex` | `keyBy` | dict comp. | `toMap(keyMapper)` | yes |
| map from key+value fns | `Maps.toMap` | — | dict comp. | `toMap(k, v)` | yes |
| merge on key collision | — | — | dict comp. | `toMap(k, v, merge)` | yes |
| count occurrences by key | `Multiset` | `countBy` | `Counter` | — | **no** |
| count of one value | `Iterables.frequency` | — | `list.count` | `indexesOf(v).size()` | comp. |
| numeric sum | — | `sum`, `sumBy` | `sum` | — | **no** |
| average | — | `mean` | `statistics.mean` | — | **no** |
| min/max by comparator | `Ordering.min/max` | `minBy` | `min(key=)` | `min(Comparator)` | yes |
| min/max natural order | `Ordering.natural().min` | `min`, `max` | `min`, `max` | — | **no** |
| top / bottom k | `Comparators.greatest` | — | `heapq.nlargest` | `sorted(c).limit(k)` | comp. |
| partition on predicate | — | `partition` | — | — | **no** |
| count matching | `Iterables.size(filter)` | — | `sum(1 for ...)` | `filter(p).size()` | comp. |
| join to string | `Joiner` | `join` | `str.join` | `toString(...)` | yes |
| fold / reduce | — | `reduce` | `reduce` | `reduce` | yes |
| running totals | — | — | `accumulate` | — | **no** |

`Joiner` is worth a note: Guava built a whole configurable object
(`Joiner.on(",").skipNulls().useForNull("?")`) for what `Seq` does with
`toString(sep, prefix, suffix)`. The `Seq` form covers the common case and
`map(...).toString(...)` covers the rest. No gap.

### Sequence-to-sequence (the strong area)

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
| distinct by key fn | — | `uniqBy` | — | — | **no** |
| concat | `Iterables.concat` | `concat` | `s + t` | `concat`, `sum` | yes |
| zip | `Streams.zip` | `zip` | `zip` | `zip(that, mapper)` | yes |
| enumerate | `Streams.mapWithIndex` | — | `enumerate` | `zip(indexes(), f)` | comp. |
| shuffle | — | `shuffle` | `random.shuffle` | `shuffled` | yes |
| rotate | — | — | — | `rotated` | yes |
| chunk into blocks of n | `Lists.partition` | `chunk` | `batched` | — | **no** |
| sliding window | — | — | `pairwise` | — | **no** |
| cartesian product | `Lists.cartesianProduct` | — | `product` | `product(that, mapper)` | yes |
| merge two sorted | `Iterables.mergeSorted` | — | `heapq.merge` | — | no² |
| cycle | `Iterables.cycle` | — | `cycle` | — | **no**³ |
| compact / drop nulls | `filter(notNull)` | `compact` | comp. | `filter(nonNull)` | comp. |
| deep flatten | — | `flattenDeep` | — | — | no⁴ |
| unzip | — | `unzip` | `zip(*xs)` | — | no⁴ |

¹ `sorted(Comparator.comparing(Person::getName))` works; Guava spelling it
`Ordering.natural().onResultOf(f)` is not better. A `sortedBy(Function)`
overload beats both.
² Needs sortedness the type system cannot express — same reason as binary
search. Guava is the only reference with it and its contract is "results are
undefined if inputs aren't sorted", which is the kind of API `Seq` should not
copy.
³ `SeqStream` only; an eager `Seq` cannot hold it.
⁴ Blocked by erasure and the absence of tuples — see "What Java's type system
makes unreachable" below.

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
| exactly-one element | `getOnlyElement` | — | — | `findSingle`, `getSingle` | yes |
| subsequence search | — | — | — | `indexOfSlice`, `containsSlice`, `lastIndexOfSlice`, `indexesOfSlice` | yes |
| starts / ends with | — | — | `str` only | `startsWith`, `endsWith` | yes |
| elements equal | `Iterables.elementsEqual` | `isEqual` | `==` | `listEquals` | yes |
| is sorted | `Comparators.isInOrder` | — | — | — | no⁵ |
| binary search on sorted | `Ordering.binarySearch` | `sortedIndex` | `bisect` | — | no⁵ |

⁵ Both are cheap and neither needs a sorted *type* — `isInOrder` is just a
predicate. `isSorted(Comparator)` is a defensible small addition;
`binarySearch` still is not, because a wrong answer on unsorted input is silent.

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

Only `symmetricDifference` is missing, and
`a.difference(b).sum(b.difference(a))` covers it. Two references now have it,
so it moves from "not worth adding" to Tier 3.

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

## What Java's type system makes unreachable

Not every gap is fixable, and it would be a mistake to chase these:

- **No tuples.** `zip` returning pairs, `unzip`, `starmap`, `fromPairs`,
  `zipObject` and `pairwise`-returning-pairs all depend on a cheap anonymous
  product type. `zip(that, mapper)` is the correct Java answer and `Seq` already
  has it. `windowed(n)` returning `Seq<Seq<E>>` sidesteps the issue and is still
  worth having.
- **No slice syntax.** `s[i:j:k]` will never be one character. `slice(from, to)`
  is as close as Java gets.
- **Primitive boxing.** `sum`/`average` on `Seq<Integer>` cannot be as clean as
  Python's `sum`. The realistic shape is `sum(ToIntFunction)` — which is still
  far terser than `stream().mapToInt(f).sum()`. Guava's abstention here is the
  one piece of evidence that this cost is real.
- **Erasure.** `flattenDeep` has no sound signature. Skip it.

## What Guava has that `Seq` should not take

Guava is exhaustive, and most of the excess is excess for a reason `Seq` should
respect:

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

## Two naming problems worth deciding before the gaps get filled

1. **`sum(Iterable)` means concatenation.** In lodash, Python, Kotlin and
   `Stream`, `sum` is the numeric reduction, so this is the one name on `Seq`
   that means something different from what most reference libraries train
   users to expect — and it occupies the name the top-priority missing feature
   wants. But Guava is a genuine counterexample: `Multisets.sum` is its name for
   the same operation on the same semantics. So the prior art is split, and the
   case for renaming rests on terseness and on freeing the name, not on `Seq`
   contradicting everyone else. `concat` already exists as the static form; the
   instance method could be `then`, `append`, `plus` or `concat`.

2. **`count()` means size.** That follows `Stream`, so it should stay — but it
   means a lodash-style `countBy` and a Python-style `count(value)` would both
   sit awkwardly next to it. Prefer `frequencies()` or `groupBy(f, counting)`
   over an overloaded `count`.

## Ranked gaps

**Tier 1 — five of five references have each one.**

- `sortedBy(Function)` — the last place `Stream` idiom leaks into `Seq` code.
- `min()` / `max()` natural-order overloads. `sorted()` already has the pair.
- `chunked(n)`. `Lists.partition`, lodash `chunk`, Python `batched`, Kotlin
  `chunked`. Only `Stream` lacks it.
- Numeric terminals: `sum(ToIntFunction)` and `average(...)`. Four of five;
  Guava abstains, which is worth noting but not enough to demote it — Guava
  also predates `ToIntFunction`. Requires resolving the `sum` name clash above.

**Tier 2 — common, each removes a real workaround.**

- `frequencies()` / `countBy(Function)`. Guava dedicates a whole type
  (`Multiset`) to this.
- `partition(Predicate)`.
- `distinctBy(Function)`.
- `find(Predicate)` / `findLast(Predicate)`. Guava has `Iterables.find`,
  `tryFind` and `Streams.findLast`; `filter(p).findFirst()` is already terse,
  so this is about avoiding the intermediate `Seq`.
- `indexOf(Predicate)` — `Iterables.indexOf` takes a predicate and lodash has
  `findIndex`. `Seq` has the value-based family already, so this is one
  overload.

**Tier 3 — real but narrower.**

- `windowed(n)` / `pairwise()`.
- `scan` / running reduce.
- `symmetricDifference` — promoted from "not worth adding" on Guava's evidence.
- `isSorted()` / `isSorted(Comparator)`.
- `greatest(k, Comparator)` / `least(k, ...)` — top-k without a full sort.
  Guava and `heapq` both have it; a naive `sorted().limit(k)` already works, so
  this is a performance affordance, not an expressiveness one.
- `range` with step.
- `cycle` on `SeqStream`.
- `slice` with step.

**Not worth adding.** `flattenDeep`, `unzip`, `starmap`, `zipObject`,
`zip_longest`, `sample`, `mergeSorted`, `paddedPartition`, the
`sortedIndex`/`bisect`/`binarySearch` family, lodash's `*By`/`*With`
comparator-variant scheme, Guava's `Ordering` beyond top-k, and `tee` — which
`Seq` makes unnecessary, since a `Seq` is re-traversable by construction.

## What the README should say

Two claims are currently missing from it, and Guava supplies the evidence for
both.

1. **One type, not a dozen utility classes.** Guava's operations are correct
   and complete and you still have to know that chunking a list is
   `Lists.partition`, that grouping is `Multimaps.index`, that "exactly one
   element" is `Iterables.getOnlyElement`, and that multiset intersection is
   `Multisets.intersection`. On `Seq` these are methods on the value you
   already hold, found by autocomplete. Terseness is the first half of the
   pitch; discoverability is the second, and it is the one the README omits.

2. **Snapshot semantics by default, laziness on request.** Guava's views are
   lazy and alias their sources; `Stream` is lazy and single-use. `Seq` is
   neither — it is a re-traversable value, and `stream()` is there when
   laziness is actually wanted. That is a real design position and it is
   currently only visible in the implementation notes.
