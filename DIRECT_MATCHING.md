# Direct matching families

Companion to `EQUALITY_AND_ORDERING.md`, covering only direct matching: one
input searched against a query. Not equivalence classes (`distinct`, set
operations) or ordering (`sorted`, `min`).

This is conditional naming guidance, not an implementation plan. A coherent
family does not establish demand for every member, and an empty cell is not
necessarily an API gap. No predicate-search expansion is currently planned;
counting is considered independently.

## The naming axis

```
foo()          ==  foo(alwaysTrue())     // identity query, overloads the base name
fooOf(object)  ==  foo(e -> Objects.equals(object, e)) // equality query
```

The codebase already instantiates this: `indexOf`/`lastIndexOf`/`indexesOf`
are the `Of` column, and `indexes()` is the identity form. Only the predicate
column is missing. Because the predicate form takes the *base* name, there is
no `indexOf(Object)`/`indexOf(Predicate)` overload hazard, so no `Where` or
`Match` suffix is needed. (`While` is unaffected: it marks prefix semantics,
not the argument kind.)

`Of` also appears in the factories with a different sense. Read it here as
"the elements are supplied by the next argument".

## The other axes

- **Occurrence**: first / last / all / single.
- **Absence**: throws (`get*`), `Optional` (`find*`), `-1` (`index*`).
- **Result**: boolean, count, index, element, sequence.

`get*`/`find*` name `First` explicitly because `SequencedCollection` forces it;
`index`/`lastIndex` leave first implicit because `List.indexOf` does. Those are
the only two classes, so nothing else has to be made consistent.

## Tables

Status: **have**, **candidate** (possible shape, not a planned addition),
*reject* (fits the pattern, not worth a name),
n/a (degenerate or ill-formed).

### Boolean — the JDK-owned irregular block

| identity | predicate | `Of` |
|---|---|---|
| `!isEmpty()` have | `anyMatch(p)` have | `contains(o)` have |
| `isEmpty()` have | `noneMatch(p)` have | *`noneMatchOf(o)`* = `!contains` |
| n/a | `allMatch(p)` have | *`allMatchOf(o)`* real op, no demand |

Names are fixed by `Collection` and `Stream`; the law cannot reach this block.
It is a group, but an unreachable one. Note `none` negates the *result*, so
`contains` already covers it, while `all` is a *different predicate* and is not
expressible from `contains` at all.

### Count

| identity | predicate | `Of` |
|---|---|---|
| `count()` have (`size()` returns an `int`) | `count(p)` **candidate** | `countOf(o)` **candidate** |

### Index (`int`, `-1`)

| identity | predicate | `Of` |
|---|---|---|
| `index()` n/a — `isEmpty() ? -1 : 0`, a boolean spelled wrong | `index(p)` **candidate** | `indexOf(o)` have |
| `lastIndex()` **candidate** — `size()-1`, lands on `-1` when empty | `lastIndex(p)` **candidate** | `lastIndexOf(o)` have |
| `indexes()` have | `indexes(p)` **candidate** | `indexesOf(o)` have |

`Optional` variants of the whole index family (`findIndex` and friends) are
rejected: `Optional<Integer>` boxes, `OptionalInt` will not chain, `-1` is
`List` parity, and it would double the largest family for one idiom.

### Element

| identity | predicate | `Of` |
|---|---|---|
| `getFirst()` have | `getFirst(p)` **candidate** | *degenerate* |
| `getLast()` have | `getLast(p)` **candidate** | *degenerate* |
| `getOnly()` have | `getOnly(p)` **candidate** | *degenerate* |
| `findFirst()` have | `findFirst(p)` **candidate** | *degenerate* |
| `findLast()` have | `findLast(p)` **candidate** | *degenerate* |
| `findOnly()` have | `findOnly(p)` **candidate** | *degenerate* |
| `get(i)` have | — | — |
| `find(i)` **candidate** | — | — |

The `Of` column returns an element you already hold, up to equality.
`findFirstOf` is the least degenerate, since equality is not identity, and
still does not earn a name.

`find(i)` is independent of predicate matching: it concerns bounds. Its
possible name and shape do not by themselves justify adding it.

### Sequence

| identity | predicate | `Of` |
|---|---|---|
| n/a (identity) | `filter(p)` have | *`filterOf(o)`* — the information is in `countOf`/`indexesOf` |

## Is the predicate column justified?

Avoiding an eager intermediate sequence is not sufficient justification for
a specialized method. `seq.stream().filter(p).findFirst()` already avoids
materializing the filter result and stops at the first match. The same lazy
pipeline mechanism supports mapping, flat-mapping and combinations of stages;
short-circuiting does not uniquely justify condensing a filter and terminal.
Predicate `get`/`find` overloads could still earn a place as natural ways to
express common queries, but that demand has not been established here.

Predicate indexes have a distinct benefit: filtering loses original positions.
They also cover searching projected values:

```java
seq.index(e -> Objects.equals(f.apply(e), value)) // proposed API
```

This expresses `seq.map(f).indexOf(value)` without materializing mapped
values. Unlike filtering, mapping preserves positions. Predicate indexes
therefore serve more than filter fusion, but preserving information does not
establish how often callers need it. Indexes can identify match locations or
feed `slice` without feeding `get`; these are valid uses, currently not enough
to prioritize expanding the positional API.

There is no planned family of predicate terminals such as `min(p)`, `max(p)`,
`sum(p)`, `reduce(p, ...)`, `toList(p)`, `toSet(p)` or `sorted(p)`. Nor does
the existence of a composition automatically rule out a method. The question
is whether the operation naturally belongs in the library's everyday
vocabulary, not whether a table can be completed or an intermediate avoided.

## Counting is a separate candidate

`count(p)` and `countOf(value)` directly name a requested summary. Their case
is natural expression and consolidation, with avoiding intermediate storage
as an additional benefit, not short-circuiting.

`countBy(key)` belongs to keyed aggregation rather than direct matching, but
should be considered alongside them. It would count occurrences per projected
key, not matches to a predicate. `groupBy(key, Seq::size)` currently builds
groups just to count them; a dedicated operation could accumulate `long`
counts using storage proportional to the number of keys. These are candidates
to assess independently, not commitments implied by the search tables.

## Footnote: the sentinel

`size()` would compose better than `-1` for forward search, and `-1` for
backward search. The sentinel wants to be the identity of the reduction:
`indexOf` is a min, `lastIndexOf` is a max.

```java
limit(indexOf(o))            // prefix before first o, whole seq if absent
slice(indexOf(o), size())    // suffix from first o, empty if absent
min(indexOf(a), indexOf(b))  // first occurrence of either, correct if one is absent
```

It would also make `limit(indexOf(o))` equal `takeWhile(not(o::equals))` in the
absent case, and make `index()` cleanly `0`. Blocked by `List.indexOf` parity,
and a split convention would be worse than either. Recorded so it is not
rediscovered.

## Summary

No predicate-search expansion is currently planned. Retain the naming scheme
for evaluating concrete proposals; predicate indexes are valid but currently
unprioritized, and predicate `get`/`find` overloads are not justified solely
by short-circuiting. Neither `lastIndex()` nor `find(i)` earns a place merely
by filling a cell. Consider counting independently on natural expression,
consolidation and storage benefits.
