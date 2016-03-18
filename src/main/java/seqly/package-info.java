/**
 * <h3>Introduction</h3>
 *
 * {@code seqly} (short for "sequence library", rhymes with "weekly")
 * is a tiny collections library to fill the remaining gap left in the
 * standard collections libraries after the addition of
 * {@link java.util.stream stream}s in Java 8.
 * It adds a single type, {@link seqly.Seq Seq},
 * which directly implements eager-by-default functional operations,
 * and which can be used seamlessly with the existing
 * {@link java.util.Collection Collection},
 * {@link java.util.stream.Stream Stream},
 * {@link java.util.Optional Optional} and
 * {@link java.lang.String String} types.
 * <p>
 * Java 8 introduced {@link java.util.stream.Stream Stream}s which enable
 * functional operations such as {@code map} and {@code reduce} on collections.
 * Two goals of the {@code Stream}s design was to allow doing these functional
 * operations lazily and in parallel.
 * However, it is also common to want to apply these operations eagerly
 * and sequentially, but the existing streams API makes this verbose.
 * The new type {@link seqly.Seq Seq} directly defines functional-style
 * operations which are implemented eagerly and sequentially.
 * <p>
 * Compare:
 * <pre>{@code
 *     Seq<String> words = Seq.of("one", "two", "three");
 *     Seq<Integer> lengths = words.map(String::length);
 * }</pre>
 * with:
 * <pre>{@code
 *     List<String> words = List.of("one", "two", "three");
 *     List<Integer> lengths = words
 *             .stream()
 *             .map(String::length)
 *             .collect(toList());
 * }</pre>
 * These directly-defined, eager-by-default operations are the reason for
 * this library to exist. This library believes these types of operations are
 * so extremely common that there is no justification for the extra verbosity.
 * Invoking 4 methods instead of 1 just to map a collection is too much.
 *
 * <h3>Relationship with {@code Stream}s</h3>
 *
 * When laziness or parallelization are required, {@link seqly.Seq#stream}
 * can be called as usual, and additionally a no-args version of
 * {@link seqly.Seq#collect} can be called on the result to return a {@code Seq}.
 * <pre>{@code
 *     Seq<Integer> lengths = words
 *             .stream()
 *             .filter(w -> w.contains("e"))
 *             .map(String::length)
 *             .collect();
 * }</pre>
 * All functional operations defined directly on {@code Seq} have the same
 * signature as the {@code Stream} version, so code using both types looks
 * perfectly natural.
 *
 * <h3>Relationship to other {@code Collection}s</h3>
 *
 * {@link seqly.Seq Seq} implements {@link java.util.Collection Collection}
 * and can therefore be used as the default choice for most collection use cases.
 * Most collections code does not require specific features of particular
 * implementations (such as constant-time indexing or restriction of duplicates)
 * so simple efficient types such as {@code Arrays.asList()} or {@code List.of()}
 * are {@code new ArrayList()} are used. {@link seqly.Seq#of(java.lang.Object[])},
 * {@link seqly.Seq#builder()}, etc can be used instead.
 *
 * <h4>Factories</h4>
 *
 * {@code Seq} contains factories to convert from existing collections types and
 * various collection-like types such as {@code String} and {@code Optional}.
 * <pre>{@code
 *     Seq.of(0, 1, 2);
 *     Seq.builder().add("").build();
 *     Seq.copy(new Integer[]{3, 4});
 *     Seq.view(Optional.of(6)); // contains a single element, 6
 *     Seq.view(Arrays.asList(7, 8));
 *     Seq.copy(Arrays.asList(7, 8).iterator());
 *     Seq.copy(Stream.of(1, 2, 3));
 *     Seq.view("five"); // contains 'f', 'i', 'v' and 'e'
 * }</pre>
 *
 * <h4>Converting back to other types</h4>
 *
 * {@code Seq} implements {@code Collection} (edit: List?) and so can simply be used
 * as-is when {@code Collection}s as required. For {@code Optional}s, as well as
 * {@link seqly.Seq#findFirst}, there is {@code findLast} and {@code findOnly}
 * which throws for multiple elements.
 *
 * <h3>Other methods</h3>
 *
 * Various useful operations on collection-like types are spread around
 * in a few different places in Java. {@code Seq} defines these methods
 * directly to reduce the. Hmm..... is this justified?
 * <p>
 * In addition to defining the functional-style stream methods, many other
 * useful methods are defined, including those which should be defined for all
 * collections found in {@code Collections} utility class, {@code String},
 * {@code List} and {@code Set}, such as {@code nCopies}, {@code shuffled},
 * {@code indexOf} (lists), {@code slice} (aka subList), {@code difference} (sets).
 * The main aim with extra methods is to unify collection-like methods but also
 * to add common methods whose omission is surprising.
 */
package seqly;
