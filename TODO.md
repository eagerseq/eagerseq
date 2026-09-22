# TODO

Outstanding work only. Settled design and working practices belong in
[CONTRIBUTING.md](CONTRIBUTING.md) or its linked references. Candidate APIs
below are subjects to assess, not commitments to implement.

## Compatibility and robustness

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
  [seq-bench/RESULTS.md](seq-bench/RESULTS.md), and assess the current implementation
  against the working budget in [seq-bench/README.md](seq-bench/README.md). Historical
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
  multiplicity, combinatorial and generated-pipeline coverage. Shuffling
  relies mainly on example tests; extend independent rotation checks beyond
  the fixed distance used by the generated pipelines.
- Extend the counted-source checks in `TraversalTest` to the remaining
  short-circuiting terminals and both operands of pairwise operations,
  including unequal lengths and failure paths. The current checks cover
  first/index lookup, matching, prefix/slice search, uniqueness and bounded
  prefix transformations; termination on an infinite source alone does not
  prove no extra element was consumed.
- Review terminal validation and failure coverage across operations: validation
  before claiming streams or invoking callbacks, callback exception propagation,
  stopping after failure, and stream consumption after failure. Address shared
  concerns consistently rather than only for each newly added operation.
