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

## API candidates

- Numeric averages and summary statistics; mapper-based sums and products
  already exist.
- `sortedBy(Function)`, considering whether computing keys once per element
  provides enough benefit beyond comparator composition.
- Counting by predicate, value or key. See
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

- Restructure the user introduction to lead with eager operations and the
  library's consolidation of common collection operations; place installation
  after the motivation and examples.
- Reconsider whether the generated introduction should draw from both `Seq`
  and `SeqStream`, or whether it should be authored independently of Javadoc.
  Until that decision changes the workflow, follow the existing generation
  instructions in [DEVELOPMENT.md](docs/DEVELOPMENT.md#updating-the-readme).
