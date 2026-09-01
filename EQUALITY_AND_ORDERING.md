# Equality, matching and ordering

This is a naming and API-shape guide, not a list of promised additions. Use it
when reviewing an existing or proposed operation so related methods do not grow
arbitrary, superficially symmetrical overloads.

## Classify the operation first

| Kind | Question asked | Customization | Existing examples |
|---|---|---|---|
| Ordering | Which element comes before another? | `Comparator` | `sorted`, `min`, `max` |
| Equivalence classes | Which elements have the same global identity? | key `Function`; optionally a `Comparator` | `distinct`, `setEquals`, `multisetEquals`, `containsMultiset`, `intersection`, `difference`, `union` |
| Direct matching | Do particular elements satisfy this match? | `Predicate` for a query; `BiPredicate` for two inputs | `contains`, `indexOf`, `listEquals`, the slice searches, `startsWith`, `endsWith` |

`Seq.equals` and `hashCode` are not customizable: they define the value's
object contract.

## Ordering

Ordering operations take a `Comparator`; their no-argument overloads use
natural order:

```java
sorted()                 sorted(comparator)
min()                    min(comparator)
max()                    max(comparator)
```

A projected form such as `sortedBy(key)` adds convenience, not semantic
capability: `sorted(comparing(key))` already works. It earns a method chiefly
if implemented as decorate-sort-undecorate, evaluating an expensive key once
per element rather than once per comparison. A complete projected sorting
pair would be:

```java
sortedBy(key)                         // naturally ordered key
sortedBy(key, keyComparator)          // explicitly ordered key
```

The same argument is weaker for `minBy` and `maxBy`, whose comparator forms
are already linear.

Do not provide a generic `keying(comparator)` adapter. A wrapper can delegate
`Comparable.compareTo` to a comparator, but it cannot in general derive a
useful consistent `hashCode`. Giving it the only universal hash, zero, makes
apparently ordinary uses such as `distinctBy(keying(comparator))` quadratic.

## Equivalence classes

The default relation is `equals`; hashing is its usual implementation:

```java
distinct()
multisetEquals(that)
intersection(that)
```

A key projection defines equality through the key's `equals`/`hashCode` while
preserving original elements. The clearest names use `By`:

```java
distinctBy(key)
groupBy(key)
```

`groupBy` is the primary operation: grouping necessarily needs a classifier.
There is no need for an ambiguous no-argument `group()` merely for symmetry.

A comparator can also define equivalence through
`comparator.compare(a, b) == 0`, normally implemented with a tree in
O(n log n):

```java
distinct(comparator)
groupBy(key, keyComparator)
```

This is useful when comparison logic exists but no practical canonical,
hashable key is exposed. It is still the same high-level operation, but it may
not use the same equivalence relation as `equals` (for example,
`BigDecimal.compareTo` versus `BigDecimal.equals`). Do not add comparator
variants one at a time for symmetry; consider them as a coherent
equivalence-aware API addition.

Projection-based set and multiset operations such as `intersectionBy` can be
genuinely useful because mapping first loses the original elements. They also
raise questions about operand types, representative selection and encounter
order, so design that family together rather than growing isolated overloads.

## Direct matching

Direct matches are evaluated independently and do not establish global
classes. They need neither hashing nor a transitive equivalence relation. For
two traversed inputs, a `BiPredicate` is the natural generalization:

```java
listEquals(that, matcher)
startsWith(that, matcher)
indexOfSlice(that, matcher)
```

This is more general than `listEqualsBy(key)` and naturally supports operands
of different types. Add such overloads only when the use case warrants them;
the category explains their shape, not their priority.

### Query and search variants

Searching one input against a fixed query is also direct matching. A
`Predicate` can capture the query, key, comparator or tolerance, so specialized
`By` and comparator overloads are normally unnecessary:

```java
indexWhere(person -> person.email().equals(email))
indexWhere(e -> comparator.compare(e, query) == 0)
```

This family has further axes and should be analysed separately rather than
expanded mechanically:

| Equality-specific | Predicate form |
|---|---|
| `contains(value)` | `anyMatch(predicate)` |
| `indexOf(value)` | `indexWhere(predicate)` |
| `lastIndexOf(value)` | `lastIndexWhere(predicate)` |
| `indexesOf(value)` | `indexesWhere(predicate)` |
| frequency of a value | `count(predicate)` |
| find an equal element | `filter(predicate).findFirst()` |

The result may be a boolean, element, index, indexes, count or filtered
sequence; it may find the first, last or all matches; and it may short-circuit
or traverse everything. Consequently there is no universal predicate suffix:
`Where` suits indexes, while `Match`, `find`, `filter` and `count` suit other
results. Add a method only where its name and benefit are clearer than existing
composition. Ordered or binary search is separate again because it introduces
ordering and a sortedness precondition.

## Naming rule

- Keep the base name when an argument directly supplies the operation's
  strategy: `sorted(comparator)`, possibly `distinct(comparator)` or
  `listEquals(that, matcher)`.
- Use `By` when a function projects each element to a key:
  `distinctBy(key)`, `sortedBy(key)`, `groupBy(key)`.
- Do not add overloads just to make every row symmetrical. Prefer the most
  natural abstraction for the category and require a concrete usability or
  performance benefit.

In particular, these similar-looking expressions intentionally use different
key semantics:

```java
distinctBy(key)                    // key.equals / key.hashCode
distinct(comparing(key))           // key.compareTo == 0
```

They agree only when the key's natural ordering is consistent with its
`equals` method.
