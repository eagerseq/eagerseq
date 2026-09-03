# Direct matching families

Companion to `EQUALITY_AND_ORDERING.md`, covering only direct matching: one
input searched against a query. Not equivalence classes (`distinct`, set
operations) or ordering (`sorted`, `min`).

## The naming axis

```
foo()          ==  foo(alwaysTrue())     // identity query, overloads the base name
fooOf(object)  ==  foo(object::equals)   // equality query
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

Status: **have**, **add**, *reject* (fits the pattern, not worth a name),
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
| `count()` have (`size()` is the `int` alias) | `count(p)` **add** | `countOf(o)` **add** |

### Index (`int`, `-1`)

| identity | predicate | `Of` |
|---|---|---|
| `index()` n/a — `isEmpty() ? -1 : 0`, a boolean spelled wrong | `index(p)` **add** | `indexOf(o)` have |
| `lastIndex()` **add** — `size()-1`, lands on `-1` when empty | `lastIndex(p)` **add** | `lastIndexOf(o)` have |
| `indexes()` have | `indexes(p)` **add** | `indexesOf(o)` have |

`Optional` variants of the whole index family (`findIndex` and friends) are
rejected: `Optional<Integer>` boxes, `OptionalInt` will not chain, `-1` is
`List` parity, and it would double the largest family for one idiom.

### Element

| identity | predicate | `Of` |
|---|---|---|
| `getFirst()` have | `getFirst(p)` **add** | *degenerate* |
| `getLast()` have | `getLast(p)` **add** | *degenerate* |
| `getSingle()` have | `getSingle(p)` **add** | *degenerate* |
| `findFirst()` have | `findFirst(p)` **add** | *degenerate* |
| `findLast()` have | `findLast(p)` **add** | *degenerate* |
| `findSingle()` have | `findSingle(p)` **add** | *degenerate* |
| `get(i)` have | — | — |
| `find(i)` **add** | — | — |

The `Of` column returns an element you already hold, up to equality.
`findFirstOf` is the least degenerate, since equality is not identity, and
still does not earn a name.

`find(i)` is the only unambiguous hole here and is independent of every open
question above: it concerns bounds, not matching.

### Sequence

| identity | predicate | `Of` |
|---|---|---|
| n/a (identity) | `filter(p)` have | *`filterOf(o)`* — the information is in `countOf`/`indexesOf` |

## Is the predicate column justified?

It applies to *every* terminal, so something has to gate it. Two properties do,
and "avoids materialising an intermediate `Seq`" is not one of them — that is
equally true of `min(p)`, `sum(p)` and the rest, which nobody wants.

1. **Information loss.** `filter` destroys positions, so the index forms have
   no clean composition at all. Airtight, and independent of performance.
2. **Short-circuiting.** On eager `Seq`, `filter(p).findFirst()` is always
   Θ(n) where the fused form is O(k). A complexity class, not a constant.
   Covers `First` and `Single`; excludes anything Θ(n) either way.

So `index(p)` is the best-justified member, not an overreach. `findLast(p)` and
`getLast(p)` pass only for indexed representations that can scan backwards.
`count(p)` passes neither gate and is added on convention alone — the name is
shared by Scala, Kotlin, LINQ, Ruby and Eclipse Collections.

Explicitly excluded, so the boundary is written down rather than remembered:
`min(p)`, `max(p)`, `sum(p)`, `reduce(p, ...)`, `toList(p)`, `toSet(p)`,
`sorted(p)`. All are exactly `filter(p).op()`.

Bundling a *mapper* is never the same win: `map` preserves size and position,
so `op(f)` always factors as `map(f).op()` or `op().map(f)`. Where a mapper is
bundled — `groupBy(key, valueMapper)`, `toMap` — it applies to a result
component, not to the matching.

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

Without predicates: `lastIndex()`, `find(i)`, `countOf(o)`.

With predicates: `index(p)`, `lastIndex(p)`, `indexes(p)` (gate 1);
`findFirst(p)`, `getFirst(p)`, `findSingle(p)`, `getSingle(p)` (gate 2);
`findLast(p)`, `getLast(p)` (weaker); `count(p)` (convention).

The smallest defensible set is the first seven predicate methods plus the
three above.
