# Collection operations review: concise version

A standalone decision aid based on the 20 September 2026 investigation, not a
roadmap. The baseline includes the working-tree additions to commit `0e50ed9`:
`frequency`, `disjoint`, `symmetricDifference`, `isSorted`, `distinctBy`, numeric
mapper terminals, windows and `scan` already exist.

Comparisons prioritise **JDK 25**, then **Guava 33.4.x**, with **Scala 2.13.16**
as a secondary reference. These are the researched documentation versions, not
a claim of exhaustive coverage of subsequent releases. EagerSeq still targets
Java 8; newer JDK operations can inspire implementations without importing
newer public types.

**Have** means directly supported; **compose** means existing operations suffice;
**candidate** means a direct addition may earn its place; **omit/defer** means
no addition is recommended now. Priorities concern additional work, not the
importance of an already well-supported group. They reflect design judgment,
not measured popularity. The comparison groups related operations rather than
cataloguing every overload.

## Priorities at a glance

| Priority | Group | Most useful decision |
|---|---|---|
| High | Numeric summaries | Add mapper averages and JDK summary-statistics terminals? |
| High | Counting | Add `count(predicate)` and `countBy(key)` as clear summary vocabulary? |
| Medium–high | Ordered selection | Add top/bottom k with bounded selection storage? |
| Medium | Positional editing | Design `updated` and `patch` together, including bounds rules. |
| Medium | Search | Consider predicate indexes, which preserve positions lost by filtering. |
| Medium | Projected sorting | Add `sortedBy` if it guarantees computing each key once. |
| Low–medium | Windows and splitting | Consider stepped windows or `span` for demonstrated use cases. |
| Low | Set algebra, factories, conversions, combinatorics | Coverage is broad; avoid filling every conceivable API cell. |

The first three groups offer useful additions without requiring new public
carrier types or execution protocols. The smaller candidates below should be
judged as families, not added simply because another library has a name.

Three tests help distinguish stronger proposals: does the method name a common
result more directly, preserve information awkward to recover through
composition, or enable a meaningfully better algorithm? Counting primarily
meets the first test, predicate indexes the second, and top-k the third.
Avoiding an eager intermediate alone is weaker evidence because `stream()`
already provides lazy composition. Conversely, composability is not sufficient
reason to reject a useful collection operation.

## 1. Counting and aggregation

**Have:** `size`, `count`, `isEmpty`, `frequency(value)`, `groupBy(key)`,
`groupBy(key, groupMapper)`, `partitionBy` with an optional completed-partition
mapper, and `toMap` with key/value mappers and an optional collision merger.

| Potential addition | Existing expression / precedent | Recommendation |
|---|---|---|
| `count(predicate)` | `stream().filter(p).count()`; Scala `count(p)` | **High candidate:** directly names a common summary. [Scala traversal][s-once] |
| `countBy(key)` | `toMap(key, e -> 1L, Long::sum)`; Guava multisets and Scala `groupMapReduce` | **High candidate:** concise, discoverable vocabulary, not new capability. [Multiset][g-multiset], [Scala grouping][s-iterable] |
| General grouped reduction | Already `toMap(k, v, merge)` | **Omit a synonym** for Scala `groupMapReduce`. [Scala grouping][s-iterable] |
| Downstream collector per group | `collect(groupingBy(k, downstream))` | **Medium candidate:** arbitrary per-key accumulation without building complete groups. [Collectors][jdk-collectors] |
| Map elements within groups | `groupBy(k, g -> g.map(v))`; Scala `groupMap` | **Lower priority:** a direct method could avoid retaining originals, but still stores mapped outputs. [Scala grouping][s-iterable] |
| Several summaries simultaneously | Custom `collect`, or JDK `teeing` where available | **Omit a dedicated family:** the collector entry point exists. [Collectors][jdk-collectors] |

`groupBy(key, Seq::size)` materialises groups; the `toMap` counting expression
already uses storage proportional to distinct keys. Thus `countBy` earns its
place through clarity, not a claim that efficient aggregation is missing.
Similarly, the current group-mapper overload consumes completed groups; it is
not a downstream collector.

Value frequency is already covered by `frequency`, following JDK and Guava
terminology. Do not add a competing `countOf` spelling. [Collections][jdk-collections],
[Iterables][g-iterables]

Ordinary grouping already corresponds to Guava `Multimaps.index` and JDK
`groupingBy`; Boolean partitioning corresponds to `partitioningBy`, with both
Boolean keys present. The interesting expansion is therefore how groups are
accumulated, not another grouping result structure or new partition spelling.
[Multimaps][g-multimaps], [Collectors][jdk-collectors]

## 2. Numeric summaries

**Have:** `sumOfInt/Long/Double`, `productOfInt/Long/Double`, and natural-order or
comparator-based `min`/`max`. General reduction and collection cover custom
accumulators.

| Potential addition | Existing expression / precedent | Recommendation |
|---|---|---|
| `averageOfInt/Long/Double` | `stream().mapToInt(f).average()` and corresponding long/double forms | **High candidate:** completes basic primitive summaries. [Primitive streams][jdk-intstream] |
| `summaryStatisticsOfInt/Long/Double` | `collect(summarizingInt(f))` and corresponding collectors | **High candidate:** return existing JDK statistics types; no new result class. [Collectors][jdk-collectors] |
| Variance, deviation, quantiles | Guava `Stats` and `Quantiles` | **Omit for now:** estimator and precision policies expand this into a statistics library. [Stats][g-stats], [Quantiles][g-quantiles] |

Prefer `OptionalDouble` for an empty average, matching primitive streams;
JDK averaging collectors instead return zero. Statistics objects have their
own empty-state conventions. Specify these choices explicitly. Current
`sumOfDouble` adds in encounter order, so using another accumulator may change
rounding; do not promise bit-identical results across all compositions.

## 3. Sorting, selection and sorted inputs

**Have:** `sorted`, `min`, `max` and `isSorted`, each with natural-order and
comparator forms. The nondecreasing-order check already covers Guava's
`Comparators.isInOrder`. [Comparators][g-comparators]

| Potential addition | Existing expression / precedent | Recommendation |
|---|---|---|
| Least/greatest k | `sorted(c).limit(k)`; Guava `least/greatest`, `leastOf/greatestOf` | **Medium–high:** a bounded selector avoids full sorting. [Comparators][g-comparators], [Ordering][g-ordering] |
| `sortedBy(key[, comparator])` | `sorted(Comparator.comparing(key))`; Scala `sortBy` | **Medium:** strongest case is evaluating each key once, with stable ties. [Scala sequences][s-seq] |
| `minBy` / `maxBy` | Comparator composition; Scala equivalents | **Low:** convenient naming, but extrema already take linear comparisons. [Scala traversal][s-once] |
| Strict order test | Adjacent comparisons; Guava `isInStrictOrder` | **Low:** useful if strict ordering is common. [Comparators][g-comparators] |
| Merge sorted inputs | Concatenate then sort; Guava `mergeSorted` | **Medium specialised candidate:** two-way merging can be linear with bounded lookahead, particularly on `SeqStream`. [Iterables][g-iterables] |
| Binary search / insertion point | `Collections.binarySearch(toList(), x, c)`; Scala `search` | **Lower priority:** chiefly for `Seq`; conversion and non-random-access views can undermine the benefit. [Collections][jdk-collections], [Scala sequences][s-seq] |

Top-k must specify tie handling: Guava allows arbitrary ties; matching stable
`sorted(c).limit(k)` is stronger. A heap can use O(k) selection storage and
O(n log k) selection time, plus sorting the result. This is an algorithmic
possibility, not an EagerSeq benchmark. Define output direction and invalid k.

Sortedness preconditions alone do not justify rejecting merge or binary search:
the comparison libraries already accept them. Demand, performance guarantees,
ties and stream ownership are the more useful design questions.

## 4. Search and sequence comparison

**Have:** `contains`, `containsAll`, Boolean match terminals, `get(index)`,
`getFirst/Last/Only`, `findFirst/Last/Only`, `toOptional`, `indexes`, equality
index searches, all four slice-search methods, `startsWith`, `endsWith`, and
`listEquals`/`setEquals`/`multisetEquals`.

| Potential addition | Existing expression / precedent | Recommendation |
|---|---|---|
| Predicate indexes | Guava predicate `indexOf`; Scala `indexWhere`/`lastIndexWhere` | **Medium:** filtering loses original positions. Candidate names: `index(p)`, `lastIndex(p)`, `indexes(p)`. [Iterables][g-iterables], [Scala sequences][s-seq] |
| Predicate element searches | `stream().filter(p).findFirst/Last/Only()`; Guava `find`/`tryFind` | **Low:** useful convenience, no missing algorithm. [Iterables][g-iterables] |
| Optional/default indexed access | Bounds handling; Guava defaulted `get`, Scala `lift` | **Low–medium:** `find(i)` needs an explicit null policy. [Iterables][g-iterables], [Scala immutable sequences][s-immutable] |
| Pairwise relation | Scala `corresponds` | **Low–medium:** possible matcher overload; require equal lengths. [Scala sequences][s-seq] |
| First differing position | JDK `Arrays.mismatch` | **Low–medium:** useful diagnostics/common-prefix primitive; no direct equivalent. [Arrays][jdk-arrays] |
| Lexicographic comparison | JDK `Arrays.compare`; Guava `lexicographical` | **Lower:** consider a comparator factory, not making every `Seq` naturally comparable. [Arrays][jdk-arrays], [Comparators][g-comparators] |
| Offset search / bounded size comparison | Slice and adjust indexes / bounded traversal; Scala APIs | **Low:** avoid multiplying search overloads; size comparison matters more for unknown-size streams. [Scala sequences][s-seq], [Scala grouping][s-iterable] |

Cardinality policies differ: `findOnly` is empty for zero or multiple elements;
`toOptional` throws for multiple; `getOnly` requires exactly one. Optional
searches cannot represent a selected null. Consequently, default-value access
is not always equivalent to `findFirst().orElse(default)`.

A mismatch operation should return the shorter length when one input is a
proper prefix, and `-1` for equality. Truncating `zip` alone cannot implement
that or equal-length correspondence. Existing predicate-index naming analysis
remains in `DIRECT_MATCHING.md`; these are candidates, not commitments.

## 5. Set and multiset operations

**Have:** `distinct`, `distinctBy`, `intersection`, `difference`, `union`, `sum`,
`symmetricDifference`, `containsMultiset`, `containsAll`, `disjoint`, and both
set/multiset equality tests. The core algebra is now complete.

For multiplicities a and b, intersection retains min(a,b), difference retains
max(0,a−b), union retains max(a,b), sum retains a+b, and symmetric difference
retains |a−b|. This follows Guava's multiset vocabulary; its set symmetric
difference is the related set precedent. [Multisets][g-multisets], [Sets][g-sets]

| Remaining question | Recommendation |
|---|---|
| Separate set-operation methods | **Omit:** distinct both operands before using multiset algebra. |
| Membership-based retain/remove | **Compose with filtering:** retaining every occurrence whose value belongs to a set differs from multiset intersection. |
| Projected/comparator-defined algebra | **Lower-priority family decision:** design equivalence, representative selection and order together. Mapping first loses original elements. |

For `[x,x,y]` against `[x,z]`, intersection gives `[x]`, while membership filtering
gives `[x,x]`. Scala's deprecated sequence `union` means concatenation, not
maximum multiplicity; its spelling is not a reason to disrupt the current
family. [Scala sequences][s-seq]

Order also needs precision: EagerSeq keeps the earliest matching occurrences
for intersection and the later survivors for difference. Guava's
`LinkedHashMultiset` preserves first-insertion order of distinct values, but
groups equal occurrences rather than retaining arbitrary sequence interleaving.
It is therefore wrong both to assume all Guava multisets lose order and to
treat their order as identical to EagerSeq's. [LinkedHashMultiset][g-linkedmultiset]

## 6. Transformations, pairing and windows

**Have:** `map`, `filter`, `flatMap`, `flatten`, `mapMulti`, `mapIndexed`, mapped
`zip`, length-based prefix/suffix operations, `takeWhile`, `dropWhile`, `slice`,
`windowFixed`, and `windowSliding`.

| Potential addition | Precedent / current alternative | Recommendation |
|---|---|---|
| Runtime type selection | Guava `filter(Class)`; filter then cast | **Low–medium:** an `ofType` method also narrows the result type. [Iterables][g-iterables] |
| Negated/null filtering, partial-function mapping | Negate a predicate, `Objects::nonNull`, filter/map or `mapMulti` | **Omit aliases/protocols:** existing primitives suffice. |
| Padded zip / pairwise side effects | Scala `zipAll`; Guava `forEachPair` | **Low:** useful specialised shapes, not grounds for a large pairing framework. [Scala grouping][s-iterable], [Streams][g-streams] |
| Unzip / transpose | Scala methods | **Defer:** tuple carriers and rectangularity rules need concrete demand. [Scala grouping][s-iterable] |
| Stepped windows | Scala `sliding(size, step)` | **Low–medium:** useful for overlapping/gapped batches; specify final partial windows. [Scala immutable sequences][s-immutable] |
| Split at index / predicate boundary | Scala `splitAt` / `span`; paired take/drop operations | **Low / low–medium:** `span` can avoid repeated predicate evaluation; both require two-result representation. [Scala immutable sequences][s-immutable] |
| Padded blocks / all prefixes and suffixes | Guava `paddedPartition`; Scala `inits`/`tails` | **Low or omit:** synthetic padding policies and potentially quadratic snapshot output. [Iterables][g-iterables], [Scala immutable sequences][s-immutable] |

`partitionBy` separates all matches/nonmatches; `span` separates at one boundary.
`windowSliding(2)` emits a singleton window for singleton input, so it is not
unconditionally a pair operation. These are JDK-style window semantics.
[Gatherers][jdk-gatherers]

Splitting a reusable `Seq` is straightforward; splitting a single-use stream
requires buffering or coordination. Primitive mapping and flat-mapping already
exist on `SeqStream`; adding lazy primitive results directly to `Seq` would
blur its eager model.

## 7. Copy-and-edit operations

**Have:** `reversed`, `rotated`, `shuffled(Random)`, concatenation through `sum`
and `concat`, and first/last removal through `skip(1)`/`skipLast(1)`.
JDK edits mutate; suitable EagerSeq counterparts would return snapshots.
[JDK List][jdk-list], [Collections][jdk-collections]

| Potential addition | Current alternative | Recommendation |
|---|---|---|
| `updated(index, value)` | `mapIndexed` | **Medium:** validated replacement; the simple mapping expression silently ignores invalid indexes. |
| `patch` | Slices plus concat | **Medium:** one primitive can cover insertion, deletion and interval replacement. |
| Append/prepend one element | Concatenate a singleton | **Medium convenience:** consider alongside editing, not as an alias explosion. |
| Swap / pad to size | Indexed mapping / append repeated padding | **Low:** assess within this family. |
| Replace all / fill | `map` / `repeat` | **Omit:** no missing transformation capability. |
| Default-RNG shuffle | Supply a `Random` | **Low-cost optional convenience**, not a missing algorithm. |

Scala's `updated`, `patch`, `appended` and `prepended` provide useful precedents.
The worthwhile gap is validated positional modification. Decide whether patch
bounds clamp like slicing or throw; avoid adding many isolated edit names.
[Scala immutable sequences][s-immutable]

## 8. Reduction and execution

**Have:** reduction with/without identity, heterogeneous accumulation, custom
and collector-based `collect`, `collectWhile`, `scan`, and side-effect methods.

The remaining candidates are mostly lower priority:

- Scala's seed-inclusive `scanLeft` and right folds/scans can be composed by
  prepending or reversing, with argument/result-order adjustments. EagerSeq's
  JDK-style `scan` excludes the seed. [Scala immutable sequences][s-immutable],
  [Gatherers][jdk-gatherers]
- **Defer general gatherers** and concurrent mapping. JDK `gather`, `fold` and
  `mapConcurrent` offer precedents, but execution protocols, scheduling and
  cancellation are substantial commitments. Modern runtimes can use the JDK
  bridge. [Gatherers][jdk-gatherers]
- `SeqStream` operations run sequentially; `parallel()` records mode for JDK
  and primitive bridges. True parallel evaluation is architectural work,
  not a missing convenience method.

Keep existing decisions in `DESIGN.md` and lifecycle details in
`STREAM_SEMANTICS.md`; neither needs reopening solely for API symmetry.

## 9. Factories and combinatorics

**Have:** `of`, `ofNullable`, copying/adaptation factories, explicit backing
views, builders, `toSeq` collector, integer/long `range`/`rangeClosed`, bounded
`repeat`/`generate`/`iterate`, and unbounded factories on `SeqStream`.

| Remaining candidate | Recommendation |
|---|---|
| Stepped/descending ranges | **Low–medium:** Scala `Range.by` precedent; bounded iteration already works. Specify direction, zero step and overflow. [Range][s-range] |
| Index-generated values | **Low:** `range(0,n).map(f)` covers Scala `tabulate`. [Factories][s-factories] |
| Whole-sequence cycling | **Low, stream-only:** Guava `cycle`; specify finite buffering, empty input and backing mutation. [Iterables][g-iterables] |
| State/output unfolding | **Defer:** Scala `unfold` needs a termination/state/output representation beyond simple recurrence. [Factories][s-factories] |

**Combinatorics already includes** mapped binary `product`, `power(k)`, full and
length-k permutations, all permutation lengths, length-k combinations and all
combination lengths. Multiple product axes are composable; a homogeneous
multi-axis factory is a low-priority convenience. [Guava Lists][g-lists]

Duplicate-aware permutation generation is a more substantive specialised gap:
Guava `orderedPermutations` avoids redundant outputs. EagerSeq enumerates
positions, so equal elements can produce equal results; appending `distinct`
can waste enormous work. Nevertheless, this ranks below everyday aggregation
and editing. Lazy output enumeration can still require finite buffered input.
[Collections2][g-collections2]

## 10. Conversions and boundaries

**Have:** arrays, unmodifiable lists/sets, map variants, optional conversion,
formatted string joining, lazy pipelines and JDK stream bridges. `toMap(key)`
already provides unique indexing; duplicate keys fail unless a merger is given.

The set conversion preserves encounter order. A factory-based collector can
choose another destination, but its mutability and null behaviour then follow
that collector. Similarly, a newer JDK default inherited by `SeqStream` at
runtime is not automatically an explicitly supported Java 8 EagerSeq method.
These are interoperability distinctions, not reasons to duplicate every
conversion or newer stream method on `Seq`.

Custom/sorted destinations use collectors. Null-skipping/substitution uses
filter/map before joining. Delimiter-only joining and writing to an
`Appendable` are low-priority conveniences, with Guava `Joiner` precedent.
[Collectors][jdk-collectors], [Joiner][g-joiner]

Preserve these boundaries:

- No mutators, synchronised/checked wrappers or new specialised storage types.
- No live-view duplicate of every transformation; explicit views and streams
  already express those choices. Keep snapshot reversal.
- No wholesale import of Guava maps, tables, range structures or multimap APIs.
- No comparator-building ecosystem on `Seq`; accept existing comparators.
- No Scala symbolic aliases, partial-function infrastructure or tuple-arity
  families merely for parity.

Composability does not imply identical contracts: collectors and other libraries
may differ in null handling, mutability, encounter order and duplicates. Those
differences matter more than matching names. These recommendations remain
provisional, with no implementation changes or performance measurements.

[jdk-collections]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Collections.html
[jdk-list]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html
[jdk-arrays]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Arrays.html
[jdk-intstream]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/IntStream.html
[jdk-collectors]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Collectors.html
[jdk-gatherers]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Gatherers.html
[g-iterables]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Iterables.html
[g-streams]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Streams.html
[g-lists]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Lists.html
[g-sets]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Sets.html
[g-multiset]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Multiset.html
[g-multisets]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Multisets.html
[g-comparators]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Comparators.html
[g-ordering]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Ordering.html
[g-collections2]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Collections2.html
[g-joiner]: https://guava.dev/releases/33.4.2-jre/api/docs/com/google/common/base/Joiner.html
[g-stats]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/math/Stats.html
[g-quantiles]: https://guava.dev/releases/33.4.2-jre/api/docs/com/google/common/math/Quantiles.html
[s-seq]: https://www.scala-lang.org/api/2.13.16/scala/collection/SeqOps.html
[s-iterable]: https://www.scala-lang.org/api/2.13.16/scala/collection/IterableOps.html
[s-once]: https://www.scala-lang.org/api/2.13.16/scala/collection/IterableOnceOps.html
[s-immutable]: https://www.scala-lang.org/api/2.13.16/scala/collection/immutable/Seq.html
[s-range]: https://www.scala-lang.org/api/2.13.16/scala/collection/immutable/Range.html
[s-factories]: https://docs.scala-lang.org/overviews/collections-2.13/creating-collections-from-scratch.html
[g-linkedmultiset]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/LinkedHashMultiset.html
[g-multimaps]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Multimaps.html
