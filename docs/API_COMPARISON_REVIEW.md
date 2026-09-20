# Collection operations review: JDK, Guava and Scala

Research date: 20 September 2026. Standalone decision aid, not a roadmap or a
replacement for the API contracts.

## Scope and how to read this report

The baseline is the **current working tree**, based on commit `0e50ed9`, including
uncommitted API additions. In particular, `frequency`, `disjoint`,
`symmetricDifference`, `isSorted`, `distinctBy`, numeric mapper terminals,
windows and `scan` are already present. Availability was checked against
[Seq.java](../src/main/java/io/github/eagerseq/Seq.java),
[SeqStream.java](../src/main/java/io/github/eagerseq/SeqStream.java) and their
shared implementations in [Sources.java](../src/main/java/io/github/eagerseq/Sources.java).
The earlier comparison was not used as an inventory of current methods.

External reference points are JDK 25, Guava 33.4.x (mostly 33.4.8-jre), and Scala
2.13.16's collection API. These are explicit documentation baselines, not a
claim to have surveyed every subsequent release. The library still targets
Java 8: a newer JDK operation can inspire a Java 8 implementation without making
its newer types available in public signatures. Scala receives less weight;
its tuples, partial functions, implicit orderings and collection hierarchy can
make an operation economical there but awkward in Java.

The unit of comparison is an **operation**, not each overload or spelling.
The tables cover the current sequence vocabulary and plausible additions from
the specified libraries, rather than every method on every utility class.
External links identify precedents; priorities and proposed API shapes are this
report's judgments, not measured usage statistics or accepted project decisions.

Status labels:

- **Have**: directly exposed by `Seq`, unless explicitly marked stream-only.
- **Compose**: expressible with the existing API; the question is whether a
  dedicated method earns its place through clarity, semantics or efficiency.
- **Gap**: no direct method and no comparably straightforward substitute.
- **Omit**: the operation exists elsewhere, but this review does not recommend
  exposing it directly. This does not mean it is impossible to implement.

Priority describes **additional work**, not the importance of an already
well-supported operation. A low-priority group can be central to the library
and simply have few worthwhile gaps left.

## Decisions worth considering first

| Group | Current position | Recommendation for additions |
|---|---|---|
| Numeric summaries | Sums, products, extrema and general collection exist | **High:** mapper averages and JDK summary-statistics terminals. Small, familiar family with existing result types. |
| Counting and keyed aggregation | Frequency, grouping, maps and merge-on-key exist | **High:** consider `count(predicate)` and `countBy(key)` together; avoid duplicating `toMap(k, v, merge)` as a new grouped-reduction framework. |
| Ordered selection | Sorting and sortedness checks exist | **Medium–high:** top/bottom k. Unlike `sorted().limit(k)`, an implementation can keep bounded selection state. |
| Positional editing | Reads and slices are extensive; copy edits are indirect | **Medium:** design `updated` and `patch` together. Consider append/prepend conveniences in the same review. |
| Searching and comparison | Equality searches are extensive | **Medium:** predicate indexes. **Lower:** mismatch, lexicographic comparison and custom pairwise matching as a coherent comparison family. |
| Sorting by projection | Comparator composition already works | **Medium:** `sortedBy` only if its contract adds useful key-caching semantics. |
| Set and multiset algebra | Core algebra is now complete | **Low:** no urgent additions. Projected equivalence is a separate, larger design decision. |
| Windows, splitting and pairing | Basic windows, zip and partition exist | **Low–medium:** stepped windows or `span` if use cases justify their contracts. |
| Factories, conversions and combinatorics | Broad coverage | **Low:** a few conveniences and specialised algorithms; avoid systematic expansion. |

A sensible first review could stop after the first three groups. They offer
useful additions without introducing a new result-type ecosystem, general
execution protocol or mutable collection model.

## 1. Counting, aggregation and numeric summaries

### Existing coverage and remaining candidates

| Operation | Current EagerSeq support | External precedent | Assessment |
|---|---|---|---|
| Total size | **Have:** `size()`, `count()`, `isEmpty()` | JDK collections/streams; Scala `size` | Complete; preserve `int` versus `long` distinction. |
| Frequency of one value | **Have:** `frequency(value)` returning `int` | JDK `Collections.frequency`; Guava `Iterables.frequency` | No gap; do not add a competing `countOf` spelling. [JDK collections][jdk-collections], [Guava iterables][g-iterables] |
| Predicate count | **Compose:** `stream().filter(p).count()` | Scala `count(p)` | **High candidate:** `count(p)` directly expresses a summary. [Scala traversal][s-once] |
| Histogram by key | **Compose:** `toMap(key, e -> 1L, Long::sum)` | Guava `Multiset` counts; Scala `groupMapReduce` | **High candidate:** `countBy(key)` is discoverable and concise, but does not unlock new capability. [Guava multiset][g-multiset], [Scala grouping][s-iterable] |
| Group original elements | **Have:** `groupBy(key)` | `Collectors.groupingBy`; Guava `Multimaps.index`; Scala `groupBy` | Complete for ordinary grouped sequences. [Collectors][jdk-collectors], [Multimaps][g-multimaps] |
| Transform each completed group | **Have:** `groupBy(key, groupMapper)` | Scala `groupBy` followed by group transformation | Existing overload builds groups; it is not a downstream accumulator. |
| Map each element within groups | **Compose:** `groupBy(k, g -> g.map(v))` | Scala `groupMap`; downstream JDK `mapping` | Lower priority. A direct method could avoid retaining originals, but the output still stores every mapped element. [Scala grouping][s-iterable], [Collectors][jdk-collectors] |
| Reduce mapped values per key | **Have:** `toMap(k, v, merge)` | Scala `groupMapReduce`; JDK `toMap` merger | Do not add a synonymous general method merely for parity. [Scala grouping][s-iterable], [Collectors][jdk-collectors] |
| Downstream collector per key | **Compose:** `collect(groupingBy(k, downstream))` | JDK `groupingBy` | **Medium candidate**, after the simpler summaries. Enables mutable per-key accumulators and finishing without whole groups. [Collectors][jdk-collectors] |
| Boolean partition | **Have:** `partitionBy(p)` and completed-partition mapper | JDK `partitioningBy`; Scala `partition` | Complete for ordinary partitions; both Boolean keys are present. [Collectors][jdk-collectors], [Scala grouping][s-iterable] |
| Primitive mapper sums/products | **Have:** `sumOfInt/Long/Double`, `productOfInt/Long/Double` | JDK primitive sums; Scala `sum`/`product` | No missing sum/product family. [Primitive streams][jdk-intstream], [Scala traversal][s-once] |
| Average of mapped numbers | **Compose:** `stream().mapToInt(f).average()`; analogous long/double paths | JDK primitive `average`; Guava `Stats.mean` | **High candidate:** `averageOfInt/Long/Double`. [Primitive streams][jdk-intstream], [Guava statistics][g-stats] |
| Count/sum/min/max/average together | **Compose:** `collect(summarizingInt(f))`, analogous long/double collectors | JDK summary-statistics collectors | **High candidate:** `summaryStatisticsOfInt/Long/Double`, returning JDK statistics types. No new carrier class required. [Collectors][jdk-collectors] |
| Multiple arbitrary summaries in one pass | **Have:** custom `collect`; newer JDK `teeing` collector can be passed directly when available | JDK `Collectors.teeing` | Omit a dedicated `Seq.teeing` family. A collector is already accepted. [Collectors][jdk-collectors] |
| Variance, standard deviation, quantiles | No direct method | Guava `Stats` and `Quantiles` | **Omit for now:** this is a statistics API, with estimator and precision choices, rather than completion of basic numeric terminals. [Guava statistics][g-stats], [Quantiles][g-quantiles] |

### Why this group deserves attention

The useful distinction is between **summary results** and **materialised groups**.
`groupBy(key, Seq::size)` builds all groups; `toMap(key, e -> 1L, Long::sum)`
already counts using state proportional to the number of keys. The case for
`countBy` is its vocabulary and discoverability, not a claim that EagerSeq
currently cannot aggregate efficiently. A downstream collector overload would
serve a different need: arbitrary accumulation state separate from the final
value.

Averages need an explicit empty-input policy. Prefer the primitive stream
convention of `OptionalDouble` over silently returning zero; JDK averaging
collectors instead return zero for empty input. Summary-statistics objects have
their own documented empty-state values. These alternatives should not be
accidentally mixed. [Primitive streams][jdk-intstream], [Collectors][jdk-collectors]

Numerical equivalence also needs care: current `sumOfDouble` adds in encounter
order; delegation to a JDK numeric accumulator can change rounding. Specify
new terminals on their own merits instead of promising bit-identical results
from every sum/average/statistics composition.

Suggested review boundary: start with the three primitive average and three
summary-statistics forms, plus predicate/keyed counting. Do not infer a need for
predicate variants of every numeric terminal.

## 2. Sorting, extrema and selection

| Operation | Current support | External precedent | Assessment |
|---|---|---|---|
| Natural/comparator sort | **Have:** `sorted()`, `sorted(c)` | JDK sort/streams; Guava `Ordering.sortedCopy` | Complete. [JDK collections][jdk-collections], [Guava ordering][g-ordering] |
| Natural/comparator extrema | **Have:** `min`, `max`, with or without comparator | JDK and Guava extrema | Complete; EagerSeq returns `Optional`. [JDK collections][jdk-collections], [Guava ordering][g-ordering] |
| Nondecreasing order test | **Have:** `isSorted`, with or without comparator | Guava `Comparators.isInOrder` | No gap. [Guava comparators][g-comparators] |
| Strict order test | **Compose:** adjacent-window predicate, taking care with short input | Guava `isInStrictOrder` | Low candidate; a direct short-circuiting check is reasonable if strict ordering matters in practice. [Guava comparators][g-comparators] |
| Sort by extracted key | **Compose:** `sorted(Comparator.comparing(key))` | Scala `sortBy`; Guava `Ordering.onResultOf` | Medium candidate if `sortedBy` computes each key once, preserves stable ties and offers a key comparator. [Scala sequences][s-seq], [Guava ordering][g-ordering] |
| Min/max by extracted key | **Compose:** `min/max(Comparator.comparing(key))` | Scala `minBy`/`maxBy` | Lower priority; naming convenience is real, but extrema already need only linear comparisons. [Scala traversal][s-once] |
| Least/greatest k elements | **Compose:** `sorted(c).limit(k)` or reversed comparator | Guava `Comparators.least/greatest`, `Ordering.leastOf/greatestOf` | **Medium–high candidate:** avoid full sorting; return results in the chosen order. [Guava comparators][g-comparators], [Guava ordering][g-ordering] |
| Merge already sorted inputs | **Gap:** concatenation then sorting does not exploit sortedness | Guava `Iterables.mergeSorted` | Medium specialised candidate, especially for `SeqStream`; two-way merge can be linear with bounded lookahead. [Guava iterables][g-iterables] |
| Search sorted input / insertion point | **Compose via JDK:** `Collections.binarySearch(toList(), x, c)` | JDK binary search; Scala `search` | Lower priority, principally a `Seq` operation. Conversion can erase the performance benefit. [JDK collections][jdk-collections], [Scala sequences][s-seq] |

Top-k needs a **tie contract** before naming or implementation. Guava permits
arbitrary ties; stable equivalence to `sorted(c).limit(k)` is a stronger and
potentially more useful promise. An ordinary heap-based design offers
O(n log k) selection and O(k) retained selection state, plus sorting the result;
this is a proposed algorithm, not a benchmark of EagerSeq. The eager receiver
still occupies its original storage. Define `k = 0`, negative k, k greater than
size, and whether greatest-k output is ascending or descending.

Sorted-input preconditions are not by themselves grounds to reject binary
search or merge: the JDK and Guava already expose such contracts. The more
substantial concerns here are demand, whether a particular `Seq` has efficient
random access, and keeping the performance promise meaningful for collection
views. `mergeSorted` should define stable ties and how it claims/closes stream
operands. Binary search has little justification as a generic streaming method.

## 3. Membership, searches and whole-sequence comparison

| Operation | Current support | External precedent / assessment |
|---|---|---|
| Membership and Boolean predicates | **Have:** `contains`, `containsAll`, `anyMatch`, `allMatch`, `noneMatch` | JDK collection/stream coverage. No new aliases needed. [JDK streams][jdk-stream] |
| Positional access | **Have:** `get(index)` | JDK `List.get`; Guava `Iterables.get`. [JDK lists][jdk-list], [Guava iterables][g-iterables] |
| First/last/single element | **Have:** `getFirst/Last/Only`, `findFirst/Last/Only`, `toOptional` | Broad coverage; see cardinality caveat below. |
| Optional/default positional access | **Compose:** bounds handling, or `stream().skip(i).findFirst()` for nonnegative i | Guava `get(iterable, i, default)`; Scala `lift`. **Low–medium candidate:** `find(i)` is useful but requires a null policy. [Guava iterables][g-iterables], [Scala sequences][s-immutable] |
| Find matching element | **Compose:** `stream().filter(p).findFirst/Last/Only()` | Guava `find`/`tryFind`; Scala `find`. **Low candidate:** concise direct overloads could help, but no missing algorithm. [Guava iterables][g-iterables], [Scala traversal][s-once] |
| Equality indexes | **Have:** `indexOf`, `lastIndexOf`, `indexesOf`; also `indexes()` | Broad coverage already. |
| Predicate indexes | **Gap** in direct vocabulary | Guava predicate `indexOf`; Scala `indexWhere`/`lastIndexWhere`. **Medium candidate:** filtering loses original positions. [Guava iterables][g-iterables], [Scala sequences][s-seq] |
| Search from an offset | **Compose:** slice/skip plus index adjustment | Scala index-search overloads. Low priority; avoid multiplying every search form. [Scala sequences][s-seq] |
| Contiguous slice search | **Have:** `indexOfSlice`, `lastIndexOfSlice`, `indexesOfSlice`, `containsSlice` | JDK `indexOfSubList`/`lastIndexOfSubList`; already broader. [JDK collections][jdk-collections] |
| Prefix/suffix tests | **Have:** `startsWith`, `endsWith` | Scala equivalents. [Scala sequences][s-seq] |
| Ordered, set, multiset equality | **Have:** `listEquals`, `setEquals`, `multisetEquals` | Clear separation of three questions; retain it. |
| Pairwise relation over equal-length inputs | No direct matcher overload | Scala `corresponds`. **Low–medium candidate:** `listEquals(that, matcher)` or another explicit relation name. [Scala sequences][s-seq] |
| First differing position | **Gap:** no `mismatch` | JDK `Arrays.mismatch` (Java 9). **Low–medium candidate:** useful for comparison diagnostics and common-prefix work. [JDK arrays][jdk-arrays] |
| Lexicographic comparison | No direct method; external comparator usable | JDK `Arrays.compare` (Java 9); Guava `Comparators.lexicographical` | Lower priority than mismatch. Consider a comparator factory rather than making `Seq` naturally `Comparable`. [JDK arrays][jdk-arrays], [Guava comparators][g-comparators] |
| Size comparison without full counting | **Compose:** bounded traversal | Scala `sizeCompare`/`sizeIs` | Low priority on eager materialised values; more meaningful for unknown-size streams. [Scala collections][s-iterable] |

`findOnly()` returns empty for both zero and multiple elements. `toOptional()`
returns empty only for zero and throws for multiple elements. `getOnly()` throws
unless there is exactly one element. These are separate cardinality policies,
not interchangeable aliases. Present null elements cannot be represented by
`Optional`; the current optional-returning searches throw when selecting null.
A Guava default-value overload is therefore not exactly reproduced by
`findFirst().orElse(defaultValue)` on every EagerSeq input.

The smallest plausible search extension is predicate indexes, using the
existing proposal names `index(p)`, `lastIndex(p)`, `indexes(p)` rather than
creating ambiguous `indexOf(Object)`/`indexOf(Predicate)` overloads. That is a
candidate, not a commitment; existing naming analysis lives in
[DIRECT_MATCHING.md](DIRECT_MATCHING.md).

If adding mismatch, adopt a clear length rule: equal inputs return `-1`; when
one is a proper prefix, return the shorter length. A zipped equality check
alone is insufficient for either mismatch or pairwise correspondence because
`zip` truncates at the shorter input. [JDK arrays][jdk-arrays]

## 4. Set and multiset algebra

| Operation | Current support | External precedent / assessment |
|---|---|---|
| Remove duplicate values / keys | **Have:** `distinct`, `distinctBy` | JDK `distinct`; Scala `distinctBy`. Complete for hashable equality keys. [JDK streams][jdk-stream], [Scala sequences][s-seq] |
| Intersection | **Have:** `intersection` | Guava `Multisets.intersection`: minimum multiplicity. [Guava multiset algebra][g-multisets] |
| Difference | **Have:** `difference` | Guava `Multisets.difference`: subtract counts, floor at zero. [Guava multiset algebra][g-multisets] |
| Union | **Have:** `union` | Guava `Multisets.union`: maximum multiplicity. [Guava multiset algebra][g-multisets] |
| Add multiplicities / concatenate | **Have:** instance `sum`, static `concat` | Guava `Multisets.sum`; Scala concatenation. No missing append-all capability. [Guava multiset algebra][g-multisets] |
| Symmetric difference | **Have:** `symmetricDifference` | Guava `Sets.symmetricDifference` is the set precedent; EagerSeq uses absolute count difference. [Guava sets][g-sets] |
| Containment ignoring / respecting counts | **Have:** `containsAll` / `containsMultiset` | Guava set containment / `containsOccurrences`. [Guava multiset algebra][g-multisets] |
| No overlap | **Have:** `disjoint` | JDK `Collections.disjoint`. No gap. [JDK collections][jdk-collections] |
| True set results | **Compose:** distinct both operands before multiset algebra | Guava `Sets` operations | Omit separate duplicate set-operation names. [Guava sets][g-sets] |
| Retain/remove every occurrence whose value belongs to another collection | **Compose:** `filter(membership::contains)` or negation, preferably with a set | JDK `retainAll`/`removeAll` have membership semantics | This is **not** multiset intersection/difference. Usually leave as filtering; do not copy mutation names. [JDK collections API][jdk-collection] |
| Projected or comparator-defined algebra | No direct `intersectionBy`, `differenceBy`, etc. | Guava sorted sets provide comparison-based membership | Lower-priority **family decision**: identity keys, representatives and order all need a coherent contract. [Guava sets][g-sets] |

For `a = [x, x, y]` and `b = [x, z]`, the current multiset operations produce:

| Expression | Result |
|---|---|
| `a.intersection(b)` | `[x]` |
| `a.difference(b)` | `[x, y]` |
| `a.union(b)` | `[x, x, y, z]` |
| `a.sum(b)` | `[x, x, y, x, z]` |
| `a.symmetricDifference(b)` | `[x, y, z]` |
| `a.filter(b.toSet()::contains)` | `[x, x]` |

Scala's deprecated sequence `union` means concatenation, not maximum
multiplicity. Its name is not evidence against EagerSeq's internally consistent
Guava-style multiset family. [Scala sequences][s-seq]

This group now has few obvious holes. Do not let its conceptual symmetry push
it ahead of summaries and editing. If projected algebra is revisited, use the
existing [equality and ordering analysis](EQUALITY_AND_ORDERING.md), rather than
adding isolated overloads here. Guava's `LinkedHashMultiset` preserves the
first-insertion order of distinct elements, although it groups equal occurrences
rather than preserving an arbitrary sequence's interleaving. Guava multiset use
does not require choosing `HashMultiset`. [LinkedHashMultiset][g-linkedmultiset]

## 5. Transforming, filtering and combining elements

| Operation | Current support | External precedent / assessment |
|---|---|---|
| Map/filter/flat-map/flatten | **Have:** `map`, `filter`, `flatMap`, static `flatten` | JDK streams and Scala core operations. Complete. [JDK streams][jdk-stream], [Scala traversal][s-once] |
| Emit zero or more outputs without creating an intermediate iterable | **Have:** `mapMulti` | JDK `Stream.mapMulti` (Java 16). No gap. [JDK streams][jdk-stream] |
| Transform with index | **Have:** `mapIndexed` | Guava `Streams.mapWithIndex`. EagerSeq uses integer indexes; Guava uses long indexes. [Guava streams][g-streams] |
| Negated filter / remove nulls | **Compose:** `filter(p.negate())`, `filter(Objects::nonNull)` | Scala `filterNot`; Guava predicate filtering | Omit aliases absent demonstrated demand. [Scala traversal][s-once] |
| Select values of a runtime type | **Compose:** `filter(type::isInstance).map(type::cast)` | Guava `Iterables.filter(Class)` | **Low–medium candidate:** `ofType(Class<R>)` could combine selection with static type narrowing. [Guava iterables][g-iterables] |
| Partial-function mapping | **Compose:** filter/map or `mapMulti` | Scala `collect`, `collectFirst` | Omit the Scala protocol; `collect` already means JDK accumulation here. [Scala traversal][s-once] |
| Primitive mapping / flat mapping | **Stream-only:** `mapToInt/Long/Double`, `flatMapToInt/Long/Double` | JDK primitive streams | Keep the bridge. Direct `Seq` methods returning lazy primitive streams would blur eager expectations. [JDK streams][jdk-stream] |
| Primitive multi-output mapping | No EagerSeq-specific primitive family | JDK `mapMultiToInt/Long/Double` | Low priority; use a JDK stream bridge on supported runtimes. Do not count inherited newer defaults as Java 8 EagerSeq API. [JDK streams][jdk-stream] |
| Zip two inputs with a function | **Have:** `zip(that, mapper)` | Guava `Streams.zip` | Complete for truncation at the shorter input. [Guava streams][g-streams] |
| Zip with padding / equal-length requirement | No direct method | Scala `zipAll` supplies padding | Low candidate; name and absence/padding semantics must be explicit. Strict zip is a related design option, not Scala `zipAll`'s contract. [Scala collections][s-iterable] |
| For-each corresponding pair | No direct void terminal | Guava `Streams.forEachPair` | Low candidate if pairwise side effects are common; returning dummy mapped values is awkward but does not justify a whole pairing framework. [Guava streams][g-streams] |
| Unzip / transpose nested sequences | No direct methods | Scala `unzip`, `unzip3`, `transpose` | Defer: tuple carriers for unzip and rectangularity rules for transpose deserve actual use cases. [Scala collections][s-iterable] |

The lack of built-in tuples is a cost, not a proof that these methods are
impossible in Java. A pair class or mapper-based API is possible. The question
is whether those extra public concepts are justified; existing `zip` and
`product` successfully avoid requiring them.

## 6. Slicing, windows and splitting

| Operation | Current support | External precedent / assessment |
|---|---|---|
| Prefix/suffix by length | **Have:** `limit`, `skip`, `limitLast`, `skipLast` | JDK prefix operations; Scala `take`/`drop` and right variants | Complete. [JDK streams][jdk-stream], [Scala collections][s-immutable] |
| Prefix by condition | **Have:** `takeWhile`, `dropWhile` | JDK since Java 9; Scala equivalents | Complete. [JDK streams][jdk-stream] |
| Index interval | **Have:** `slice(from, to)` | JDK `List.subList`; Scala `slice` | Capability exists, with snapshot and clamped-upper-bound semantics, not JDK view/strict-bounds semantics. [JDK lists][jdk-list] |
| Fixed blocks | **Have:** `windowFixed(size)` | JDK `Gatherers.windowFixed`; Guava `Lists.partition`; Scala `grouped` | Complete, including a final partial block. [Gatherers][jdk-gatherers], [Guava lists][g-lists] |
| Sliding windows | **Have:** `windowSliding(size)` | JDK `Gatherers.windowSliding`; Scala `sliding` | Complete for unit step and the current JDK-style short-input rule. [Gatherers][jdk-gatherers] |
| Stepped windows | No public step argument | Scala `sliding(size, step)` | **Low–medium candidate:** useful for overlapping or gapped batches; decide final partial-window rules. [Scala collections][s-immutable] |
| Padded fixed blocks | **Compose:** map/pad resulting blocks | Guava `Iterables.paddedPartition` | Omit a dedicated null-padding method; padding introduces synthetic values and an extra policy. [Guava iterables][g-iterables] |
| Split into prefix/suffix at an index | **Compose:** `limit(n)` plus `skip(n)` | Scala `splitAt` | Low candidate on reusable `Seq`; harder on a single-use stream because both results need coordination. [Scala collections][s-immutable] |
| Split at first predicate failure | **Compose:** `takeWhile` plus `dropWhile` on reusable input | Scala `span` | **Low–medium candidate:** one boundary search and a single evaluation of predicates can be worthwhile. Needs a two-result representation. [Scala collections][s-immutable] |
| All prefixes / all suffixes | **Compose:** indexes and slices | Scala `inits`, `tails` | Low priority; eager snapshots can require quadratic output storage. [Scala collections][s-immutable] |
| Prefix/segment length | **Compose:** `stream().skip(from).takeWhile(p).count()` | Scala `segmentLength` | Omit a separate name unless positional analysis becomes a major use case. [Scala sequences][s-seq] |

Two easy mistakes: `partitionBy` splits *all* matching/nonmatching elements,
whereas `span` splits at one boundary; and `windowSliding(2)` produces a
one-element window for singleton input, so it is not automatically a sequence
of pairs. Empty input produces no windows. Existing `windowSliding` should not
change its behaviour just to imitate another library's overload family.

## 7. Copy-and-edit operations and reordering

Sources for this family are [JDK List][jdk-list], [Collections][jdk-collections]
and [Scala immutable sequences][s-immutable]. JDK edits mutate; proposed EagerSeq
forms below would return new snapshots.

| Operation | Current support | Assessment |
|---|---|---|
| Reverse / rotate / shuffle | **Have:** `reversed`, `rotated(distance)`, `shuffled(Random)` | Complete core algorithms. Only the caller-supplied-RNG shuffle exists. |
| Default-RNG shuffle | **Compose:** supply a `Random` | Low-cost optional convenience matching JDK no-argument shuffle; low priority. Newer `RandomGenerator` signatures require a baseline decision. |
| Append/prepend one element | **Compose:** `sum(Seq.of(x))`, `Seq.concat(Seq.of(x), seq)` | Medium convenience candidates: frequent sequence edits, visible in Scala `appended`/`prepended`. |
| Append/prepend a sequence | **Have/compose:** `sum(that)`, `Seq.concat(that, seq)` | No algorithmic gap; avoid duplicating many aliases. |
| Replace at an index | **Compose:** `mapIndexed((i, e) -> i == index ? replacement : e)` | **Medium candidate:** `updated(index, value)` can offer strict invalid-index handling; the composition silently does nothing out of range. Scala precedent; JDK analogue is `List.set`. |
| Replace an interval / insert / remove positions | **Compose:** slices and concat | **Medium candidate:** one `patch` primitive can cover replacement, insertion and deletion without separate names for every case. Scala `patch`; JDK indexed edits are mutable. |
| Swap positions | **Compose:** indexed mapping | Low candidate from `Collections.swap`; assess as part of editing, not in isolation. |
| Replace all equal values | **Compose:** `map(e -> Objects.equals(e, oldValue) ? newValue : e)` | Usually omit; JDK `Collections.replaceAll` is not a missing mapping capability. |
| Transform all values | **Have:** `map` | Copy analogue of JDK `List.replaceAll`. No second name. |
| Fill / overwrite existing destination | **Compose:** `repeat` for a new constant sequence; map/patch for edits | Omit mutable `Collections.fill/copy` analogues. Destination mutation belongs to the destination. |
| Pad to a minimum size | **Compose:** append `repeat` of the positive size shortfall | Low candidate from Scala `padTo`, possibly useful alongside zip/window padding. |
| Remove first/last | **Have:** `skip(1)`, `skipLast(1)` | Copy equivalents already exist; do not add mutable deque operations. |

The genuine gap is **validated positional modification**, not mutation in
general. A small editing family is easier to justify than miscellaneous
`inserted`, `removed`, `replaced`, `swapped` and `filled` methods added separately.
Before adoption, choose whether `patch` clamps like slicing or throws like
`updated`; Scala and the JDK do not provide one universally applicable rule.

## 8. Reduction, scanning and general stateful processing

| Operation | Current support | External precedent / assessment |
|---|---|---|
| Left fold / reduce | **Have:** identity, optional-result and heterogeneous-accumulator `reduce` forms | JDK reduction; Scala `foldLeft`/`reduceLeft`. No `foldLeft` alias needed. [JDK streams][jdk-stream], [Scala traversal][s-once] |
| Mutable accumulation | **Have:** `collect(supplier, accumulator)` and `collect(Collector)` | JDK collectors | Complete general escape hatch. |
| Stop accumulation early | **Have:** `collectWhile` | Already beyond ordinary JDK collection terminals | No need to restore rejected `reduceWhile` solely for symmetry. |
| Running left accumulation | **Have:** `scan(initialSupplier, scanner)` | JDK `Gatherers.scan` | Complete under the JDK-style contract: output excludes the seed. [Gatherers][jdk-gatherers] |
| Seed-inclusive scan | **Compose:** prepend seed when appropriate | Scala `scanLeft` includes the seed | Omit a duplicate until demanded; mutable seeds also need care with aliasing. [Scala collections][s-immutable] |
| Right fold / right scan | **Compose:** reverse then reduce/scan, adapting argument and result order | Scala `foldRight`, `scanRight` | Low priority on finite input; not generally productive for unbounded streams. [Scala traversal][s-once], [Scala collections][s-immutable] |
| General custom gatherer | No Java-8-native EagerSeq protocol | JDK `Stream.gather` (Java 24) | Defer, consistently with the existing design decision. Modern JDK callers can use `toStream()` for the JDK API. [JDK streams][jdk-stream] |
| Fold as a one-element stream stage | **Compose:** reduction plus wrapping, where materialisation is acceptable | JDK `Gatherers.fold` | Low priority; distinct stage semantics matter more on `SeqStream` than `Seq`. [Gatherers][jdk-gatherers] |
| Concurrent mapping | No concurrent EagerSeq evaluation | JDK `Gatherers.mapConcurrent` | Omit for now: scheduling, cancellation and resource policy are a much larger commitment than collection vocabulary. [Gatherers][jdk-gatherers] |
| Side effects and observation | **Have:** `forEach`, `forEachOrdered`, `peek` | JDK stream operations | Complete; eager `peek` executes immediately and returns the receiver. |

The existing rationale for deferred/rejected protocol additions belongs in
[DESIGN.md](DESIGN.md#deliberate-api-limits). This report does not reopen it merely
because the JDK now provides gatherers.

## 9. Factories, ranges and repetition

| Operation | Current support | External precedent / assessment |
|---|---|---|
| Empty, singleton, supplied values | **Have:** `of` overloads | JDK/Guava collection factories | Complete. |
| Nullable singleton / optional contents | **Have:** `ofNullable`, `copyOf(Optional)` | JDK `Stream.ofNullable`, `Optional.stream`; Guava `Streams.stream(Optional)` | No gap; factory naming need not match exactly. [JDK streams][jdk-stream], [Guava streams][g-streams] |
| Copy arrays, iterables, iterators, spliterators, streams | **Have:** `copyOf` overloads | JDK and Guava adapters | Broad coverage. |
| Explicit live backing views | **Have:** `viewOf(array/Collection)` | JDK/Guava views | Do not make ordinary transformations live to chase parity. |
| Integer/long half-open and closed ranges | **Have:** `range`, `rangeClosed` | JDK `IntStream`/`LongStream` | Complete. [Primitive streams][jdk-intstream] |
| Stepped/descending ranges | **Compose:** bounded `iterate`, with overflow care | Scala `Range.by` | **Low–medium candidate:** `range(from, to, step)` is understandable; validate zero step, direction and overflow explicitly. [Scala ranges][s-range] |
| Repeat one value n times | **Have:** `repeat(value, n)` | JDK `Collections.nCopies` | Complete. [JDK collections][jdk-collections] |
| Evaluate supplier n times | **Have:** `generate(supplier, n)` | Scala `fill` evaluates an expression repeatedly | Complete; distinct from repeating one previously computed value. [Scala factories][s-factories] |
| Generate from index | **Compose:** `range(0, n).map(f)` | Scala `tabulate`; JDK `Arrays.setAll` mutates an existing array | Low convenience candidate, not a missing expressive operation. [Scala factories][s-factories], [JDK arrays][jdk-arrays] |
| Bounded recurrence | **Have:** `iterate(seed, hasNext, next)` | JDK three-argument `iterate` | Complete. [JDK streams][jdk-stream] |
| Unbounded recurrence, supplier, constant | **Stream-only:** `iterate`, `generate`, `repeat` | JDK unbounded factories | Correct placement; eager results cannot be infinite. [JDK streams][jdk-stream] |
| Repeat a whole sequence indefinitely | No direct cycle method | Guava `Iterables.cycle` | Low candidate for `SeqStream`, preferably from a finite reusable snapshot. Empty input, buffering and backing mutation need explicit semantics. [Guava iterables][g-iterables] |
| General state-to-output unfolding | No dedicated factory | Scala `unfold` | Defer: needs a state/output/termination representation; bounded recurrence already covers simpler cases. [Scala factories][s-factories] |
| Builders and stream collector | **Have:** `builder`, static `toSeq()` | JDK stream builder and Guava builders/collectors | Complete basic construction vocabulary. |

## 10. Combinatorial operations

| Operation | Current support | External precedent / assessment |
|---|---|---|
| Two-input Cartesian product | **Have:** `product(that, mapper)` | Guava `Lists.cartesianProduct` supports multiple axes | Current binary form supports heterogeneous types through a mapper. [Guava lists][g-lists] |
| Arbitrary number of product axes | **Compose:** repeated product/flat-map with an accumulator | Guava list/set Cartesian products | Low candidate for homogeneous axes returning `Seq<Seq<E>>`; no need for fixed arities three, four, etc. [Guava lists][g-lists], [Guava sets][g-sets] |
| Cartesian power | **Have:** `power(k)` | Related to repeated product axes | Complete for repeated selection from one sequence. |
| Full and length-k permutations | **Have:** `permutations()`, `permutations(k)` | Guava `Collections2.permutations`; Scala permutations | Broad positional coverage. [Guava combinatorics][g-collections2] |
| All permutation lengths | **Have:** `allPermutations()` | Broader than the basic comparison APIs | No expansion needed. |
| Length-k / all-length combinations | **Have:** `combinations(k)`, `allCombinations()` | Guava `Sets.combinations`/`powerSet`; Scala combinations | Similar capabilities, different duplicate semantics. [Guava sets][g-sets], [Scala sequences][s-seq] |
| Unique value permutations with duplicates | **Compose:** permutations then distinct, potentially very wasteful | Guava `orderedPermutations`; Scala permutations | Low specialised candidate with a real algorithmic distinction. [Guava combinatorics][g-collections2], [Scala sequences][s-seq] |

EagerSeq enumerates original **positions**. Equal elements can therefore produce
repeated equal outputs. Guava set combinations first have set semantics, while
Scala combination/permutation enumeration treats duplicates differently.
Calling these exact equivalents would be misleading. A duplicate-aware
algorithm can avoid generating enormous numbers of redundant results, but this
is still a specialised priority below everyday aggregation and editing.

Also distinguish lazy enumeration of results from streaming input: several
`SeqStream` combinatorial methods still buffer their finite input. Eager
combinatorial output can grow exponentially or factorially; adding more names
does not solve that inherent cost.

## 11. Conversion, formatting and interoperability

| Operation | Current support | External precedent / assessment |
|---|---|---|
| Arrays, lists, sets, maps | **Have:** `toArray` overloads, `toList`, `toSet`, `toMap` overloads | JDK collectors; Guava conversion helpers | Complete ordinary conversions. |
| Unique index by key | **Have:** `toMap(key)` | Guava `Maps.uniqueIndex` | Duplicate keys fail; not a last-value-wins dictionary operation. [Guava maps][g-maps] |
| Keys to computed values | **Have:** `toMap(k, v)` with duplicate rejection; merger overload available | Guava `Maps.toMap` collapses repeated equal input keys | Capability exists, but duplicate and null policies differ. [Guava maps][g-maps] |
| Custom mutable destination collection | **Compose:** `collect(Collectors.toCollection(factory))` | JDK `toCollection`; Guava `FluentIterable.copyInto` | Omit a family of named concrete collection conversions. Existing destination can receive `addAll(seq)`. [Collectors][jdk-collectors], [Guava fluent iterable][g-fluent] |
| Sorted collection / custom map implementation | **Compose:** collectors with factories | Guava fluent sorted conversions; JDK collectors | Low convenience priority; comparator and duplicate policies belong to the target. [Guava fluent iterable][g-fluent], [Collectors][jdk-collectors] |
| String join with delimiter and wrappers | **Have:** `toString(delimiter, prefix, suffix)` | JDK joining; Guava `Joiner`; Scala `mkString` | Complete core formatting. [Guava joiner][g-joiner], [Scala traversal][s-once] |
| Simple delimiter-only join | **Compose:** pass empty wrappers | Guava/Scala join overloads | Low-cost convenience candidate; decide naming independently of richer formatting. |
| Skip/substitute nulls while joining | **Compose:** filter/map then join | Guava `Joiner.skipNulls/useForNull` | No need for a separate configurable joiner object. Policies are not identical by default. [Guava joiner][g-joiner] |
| Write joined output to an `Appendable` | No direct formatted-output terminal | Guava `Joiner.appendTo` | Low specialised candidate if avoiding a whole result string matters. [Guava joiner][g-joiner] |
| Legacy enumeration input/output | **Compose:** JDK adapters plus existing factories | `Collections.list/enumeration` | Omit dedicated legacy overloads absent use cases. [JDK collections][jdk-collections] |
| Lazy operation pipeline / JDK stream bridge | **Have:** `stream`, `parallelStream`; `SeqStream.toSeq/toStream` | JDK stream interoperability | Complete escape hatch; see execution caveat below. |

`toList` and `toSet` are unmodifiable outputs, and `toSet` retains encounter
order. Generic JDK collectors can have different mutability, order and null
behaviour. “Available through collect” means the computation is possible, not
that every collector already reproduces EagerSeq's result contracts.

`SeqStream` implements `Stream`, but its own algorithms run sequentially.
`parallel()` records mode; the JDK stream and primitive bridges can perform
parallel evaluation. Newer default methods inherited at runtime are not the
same thing as explicitly supported Java 8 EagerSeq operations. A proposal for
true parallel evaluation is architectural work, not a missing collection method.
See [stream semantics](STREAM_SEMANTICS.md).

## 12. Boundaries worth preserving

These are omissions with a coherent rationale, rather than overlooked gaps:

- **Mutators, synchronised/checked wrappers and specialised storage types.**
  JDK utility methods cover these, but `Seq` rejects collection mutation.
  Copy edits are the appropriate area to expand.
- **A second immutable/live/lazy variant of every transformation.** Explicit
  views and `SeqStream` already establish those choices. JDK
  `SequencedCollection.reversed()` is a view; EagerSeq deliberately keeps
  snapshot reversal. [JDK sequenced collections][jdk-sequenced]
- **Guava's broader data-structure ecosystem.** `Multimap`, `BiMap`, `Table`,
  range structures and specialised maps solve different problems from adding
  operations to a sequence. Using `Map<K, Seq<E>>` is sufficient for ordinary
  grouping; it does not replace every multimap feature.
- **Comparator-building methods on Seq.** JDK `Comparator` and Guava
  `Ordering` are usable as arguments. Projected sorting can earn a place through
  key caching, but comparator composition itself need not move onto `Seq`.
- **Consuming/peeking iterator utilities and iterator mutation.** Guava's
  iterator adapters serve cursor management. `SeqStream` and `Source` already
  provide the traversal boundary; they are not evidence for dozens of new eager
  sequence methods. [Guava iterables][g-iterables]
- **Scala syntax infrastructure and result-type machinery.** Symbolic aliases,
  partial-function protocols, `withFilter`, implicit conversions and tuple
  arity families are not an appropriate parity target for Java.

This review's recommendations remain provisional. If adopted, update the
existing authoritative design references and `TODO.md`; the tables here should
not silently become a second source of settled API decisions.

## Source references

Links in the tables refer to the official documentation below. Guava links use
33.4.8 where retrievable, with explicitly versioned 33.4.2/33.4.7 pages for a few
classes. Scala sources are the standard collection API, not third-party Scala
libraries. No implementation changes or performance measurements were made for
this report.

[jdk-collections]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Collections.html
[jdk-collection]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Collection.html
[jdk-list]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html
[jdk-arrays]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Arrays.html
[jdk-sequenced]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/SequencedCollection.html
[jdk-stream]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Stream.html
[jdk-intstream]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/IntStream.html
[jdk-collectors]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Collectors.html
[jdk-gatherers]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Gatherers.html
[g-iterables]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Iterables.html
[g-streams]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Streams.html
[g-lists]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Lists.html
[g-sets]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Sets.html
[g-multiset]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Multiset.html
[g-multisets]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Multisets.html
[g-multimaps]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Multimaps.html
[g-maps]: https://guava.dev/releases/33.4.7-jre/api/docs/com/google/common/collect/Maps.html
[g-comparators]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Comparators.html
[g-ordering]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Ordering.html
[g-collections2]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/Collections2.html
[g-fluent]: https://guava.dev/releases/33.4.2-jre/api/docs/com/google/common/collect/FluentIterable.html
[g-joiner]: https://guava.dev/releases/33.4.2-jre/api/docs/com/google/common/base/Joiner.html
[g-stats]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/math/Stats.html
[g-quantiles]: https://guava.dev/releases/33.4.2-jre/api/docs/com/google/common/math/Quantiles.html
[g-linkedmultiset]: https://guava.dev/releases/33.4.8-jre/api/docs/com/google/common/collect/LinkedHashMultiset.html
[s-seq]: https://www.scala-lang.org/api/2.13.16/scala/collection/SeqOps.html
[s-iterable]: https://www.scala-lang.org/api/2.13.16/scala/collection/IterableOps.html
[s-once]: https://www.scala-lang.org/api/2.13.16/scala/collection/IterableOnceOps.html
[s-immutable]: https://www.scala-lang.org/api/2.13.16/scala/collection/immutable/Seq.html
[s-range]: https://www.scala-lang.org/api/2.13.16/scala/collection/immutable/Range.html
[s-factories]: https://docs.scala-lang.org/overviews/collections-2.13/creating-collections-from-scratch.html
