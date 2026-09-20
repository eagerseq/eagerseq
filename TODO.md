# TODO

Outstanding work only. Settled design and working practices belong in
[CONTRIBUTING.md](CONTRIBUTING.md) or its linked references. Candidate APIs
below are subjects to assess, not commitments to implement.

## Compatibility and robustness

- Address premature source binding in lazy intermediates over live collection
  views. For an `ArrayList` initially containing `[2, 1]`, construct
  `Seq.viewOf(list).stream().sorted()`, then append `3` before invoking a
  terminal: `count()` returns `2`, while `toList()` in a separate reproduction
  throws `ConcurrentModificationException`. The equivalent JDK sorted stream
  observes all three elements. Stale counts also occur with `reversed()` and
  `scan()`; `rotated()` and `shuffled()` use the same size-capture pattern.
  Investigate when size queries bind sources and reconcile sizing optimizations
  with the intended lazy/live-view semantics. Deferring size discovery is one
  possible direction, not a prescribed implementation.
- Decide when to raise the Java 8 release target. Preserve the deliberate
  snapshot semantics of `Seq.reversed()` when reviewing newer JDK contracts.
- Decide whether serialization is in scope. Current implementations are not
  `Serializable`; investigate Jackson writing and reading before specifying
  support or adding a module.
- Review the remaining dependencies and buffering choices in `Sources`.
  `ArrayBuilder` is independent of the result types, but coexists with
  `ArrayList` accumulation. Assess whether simplifying that tradeoff is worth
  extra copying using relevant measurements. `toStream()` already lives on
  `SeqStream`; moving it is no longer outstanding work.

## Performance evidence

- Refresh measurements affected by implementation changes recorded in
  [bench/RESULTS.md](bench/RESULTS.md), and assess the current implementation
  against the working budget in [bench/README.md](bench/README.md). Historical
  ratios do not establish current performance. Include small eager workloads
  as well as lazy pipelines and distinguish allocation costs from timing.
- Assess the remaining costs of slicing and tail operations, particularly on
  array-backed or sized inputs. `skip` traverses discarded elements and
  `limitLast` drains through a circular buffer even when more source information
  is available. Consider specialization or other approaches where measurements
  justify them, preserving snapshot and callback semantics.

## API candidates

- Numeric averages and summary statistics; mapper-based sums and products
  already exist.
- `sortedBy(Function)`, considering whether computing keys once per element
  provides enough benefit beyond comparator composition.
- Counting by predicate or key; value frequency is available via `frequency`.
  See
  [DIRECT_MATCHING.md](docs/DIRECT_MATCHING.md#counting-is-a-separate-candidate)
  for the rationale; search-family symmetry is not an implementation plan.
- Positional copy-modify operations such as `updated` or `patch`, currently
  expressed through slicing and concatenation.
- Revisit a general `gather` only with concrete demand; the reasons for deferral
  are recorded in [the design reference](docs/DESIGN.md#deliberate-api-limits).

## Test depth

- Extend independent reference checks where useful beyond the current index,
  multiplicity and combinatorial coverage. Predicate/mapping operations and
  shuffling rely mainly on example tests; reversal and rotation references
  share JDK algorithms with the implementation.
- Systematically check exact input consumption for short-circuiting terminals.
  Terminating on an infinite source alone does not prove no extra element was
  consumed.
- Review terminal validation and failure coverage across operations: validation
  before claiming streams or invoking callbacks, callback exception propagation,
  stopping after failure, and stream consumption after failure. Address shared
  concerns consistently rather than only for each newly added operation.

## User documentation

- Address misleading custom-implementation guidance. The introduction says
  implementing `spliterator()` supplies all other behaviour through defaults,
  but value-based `equals`, `hashCode` and `toString` are implemented by
  `AbstractSeq`. For `Seq<Integer> custom = () -> Seq.of(1, 2).spliterator()`,
  `custom.equals(Seq.of(1, 2))` is false while the reverse comparison is true.
  Clarify or otherwise address the extension contract; explaining `AbstractSeq`
  and the obligations of direct implementers is one possible direction.
- Reconcile introductory claims with the API: functional signatures are not
  universally identical to `Stream` (`flatMap` accepts `Iterable` on `Seq`),
  and collection mutators do have default implementations, which throw.
  Make changes in the authoritative Javadoc and regenerate the README under
  the existing workflow.
- Review the breadth of the recommendation to use `Seq` as the default
  collection and the visibility of its tradeoffs. Explain its fit for finite,
  ordered, read-mostly data and make the deliberate parallel-evaluation and
  partial-traversal resource-closure differences discoverable from the user
  introduction. Link to the existing contracts rather than duplicating the
  detailed stream semantics.
- Restructure the user introduction to lead with eager operations and the
  library's consolidation of common collection operations; place installation
  after the motivation and examples.
- Reconsider whether the generated introduction should draw from both `Seq`
  and `SeqStream`, or whether it should be authored independently of Javadoc.
  Until that decision changes the workflow, follow the existing generation
  instructions in [DEVELOPMENT.md](docs/DEVELOPMENT.md#updating-the-readme).
