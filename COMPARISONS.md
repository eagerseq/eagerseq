# Comparison with lodash and Python

How `Seq`'s surface compares with the two ecosystems people reach for when they
want terse list manipulation: lodash (the `Array` and `Collection` modules) and
Python (sequence syntax, builtins, `itertools`, `collections`).

The question this answers is not "does `Seq` have every function those have" —
it shouldn't — but "can a user do the ordinary thing without dropping back into
`stream()` and `Collectors`", which is the verbosity `Seq` exists to remove.

## Method of ranking

There is no honest per-function popularity data. lodash download counts are
per-package; Python has no equivalent at all; measuring real usage would mean
grepping a corpus.

So the ranking below uses **independent convergence** as the proxy: an
operation that JDK `Stream`/`Collectors`, Kotlin's stdlib, lodash and Python all
ship separately is very likely load-bearing, because four API designs with
different tastes each concluded it earned a name. Convergence is evidence, not
proof — a mediocre idea can be copied — so it is used to *rank* candidates, and
judgement is used to reject them. Where the two disagree the disagreement is
stated, not hidden.

The reverse also matters: an operation only one library has is usually one
library's hobby. Scala's collections are the standard warning here, and lodash
has its own tail (`sortedLastIndexBy`, `flattenDepth`, `zipObjectDeep`) that
nobody needs.

## Verdict

**`Seq` covers the sequence-shaped operations well and the
aggregation-shaped operations badly.**

Anything that takes a sequence and returns a sequence of the same elements —
filter, slice, take, drop, reverse, rotate, dedupe, set operations, subsequence
search — is present, usually with a better name than lodash's and with multiset
semantics that lodash and Python's `set` both lack. In this half of the space
`Seq` is *more* complete than either reference, and materially terser than
`Stream`.

Anything that reduces a sequence to a summary keyed or numbered by something —
`groupBy`, `sum`, `average`, `countBy`, `partition` — is absent. These are not
exotic. `groupBy` and numeric `sum` are, in my estimate, in the top five most
used operations in both reference libraries. A user hitting them today writes:

```java
seq.stream().collect(Collectors.groupingBy(Person::getDept));
seq.stream().mapToInt(String::length).sum();
```

which is exactly the `Stream` boilerplate the README opens by rejecting. So the
gap is not "a few missing conveniences"; it is a category of operation where the
library currently does not deliver its stated benefit at all.

Secondary finding: **the tail is over-served relative to the head.** `powerSet`,
`permutations`, `lastIndexOfSlice`, `indexesOfSlice` and `containsMultiset` are
all present, and none of them appears in lodash or Python's builtins. Meanwhile
`groupBy` — which all four reference APIs have — does not. That is an inverted
priority, and worth weighing before adding more combinatorics.

## Coverage tables

Legend: **yes** — direct method. **comp.** — composable in one terse expression,
no `stream()` needed. **no** — requires `stream()`/`Collectors`, or a manual
loop.

### Reduce-to-summary (the weak area)

| Operation | lodash | Python | `Seq` | |
|---|---|---|---|---|
| group by key | `groupBy` | `defaultdict`, `itertools.groupby` | — | **no** |
| index by key | `keyBy` | dict comp. | `toMap(keyMapper)` | yes |
| merge on key collision | — | dict comp. | — | **no** |
| count occurrences by key | `countBy` | `Counter` | — | **no** |
| numeric sum | `sum`, `sumBy` | `sum` | — | **no** |
| average | `mean`, `meanBy` | `statistics.mean` | — | **no** |
| min/max by key | `minBy`, `maxBy` | `min(key=)` | `min(Comparator)` | comp. |
| min/max natural order | `min`, `max` | `min`, `max` | — | **no** |
| partition on predicate | `partition` | — | — | **no** |
| count matching | — | `sum(1 for ...)` | `filter(p).size()` | comp. |
| count of a value | — | `list.count(v)` | `indexesOf(v).size()` | comp. |
| join to string | `join` | `str.join` | `toString(...)` | yes |
| fold / reduce | `reduce` | `functools.reduce` | `reduce` | yes |
| running totals | — | `accumulate` | — | **no** |

### Sequence-to-sequence (the strong area)

| Operation | lodash | Python | `Seq` | |
|---|---|---|---|---|
| map / filter | `map`, `filter` | comp., `map`, `filter` | `map`, `filter` | yes |
| reject | `reject` | `filterfalse` | `filter(p.negate())` | comp. |
| flat map / flatten | `flatMap`, `flatten` | comp., `chain` | `flatMap`, `flatten` | yes |
| take / drop n | `take`, `drop` | `s[:n]`, `s[n:]` | `limit`, `skip` | yes |
| take / drop from end | `takeRight`, `dropRight` | `s[-n:]`, `s[:-n]` | `limitLast`, `skipLast` | yes |
| take / drop while | `takeWhile`, `dropWhile` | `takewhile`, `dropwhile` | `takeWhile`, `dropWhile` | yes |
| ...while, from the end | `takeRightWhile` | — | `reversed()…reversed()` | comp. |
| slice | `slice` | `s[i:j]`, `islice` | `slice` | yes |
| slice with step | — | `s[i:j:k]` | — | **no** |
| reverse | `reverse` | `reversed` | `reversed` | yes |
| sort | `sortBy`, `orderBy` | `sorted` | `sorted(Comparator)` | yes |
| sort by key fn | `sortBy` | `sorted(key=)` | `Comparator.comparing` | **no**¹ |
| distinct | `uniq` | `set`, `dict.fromkeys` | `distinct` | yes |
| distinct by key fn | `uniqBy` | — | — | **no** |
| concat | `concat` | `s + t` | `concat`, `sum` | yes |
| zip | `zip`, `zipWith` | `zip` | `zip(that, mapper)` | yes |
| zip unequal lengths | — | `zip_longest` | — | no² |
| enumerate | — | `enumerate` | `zip(indexes(), f)` | comp. |
| shuffle | `shuffle` | `random.shuffle` | `shuffled` | yes |
| sample n | `sampleSize` | `random.sample` | `shuffled().limit(n)` | comp. |
| rotate | — | — | `rotated` | yes |
| chunk into blocks of n | `chunk` | `batched` | — | **no** |
| sliding window | — | `pairwise` | — | **no** |
| cartesian product | — | `product` | — | **no** |
| compact / drop nulls | `compact` | comp. | `filter(nonNull)` | comp. |
| deep flatten | `flattenDeep` | — | — | no² |
| unzip | `unzip` | `zip(*xs)` | — | no² |

¹ `sorted(Comparator.comparing(Person::getName))` works and is not terrible, but
it is the one place a `Stream`-shaped incantation survives in otherwise clean
`Seq` code. A `sortedBy(Function)` overload removes it.
² Blocked or made pointless by Java's type system rather than by an oversight —
see below.

### Search and predicates

| Operation | lodash | Python | `Seq` | |
|---|---|---|---|---|
| contains | `includes` | `in` | `contains` | yes |
| any / all / none | `some`, `every` | `any`, `all` | `anyMatch`, `allMatch`, `noneMatch` | yes |
| index of value | `indexOf`, `lastIndexOf` | `list.index` | `indexOf`, `lastIndexOf`, `indexesOf` | yes |
| index of match | `findIndex`, `findLastIndex` | — | — | **no** |
| first / last element | `head`, `last` | `s[0]`, `s[-1]` | `findFirst`, `findLast` | yes |
| first match | `find` | `next(x for ...)` | `filter(p).findFirst()` | comp. |
| last match | `findLast` | — | `filter(p).findLast()` | comp. |
| exactly-one match | — | — | `findOnly` | yes |
| subsequence search | — | — | `indexOfSlice`, `containsSlice` | yes |
| starts / ends with | — | `str` only | `startsWith`, `endsWith` | yes |
| binary search on sorted | `sortedIndex` family | `bisect` | — | no³ |

³ Genuinely useful in both references, but it needs sortedness the type system
cannot express, and `Seq` has no sorted-sequence type. Reasonable to skip.

### Set and multiset operations

Here `Seq` is ahead of both references. `intersection`, `difference` and `union`
use multiset semantics with documented encounter order; lodash's are set-only,
and Python requires converting to `set` and losing order. `containsMultiset` has
no counterpart in either. Only `xor` (symmetric difference) is missing, and
`a.difference(b).sum(b.difference(a))` covers it.

### Factories

| | lodash | Python | `Seq` |
|---|---|---|---|
| range | `range` | `range(a, b)` | `range(from, to)` |
| range with step | `range(a,b,step)` | `range(a, b, k)` | — |
| inclusive range | `rangeRight` | — | — |
| repeat element n times | `times`, `fill` | `[x] * n` | — |
| unbounded generate | — | `count`, `cycle`, `repeat` | — |

The unbounded ones only make sense on `SeqStream`, which has no `iterate` or
`generate` either — so `SeqStream` cannot currently express an infinite source
at all, unlike the `Stream` it wraps.

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
  far terser than `stream().mapToInt(f).sum()`.
- **Erasure.** `flattenDeep` has no sound signature. Skip it.

## Two naming problems worth deciding before the gaps get filled

1. **`sum(Iterable)` means concatenation.** In lodash, Python, Kotlin and
   `Stream`, `sum` is the numeric reduction. It is currently the one name on
   `Seq` that means something different from what every reference library
   trains users to expect — and it occupies the name the top-priority missing
   feature wants. `concat` already exists as the static form; the instance
   method could be `then`, `append`, `plus` or `concat`, freeing `sum` for the
   numeric terminal.

2. **`count()` means size.** That follows `Stream`, so it should stay — but it
   means a lodash-style `countBy` and a Python-style `count(value)` would both
   sit awkwardly next to it. Prefer `frequencies()` or `groupBy(f, counting)`
   over an overloaded `count`.

## Ranked gaps

**Tier 1 — the library does not meet its goal without these.** All four
reference APIs have each one; each currently forces `stream()` + `Collectors`.

- `groupBy(Function)` returning `Map<K, Seq<E>>`, plus
  `toMap(key, value, merge)`.
- Numeric terminals: `sum(ToIntFunction)` / `sum(ToLongFunction)` /
  `sum(ToDoubleFunction)` and `average(...)`. Requires resolving the `sum` name
  clash above.
- `sortedBy(Function)` — the last place `Stream` idiom leaks into `Seq` code.
- `min()` / `max()` natural-order overloads. `sorted()` already has the pair;
  these are a few lines and their absence is plainly an oversight.

**Tier 2 — common, and each removes a real workaround.**

- `partition(Predicate)`.
- `frequencies()` / `countBy(Function)`.
- `chunked(n)`. In lodash, Python (3.12+) and Kotlin; not in `Stream`.
- `distinctBy(Function)`.
- `find(Predicate)` / `findLast(Predicate)`. `filter(p).findFirst()` is already
  terse, so this is convenience rather than necessity — but it is also the
  cheapest entry on the list, and it avoids materialising the filtered `Seq`.

**Tier 3 — real but narrower.**

- `windowed(n)` / `pairwise()`.
- `scan` / running reduce.
- `range` with step; `rangeClosed`; `repeat(e, n)`.
- `iterate` / `generate` on `SeqStream` (currently no infinite source exists).
- `product(other)` — cartesian product; it belongs with the combinatorics the
  library already invests in, and its absence there is odd given `powerSet` is
  present.
- `slice` with step.

**Not worth adding.** `flattenDeep`, `unzip`, `starmap`, `zipObject`,
`zip_longest`, `orderBy`, `sample`, `xor`, the `sortedIndex`/`bisect` family,
lodash's whole `*By`/`*With` comparator-variant scheme, and `tee` — which `Seq`
makes unnecessary, since a `Seq` is re-traversable by construction. That last
one is a genuine advantage over Python worth stating in the README.
