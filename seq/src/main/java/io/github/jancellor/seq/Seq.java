package io.github.jancellor.seq;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * An ordered, reusable collection with eager transformations and additional
 * collection operations. Methods such as {@link #map(Function)},
 * {@link #filter(Predicate)} and {@link #sorted()} evaluate immediately and return
 * new sequences. Operations such as {@link #groupBy(Function)},
 * {@link #zip(Iterable, BiFunction)}
 * and {@link #windowFixed(int)} provide further ways to organize and combine data.
 *
 * <pre>{@code
 * Seq<String> words = Seq.of("pear", "apple", "plum");
 * Seq<Integer> lengths = words.map(String::length);
 * }</pre>
 *
 * <h2>Order, ownership and equality</h2>
 *
 * <p>Every sequence has a defined encounter order, stable in the absence of
 * mutation. Collection mutators throw {@link UnsupportedOperationException}.
 * The {@code copyOf} factories and transformations such as {@code map},
 * {@code filter} and {@link #reversed()} produce shallow snapshots: element
 * references are copied, but the elements themselves are not cloned.
 * The explicit {@link #viewOf(Object[]) array} and
 * {@link #viewOf(Collection) collection} views reflect changes to their backing
 * data. Refusing collection mutation does not imply immutable backing data or
 * immutable elements.
 *
 * <p>Equality and hashing follow the ordered element contracts of
 * {@link #equals(Object)} and {@link #hashCode()}. A {@code Seq} is only equal
 * to another {@code Seq}, never to a {@link List}. Use
 * {@link #listEquals(Iterable)}, {@link #setEquals(Iterable)} or
 * {@link #multisetEquals(Iterable)} to compare with other iterables under those
 * respective equality rules.
 *
 * <h2>Lazy composition</h2>
 *
 * <p>{@link #stream()} returns a single-use {@link SeqStream}, a subtype of
 * {@link Stream} retaining additional sequence operations.
 * {@link SeqStream#toSeq()} collects its results into a reusable sequence.
 * {@code SeqStream}'s own operations evaluate sequentially even in parallel
 * mode; see {@link SeqStream#parallel()} and {@link SeqStream#toStream()}.
 *
 * <h2>Indexing and size</h2>
 *
 * <p>Array-backed sequences produced by this library, including array views,
 * support constant-time {@link #get(int)}, {@link #size()} and {@link #isEmpty()}.
 * Collection views use linear-time indexing and delegate {@code size} and
 * {@code isEmpty} to the backing collection. Custom implementations inherit
 * traversal-based defaults unless they override them or supply size information
 * through their source.
 *
 * <h2>Custom implementations</h2>
 *
 * <p>Extend {@link AbstractSeq} and implement {@link #spliterator()} to create a
 * custom sequence. The base class supplies the required value implementations
 * of {@code equals}, {@code hashCode} and {@code toString}; the interface
 * supplies default collection and sequence operations. Implementations that
 * implement {@code Seq} directly must also provide those value semantics.
 * Each call to {@code spliterator()} must return a fresh traversal that follows
 * the {@link Source} contract and reports {@link Spliterator#ORDERED}.
 */
public interface Seq<E> extends Collection<E> {

    /**
     * Returns an empty {@code Seq}.
     */
    static <E> Seq<E> of() {
        return new ArraySeq<>(ArrayBuilder.EMPTY);
    }

    /**
     * Returns a {@code Seq} containing the given element.
     */
    static <E> Seq<E> of(E element) {
        return new ArraySeq<>(new Object[]{element});
    }

    /**
     * Returns a {@code Seq} containing the given elements.
     */
    @SafeVarargs
    static <E> Seq<E> of(E... elements) {
        return new ArraySeq<>(Arrays.copyOf(
                requireNonNull(elements), elements.length));
    }

    /**
     * Returns a {@code Seq} containing the given element if not null or no elements otherwise.
     */
    static <E> Seq<E> ofNullable(E element) {
        return element == null ? of() : of(element);
    }

    /**
     * Returns a {@code Seq} containing the given elements.
     */
    static <E> Seq<E> copyOf(E[] array) {
        return new ArraySeq<>(Arrays.copyOf(
                requireNonNull(array), array.length));
    }

    /**
     * Returns a {@code Seq} containing the given elements in constant time.
     * Does not copy the argument, so subsequent mutations to the argument
     * are reflected in the returned {@code Seq}.
     */
    static <E> Seq<E> viewOf(E[] array) {
        return new ArraySeq<>(requireNonNull(array));
    }

    /**
     * Returns a {@code Seq} containing the given elements.
     */
    static <E> Seq<E> copyOf(Iterable<? extends E> iterable) {
        return new ArraySeq<>(Sources.toArray(requireNonNull(iterable)));
    }

    /**
     * Returns a {@code Seq} containing the given elements in constant time.
     * Does not copy the argument, so subsequent mutations to the argument
     * are reflected in the returned {@code Seq}.
     * Unlike most {@code Seq} instances, the returned instance will not
     * support constant-time {@link #get(int)}. Its {@link #size()} and
     * {@link #isEmpty()} methods delegate to the argument.
     *
     * <p>The argument's spliterator must report {@link Spliterator#ORDERED},
     * or this method throws.
     * See also {@link #copyOf(Iterable)} and {@link SeqStream#viewOf(Iterator)}.
     */
    static <E> Seq<E> viewOf(Collection<? extends E> collection) {
        return new CollectionSeq<>(requireNonNull(collection));
    }

    /**
     * Returns a {@code Seq} containing the value of the optional
     * if it is present, otherwise returns an empty {@code Seq}.
     * Unlike {@code Seq.of(optional)}, the optional itself is not an element
     * of the returned {@code Seq}.
     */
    static <E> Seq<E> copyOf(Optional<? extends E> optional) {
        requireNonNull(optional);
        return optional.isPresent() ? of(optional.get()) : of();
    }

    /**
     * Returns a {@code Seq} containing the given elements.
     */
    static <E> Seq<E> copyOf(Iterator<? extends E> iterator) {
        return new ArraySeq<>(Sources.toArray(requireNonNull(iterator)));
    }

    /**
     * Returns a {@code Seq} containing the given elements.
     */
    static <E> Seq<E> copyOf(Spliterator<? extends E> spliterator) {
        return new ArraySeq<>(Sources.toArray(requireNonNull(spliterator)));
    }

    /**
     * Returns a {@code Seq} containing the given elements.
     */
    static <E> Seq<E> copyOf(Stream<? extends E> stream) {
        return new ArraySeq<>(requireNonNull(stream).toArray());
    }

    /**
     * Returns a {@link Builder}.
     */
    static <E> Builder<E> builder() {
        return new SeqBuilder<>();
    }

    /**
     * Returns a {@code Collector} that accumulates input elements
     * into a {@code Seq}.
     */
    static <E> Collector<E, ?, Seq<E>> toSeq() {
        return Collector.<E, ArrayBuilder<E>, Seq<E>>of(
                ArrayBuilder::new, ArrayBuilder::accept,
                ArrayBuilder::combine,
                builder -> viewOf(builder.buildArray()));
    }

    /**
     * Returns a {@code Seq} containing consecutive integers
     * from {@code from} (inclusive) to {@code to} (exclusive)
     * if {@code from < to}, or an empty {@code Seq} otherwise.
     */
    static Seq<Integer> range(int from, int to) {
        return copyOf(Sources.range(from, to));
    }

    /**
     * Returns a {@code Seq} containing consecutive integers
     * from {@code from} (inclusive) to {@code to} (exclusive)
     * if {@code from < to}, or an empty {@code Seq} otherwise.
     */
    static Seq<Long> range(long from, long to) {
        return copyOf(Sources.range(from, to));
    }

    /**
     * Returns a {@code Seq} containing consecutive integers
     * from {@code from} (inclusive) to {@code to} (inclusive)
     * if {@code from <= to}, or an empty {@code Seq} otherwise.
     */
    static Seq<Integer> rangeClosed(int from, int to) {
        return copyOf(Sources.rangeClosed(from, to));
    }

    /**
     * Returns a {@code Seq} containing consecutive integers
     * from {@code from} (inclusive) to {@code to} (inclusive)
     * if {@code from <= to}, or an empty {@code Seq} otherwise.
     */
    static Seq<Long> rangeClosed(long from, long to) {
        return copyOf(Sources.rangeClosed(from, to));
    }

    /**
     * Returns a {@code Seq} containing the given element {@code count} times.
     */
    static <E> Seq<E> repeat(E element, int count) {
        Sources.requireNonNegativeArgument("count", count);
        return copyOf(Sources.repeat(element, count));
    }

    /**
     * Returns a {@code Seq} containing the results of calling
     * {@code supplier} {@code count} times.
     */
    static <E> Seq<E> generate(Supplier<? extends E> supplier, int count) {
        requireNonNull(supplier);
        Sources.requireNonNegativeArgument("count", count);
        return copyOf(Sources.generate(supplier, count));
    }

    /**
     * Returns a {@code Seq} containing {@code seed}, {@code next.apply(seed)},
     * and so on, up to but excluding the first element for which
     * {@code hasNext} returns false.
     * The result is empty if {@code hasNext} rejects {@code seed}.
     */
    static <E> Seq<E> iterate(
            E seed, Predicate<? super E> hasNext, UnaryOperator<E> next) {
        requireNonNull(hasNext);
        requireNonNull(next);
        return copyOf(Sources.iterate(seed, hasNext, next));
    }

    /**
     * Returns a {@code Seq} containing the result of concatenating each of
     * the given {@code Iterable} arguments in order.
     * Equivalent to {@link #sum(Iterable)} but as a static varargs method.
     */
    @SafeVarargs
    static <E> Seq<E> concat(
            Iterable<? extends E>... iterables) {
        requireNonNull(iterables);
        Arrays.stream(iterables).forEach(Objects::requireNonNull);
        return copyOf(Sources.concat(Sources::toSource, iterables));
    }

    /**
     * Returns a {@code Seq} containing the result of concatenating each
     * {@code Iterable} element in the given {@code Iterable} in order.
     * Consistent with {@link #flatMap(Function)} in that {@code null}
     * elements are treated as empty.
     */
    static <E> Seq<E> flatten(
            Iterable<? extends Iterable<? extends E>> iterables) {
        requireNonNull(iterables);
        return copyOf(Sources.flatten(
                Sources.toSource(iterables), Sources::toSource));
    }

    /**
     * Returns a {@link Source} that reports {@link Spliterator#ORDERED}
     * and has a stable encounter order. This order defines
     * {@link #equals(Object)}, {@link #hashCode()} and other order-sensitive
     * operations, and must not change in the absence of mutation.
     */
    Source<E> spliterator();

    /**
     * Equivalent to {@code toList().hashCode()}.
     * See {@link #equals(Object)} for the corresponding equality contract.
     */
    int hashCode();

    /**
     * Equivalent to
     * {@code o instanceof Seq && toList().equals(((Seq<?>) o).toList())}.
     */
    boolean equals(Object o);

    /**
     * Equivalent to {@code toList().toString()}.
     */
    String toString();

    /**
     * Returns an empty {@code Optional} if this {@code Seq} is empty,
     * an {@code Optional} containing its only element if it is not null,
     * throws {@code IllegalStateException} if it has multiple elements,
     * or throws {@code NullPointerException} if its only element is null.
     * See also
     * {@link #findFirst()}, {@link #findLast()} and {@link #findOnly()}.
     */
    default Optional<E> toOptional() {
        return Sources.toOptional(spliterator());
    }

    /**
     * Returns an unmodifiable list containing the elements of this sequence.
     */
    default List<E> toList() {
        return Sources.toList(spliterator());
    }

    /**
     * Returns an unmodifiable set containing the distinct elements of this
     * sequence in encounter order.
     */
    default Set<E> toSet() {
        return Sources.toSet(spliterator());
    }

    /**
     * Equivalent to {@link #toMap(Function, Function, BinaryOperator) toMap(e -> e, e -> e, throws)}.
     */
    default Map<E, E> toMap() {
        return Sources.toMap(spliterator());
    }

    /**
     * Equivalent to {@link #toMap(Function, Function, BinaryOperator) toMap(keyMapper, e -> e, throws)}.
     */
    default <K> Map<K, E> toMap(
            Function<? super E, ? extends K> keyMapper) {
        requireNonNull(keyMapper);
        return Sources.toMap(spliterator(), keyMapper);
    }

    /**
     * Equivalent to {@link #toMap(Function, Function, BinaryOperator) toMap(keyMapper, valueMapper, throws)}.
     */
    default <K, V> Map<K, V> toMap(
            Function<? super E, ? extends K> keyMapper,
            Function<? super E, ? extends V> valueMapper) {
        requireNonNull(keyMapper);
        requireNonNull(valueMapper);
        return Sources.toMap(spliterator(), keyMapper, valueMapper);
    }

    /**
     * Returns an unmodifiable map built from the mapped keys and values,
     * in encounter order, applying the given function to the existing and
     * the new value whenever multiple elements map to the same key.
     */
    default <K, V> Map<K, V> toMap(
            Function<? super E, ? extends K> keyMapper,
            Function<? super E, ? extends V> valueMapper,
            BinaryOperator<V> merger) {
        requireNonNull(keyMapper);
        requireNonNull(valueMapper);
        requireNonNull(merger);
        return Sources.toMap(
                spliterator(), keyMapper, valueMapper, merger);
    }

    /**
     * Returns {@code true} only if the given {@code Iterable}
     * contains the same elements in the same order
     * according to the {@code equals} method of individual elements.
     * The type of the given {@code Iterable} is irrelevant.
     */
    default boolean listEquals(Iterable<?> that) {
        requireNonNull(that);
        return Sources.listEquals(spliterator(), that.spliterator());
    }

    /**
     * Returns {@code true} only if the given {@code Iterable}
     * would contain the same elements in any order
     * after removing repeated elements from both {@code Collection}s
     * according to the {@code equals} method of individual elements.
     * The result does not depend on the multiplicity of repeated elements.
     * The type of the given {@code Iterable} is irrelevant.
     * See also {@link #multisetEquals(Iterable)}.
     */
    default boolean setEquals(Iterable<?> that) {
        requireNonNull(that);
        return Sources.setEquals(spliterator(), that.spliterator());
    }

    /**
     * Returns {@code true} only if the given {@code Iterable}
     * contains the same elements in any order
     * according to the {@code equals} method of individual elements.
     * In other words, {@code true} only if some permutation of the given
     * {@code Iterable} contains the same elements in the same order.
     * The result depends on the multiplicity of repeated elements.
     * The type of the given {@code Iterable} is irrelevant.
     * See also {@link #setEquals(Iterable)}.
     */
    default boolean multisetEquals(Iterable<?> that) {
        requireNonNull(that);
        return Sources.multisetEquals(spliterator(), that.spliterator());
    }

    /**
     * Returns a {@code Seq} that contains the results of applying the given
     * {@code BiFunction} pairwise to elements of this {@code Seq} and the given
     * {@code Iterable}.
     * If one is longer than the other, its extra elements are ignored.
     */
    default <F, R> Seq<R> zip(
            Iterable<? extends F> that,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        requireNonNull(that);
        requireNonNull(mapper);
        return copyOf(Sources.zip(spliterator(), that.spliterator(), mapper));
    }

    /**
     * Returns consecutive integers
     * from zero (inclusive) to the size of this {@code Seq} (exclusive).
     */
    default Seq<Integer> indexes() {
        return copyOf(Sources.indexes(spliterator()));
    }

    /**
     * Returns those elements of this {@code Seq} that are also present
     * in the given {@code Iterable}
     * according to the {@code equals} method of individual elements.
     * Uses the multiset definition of the intersection, which is consistent
     * with the ordinary set definition when there are no repeated elements.
     * When there are {@code a} repeated elements in {@code this}
     * and {@code b} in {@code that}, the result contains
     * {@code min(a, b)} elements. Specifically, it contains those elements
     * from this {@code Seq} which occur first in encounter order.
     * The {@link #difference(Iterable)} contains the remaining
     * {@code max(0, a - b)} elements from this {@code Seq}.
     * See also {@link #union(Iterable)}, {@link #sum(Iterable)}.
     */
    default Seq<E> intersection(Iterable<?> that) {
        requireNonNull(that);
        return copyOf(Sources.intersection(spliterator(), that.spliterator()));
    }

    /**
     * Returns those elements of this {@code Seq} that are not also present
     * in the given {@code Iterable}
     * according to the {@code equals} method of individual elements.
     * Uses the multiset definition of the difference, which is consistent
     * with the ordinary set definition when there are no repeated elements.
     * When there are {@code a} repeated elements in {@code this}
     * and {@code b} in {@code that}, the result contains
     * {@code max(0, a - b)} elements. Specifically, it contains those elements
     * from this {@code Seq} which occur last in encounter order.
     * The {@link #intersection(Iterable)} contains the remaining
     * {@code min(a, b)} elements from this {@code Seq}.
     * See also {@link #union(Iterable)}, {@link #sum(Iterable)}.
     */
    default Seq<E> difference(Iterable<?> that) {
        requireNonNull(that);
        return copyOf(Sources.difference(spliterator(), that.spliterator()));
    }

    /**
     * Returns the difference of this {@code Seq} and the given
     * {@code Iterable}, followed by the difference in the other direction.
     * Uses the multiset definition: when a value occurs {@code a} times in
     * this sequence and {@code b} times in the other, the result contains
     * {@code abs(a - b)} occurrences. Surviving elements are the last
     * occurrences in each input, in encounter order, with this sequence's
     * survivors first. Equality uses the elements' {@code equals} method.
     * Equivalent to {@code difference(that).sum(that.difference(this))}
     * when {@code that} is a {@code Seq}.
     * See also {@link #intersection(Iterable)}, {@link #union(Iterable)}.
     */
    default Seq<E> symmetricDifference(Iterable<? extends E> that) {
        requireNonNull(that);
        return copyOf(Sources.symmetricDifference(spliterator(),
                Sources.toSource(that)));
    }

    /**
     * Returns the elements of this {@code Seq} followed by those elements of
     * the given {@code Iterable} that are not present in this {@code Seq}
     * according to the {@code equals} method of individual elements.
     * Uses the multiset definition of the union, which is consistent
     * with the ordinary set definition when there are no repeated elements.
     * When there are {@code a} repeated elements in {@code this}
     * and {@code b} in {@code that}, the result contains
     * {@code max(a, b)} elements. Specifically, it contains all {@code a}
     * elements from this {@code Seq} then those {@code max(0, b - a)} elements
     * from the given {@code Iterable} which occur last in encounter order.
     * Equivalent to {@code sum(that.difference(this))}.
     * See also {@link #intersection(Iterable)}, {@link #difference(Iterable)},
     * {@link #sum(Iterable)}.
     */
    default Seq<E> union(Iterable<? extends E> that) {
        requireNonNull(that);
        return copyOf(Sources.union(spliterator(), Sources.toSource(that)));
    }

    /**
     * Returns the elements of this {@code Seq} followed by
     * the elements of the given {@code Iterable}.
     * Equivalent to {@link #concat(Iterable...)} but as an instance method.
     * See also {@link #intersection(Iterable)}, {@link #difference(Iterable)},
     * {@link #union(Iterable)}.
     */
    default Seq<E> sum(Iterable<? extends E> that) {
        requireNonNull(that);
        return copyOf(Sources.concat(Sources::toSource, this, that));
    }

    /**
     * Equivalent to
     * {@link #difference(Iterable) that.difference(this).isEmpty()}.
     * See also {@link #intersection(Iterable)},
     * {@link #union(Iterable)}, {@link #sum(Iterable)}.
     */
    default boolean containsMultiset(Iterable<?> that) {
        requireNonNull(that);
        return Sources.containsMultiset(spliterator(), Sources.toSource(that));
    }

    /**
     * Returns whether this sequence and the given {@code Iterable} have
     * no elements in common according to the elements' {@code equals}
     * method. Repeated elements do not affect the result.
     * Equivalent to {@code intersection(that).isEmpty()}.
     * Stops traversing this sequence at the first match.
     * See also {@link Collections#disjoint(Collection, Collection)}.
     */
    default boolean disjoint(Iterable<?> that) {
        requireNonNull(that);
        return Sources.disjoint(spliterator(), that.spliterator());
    }

    /**
     * Returns all full-length permutations
     * in lexical order of the original index of each element,
     * ie equivalent to {@code permutations(size())}.
     */
    default Seq<Seq<E>> permutations() {
        return copyOf(
                Sources.map(Sources.<E>permutations(toArray()), Seq::viewOf));
    }

    /**
     * Returns all permutations of length {@code k}
     * in lexical order of the original index of each element.
     * Returns an empty {@code Seq} if {@code k} exceeds {@link #size()}.
     */
    default Seq<Seq<E>> permutations(int k) {
        Sources.requireNonNegativeArgument("k", k);
        return copyOf(Sources.map(
                Sources.<E>permutations(toArray(), k), Seq::viewOf));
    }

    /**
     * Returns all permutations of all lengths from zero to {@link #size()}
     * in shortlex order of the original index of each element.
     */
    default Seq<Seq<E>> allPermutations() {
        return copyOf(Sources.map(
                Sources.<E>allPermutations(toArray()), Seq::viewOf));
    }

    /**
     * Returns all combinations of length {@code k}
     * in lexical order of the original index of each element.
     * Returns an empty {@code Seq} if {@code k} exceeds {@link #size()}.
     */
    default Seq<Seq<E>> combinations(int k) {
        Sources.requireNonNegativeArgument("k", k);
        return copyOf(Sources.map(
                Sources.<E>combinations(toArray(), k), Seq::viewOf));
    }

    /**
     * Returns all combinations of all lengths from zero to {@link #size()}
     * in shortlex order of the original index of each element.
     */
    default Seq<Seq<E>> allCombinations() {
        return copyOf(Sources.map(
                Sources.<E>allCombinations(toArray()), Seq::viewOf));
    }

    /**
     * Returns the {@code k}-th Cartesian power,
     * in lexical order of the original index of each element,
     * ie all sequences of length {@code k} whose elements are drawn
     * from this {@code Seq} with repeated selection allowed.
     */
    default Seq<Seq<E>> power(int k) {
        Sources.requireNonNegativeArgument("k", k);
        return copyOf(Sources.map(
                Sources.<E>power(toArray(), k), Seq::viewOf));
    }

    /**
     * Returns the Cartesian product of this {@code Seq} and the given
     * {@code Iterable} after applying {@code mapper} to each pair
     * in lexical order of the original indexes.
     * The given {@code Iterable} is buffered and must be finite.
     */
    default <F, R> Seq<R> product(
            Iterable<? extends F> that,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        requireNonNull(that);
        requireNonNull(mapper);
        return copyOf(Sources.product(
                spliterator(), Sources.toArray(that), mapper));
    }

    /**
     * Eager equivalent of {@link Stream#flatMap(Function)}.
     */
    default <R> Seq<R> flatMap(
            Function<? super E, ? extends Iterable<? extends R>> mapper) {
        requireNonNull(mapper);
        return copyOf(Sources.flatMap(
                spliterator(), mapper, Sources::toSource));
    }

    /**
     * Like {@link #flatMap(Function)} except that results are passed to a
     * {@code Consumer} rather than returned.
     */
    default <R> Seq<R> mapMulti(
            BiConsumer<? super E, ? super Consumer<R>> mapper) {
        requireNonNull(mapper);
        return copyOf(Sources.mapMulti(spliterator(), mapper));
    }

    /**
     * Returns those consecutive elements whose indexes
     * are from the first argument (inclusive) to the second (exclusive).
     * Throws only if either argument is negative.
     * Returns the same elements as {@code limit(to).skip(from)} otherwise.
     * Is not a view.
     */
    default Seq<E> slice(int from, int to) {
        Sources.requireNonNegativeIndex("from", from);
        Sources.requireNonNegativeIndex("to", to);
        return copyOf(Sources.slice(spliterator(), from, to));
    }

    /**
     * Returns in ascending order those values of {@code from}
     * for which there exists some {@code to} such that
     * {@link #slice(int, int) slice(from, to)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals}.
     */
    default Seq<Integer> indexesOfSlice(Iterable<?> that) {
        requireNonNull(that);
        return copyOf(
                Sources.indexesOfSlice(spliterator(), that.spliterator()));
    }

    /**
     * Returns the smallest value of {@code from}
     * for which there exists some {@code to} such that
     * {@link #slice(int, int) slice(from, to)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals},
     * or {@code -1} if no such value exists.
     */
    default int indexOfSlice(Iterable<?> that) {
        requireNonNull(that);
        return Sources.indexOfSlice(spliterator(), that.spliterator());
    }

    /**
     * Returns the largest value of {@code from}
     * for which there exists some {@code to} such that
     * {@link #slice(int, int) slice(from, to)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals},
     * or {@code -1} if no such value exists.
     */
    default int lastIndexOfSlice(Iterable<?> that) {
        requireNonNull(that);
        return Sources.lastIndexOfSlice(spliterator(), that.spliterator());
    }

    /**
     * Returns {@code true} only if there exists some value of {@code from}
     * and {@code to} such that
     * {@link #slice(int, int) slice(from, to)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals}.
     */
    default boolean containsSlice(Iterable<?> that) {
        requireNonNull(that);
        return Sources.containsSlice(spliterator(), that.spliterator());
    }

    /**
     * Returns {@code true} only if there exists some value of {@code size}
     * such that {@link #limit(long) limit(size)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals}.
     */
    default boolean startsWith(Iterable<?> that) {
        requireNonNull(that);
        return Sources.startsWith(spliterator(), that.spliterator());
    }

    /**
     * Returns {@code true} only if there exists some value of {@code size}
     * such that {@link #limitLast(long) limitLast(size)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals}.
     */
    default boolean endsWith(Iterable<?> that) {
        requireNonNull(that);
        return Sources.endsWith(spliterator(), that.spliterator());
    }

    /**
     * Return the element at the given index if within bounds,
     * or throws {@code IndexOutOfBoundsException} otherwise.
     */
    default E get(int index) {
        Sources.requireNonNegativeIndex("index", index);
        return Sources.get(spliterator(), index);
    }

    /**
     * Returns the index of the first element equal to the given argument
     * or {@code -1} if no such element exists.
     */
    default int indexOf(Object object) {
        return Sources.indexOf(spliterator(), object);
    }

    /**
     * Returns the index of the last element equal to the given argument
     * or {@code -1} if no such element exists.
     */
    default int lastIndexOf(Object object) {
        return Sources.lastIndexOf(spliterator(), object);
    }

    /**
     * Returns the indexes of all elements equal to the given argument.
     */
    default Seq<Integer> indexesOf(Object object) {
        return copyOf(Sources.indexesOf(spliterator(), object));
    }

    /**
     * Returns the number of elements in this {@code Seq} equal to the
     * specified object.
     * See {@link Collections#frequency(Collection, Object)}.
     */
    default int frequency(Object object) {
        return Sources.frequency(spliterator(), object);
    }

    /**
     * Returns a new {@code Seq} with elements reversed.
     * See {@link Collections#reverse(List)}.
     *
     * <p>The result is a snapshot, unlike
     * {@code SequencedCollection.reversed()} (Java 21), which is a live view.
     */
    // No type can implement both Seq and a SequencedCollection:
    // the return types are unrelated. If Seq ever extends SequencedCollection,
    // Seq<E> reversed() is a legal covariant override, and snapshot versus view
    // becomes a decision then; ArraySeq could flip indexes over the array.
    default Seq<E> reversed() {
        return viewOf(Sources.reversed(spliterator()));
    }

    /**
     * Returns a new {@code Seq} with elements rotated according to
     * {@link Collections#rotate(List, int)}.
     */
    default Seq<E> rotated(int distance) {
        return viewOf(Sources.rotated(spliterator(), distance));
    }

    /**
     * Returns a new {@code Seq} with elements shuffled.
     * See {@link Collections#shuffle(List)}.
     */
    default Seq<E> shuffled(Random random) {
        requireNonNull(random);
        return viewOf(Sources.shuffled(spliterator(), random));
    }

    /**
     * Returns the last {@code size} elements.
     */
    default Seq<E> limitLast(long size) {
        Sources.requireNonNegativeArgument("size", size);
        return copyOf(Sources.limitLast(spliterator(), size));
    }

    /**
     * Returns all except the last {@code size} elements.
     */
    default Seq<E> skipLast(long size) {
        Sources.requireNonNegativeArgument("size", size);
        return copyOf(Sources.skipLast(spliterator(), size));
    }

    /**
     * Returns all elements from the start to the first element for which
     * the given {@code Predicate} returns false (exclusive).
     */
    default Seq<E> takeWhile(Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return copyOf(Sources.takeWhile(spliterator(), predicate));
    }

    /**
     * Returns all elements between the first element for which the given
     * {@code Predicate} returns false (inclusive) and the end.
     */
    default Seq<E> dropWhile(Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return copyOf(Sources.dropWhile(spliterator(), predicate));
    }

    /**
     * Eager equivalent of {@link Stream#filter(Predicate)}.
     */
    default Seq<E> filter(Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return copyOf(Sources.filter(spliterator(), predicate));
    }

    /**
     * Eager equivalent of {@link Stream#map(Function)}.
     */
    default <R> Seq<R> map(Function<? super E, ? extends R> mapper) {
        requireNonNull(mapper);
        return copyOf(Sources.map(spliterator(), mapper));
    }

    /**
     * Returns a {@code Seq} containing the results of applying the given
     * mapper to each element's index followed by the element itself.
     */
    default <R> Seq<R> mapIndexed(
            BiFunction<? super Integer, ? super E, ? extends R> mapper) {
        requireNonNull(mapper);
        return copyOf(Sources.mapIndexed(spliterator(), mapper));
    }

    /**
     * Equivalent of {@link Stream#distinct()}.
     */
    default Seq<E> distinct() {
        return copyOf(Sources.distinct(spliterator()));
    }

    /**
     * Equivalent of {@link #distinct()}
     * except elements are compared by mapped keys.
     */
    default Seq<E> distinctBy(Function<? super E, ?> keyMapper) {
        requireNonNull(keyMapper);
        return copyOf(Sources.distinctBy(spliterator(), keyMapper));
    }

    /**
     * Equivalent to {@link #groupBy(Function, Function) groupBy(keyMapper, g -> g)}.
     */
    default <K> Map<K, Seq<E>> groupBy(
            Function<? super E, ? extends K> keyMapper) {
        requireNonNull(keyMapper);
        return Sources.groupBy(spliterator(), keyMapper, Seq::viewOf);
    }

    /**
     * Groups elements by the mapped key, then maps each group.
     * The returned map and each group preserve encounter order.
     */
    default <K, V> Map<K, V> groupBy(
            Function<? super E, ? extends K> keyMapper,
            Function<? super Seq<E>, ? extends V> valueMapper) {
        requireNonNull(keyMapper);
        requireNonNull(valueMapper);
        return Sources.groupBy(spliterator(), keyMapper,
                valueMapper.compose(Seq::viewOf));
    }

    /**
     * Equivalent to
     * {@link #partitionBy(Predicate, Function) partitionBy(predicate, v -> v)}.
     */
    default Map<Boolean, Seq<E>> partitionBy(
            Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return Sources.partitionBy(spliterator(), predicate, Seq::viewOf);
    }

    /**
     * Partitions elements by the predicate, then maps each partition.
     * The returned map always contains {@code false} and {@code true}.
     */
    default <V> Map<Boolean, V> partitionBy(
            Predicate<? super E> predicate,
            Function<? super Seq<E>, ? extends V> valueMapper) {
        requireNonNull(predicate);
        requireNonNull(valueMapper);
        return Sources.partitionBy(spliterator(), predicate,
                valueMapper.compose(Seq::viewOf));
    }

    /**
     * Equivalent of {@link Stream#sorted()}.
     */
    default Seq<E> sorted() {
        return viewOf(Sources.sorted(spliterator()));
    }

    /**
     * Equivalent of {@link Stream#sorted(Comparator)}.
     */
    default Seq<E> sorted(Comparator<? super E> comparator) {
        requireNonNull(comparator);
        return viewOf(Sources.sorted(spliterator(), comparator));
    }

    /**
     * Returns whether elements are in nondecreasing natural order.
     * Empty and singleton sequences are sorted. Stops at the first
     * out-of-order adjacent pair.
     */
    default boolean isSorted() {
        return Sources.isSorted(spliterator());
    }

    /**
     * Returns whether elements are in nondecreasing order according to
     * {@code comparator}. Empty and singleton sequences are sorted.
     * Stops at the first adjacent pair for which the comparator returns
     * a positive value. Null elements are supported if the comparator
     * supports them.
     */
    default boolean isSorted(Comparator<? super E> comparator) {
        requireNonNull(comparator);
        return Sources.isSorted(spliterator(), comparator);
    }

    /**
     * Eager equivalent of {@link Stream#limit(long)}.
     */
    default Seq<E> limit(long size) {
        Sources.requireNonNegativeArgument("size", size);
        return copyOf(Sources.limit(spliterator(), size));
    }

    /**
     * Eager equivalent of {@link Stream#skip(long)}.
     */
    default Seq<E> skip(long size) {
        Sources.requireNonNegativeArgument("size", size);
        return copyOf(Sources.skip(spliterator(), size));
    }

    /**
     * {@inheritDoc}
     */
    default void forEach(Consumer<? super E> action) {
        requireNonNull(action);
        spliterator().forEachRemaining(action);
    }

    /**
     * Equivalent of {@link Stream#forEachOrdered(Consumer)}.
     */
    default void forEachOrdered(Consumer<? super E> action) {
        requireNonNull(action);
        spliterator().forEachRemaining(action);
    }

    /**
     * {@inheritDoc}
     */
    default Object[] toArray() {
        return Sources.toArray(spliterator());
    }

    /**
     * Equivalent of {@link Stream#toArray(IntFunction)}.
     */
    default <A> A[] toArray(IntFunction<A[]> generator) {
        requireNonNull(generator);
        return Sources.toArray(spliterator(), generator);
    }

    /**
     * Equivalent of {@link Stream#reduce(Object, BinaryOperator)}.
     */
    default E reduce(E identity, BinaryOperator<E> accumulator) {
        requireNonNull(accumulator);
        return Sources.reduce(spliterator(), identity, accumulator);
    }

    /**
     * Equivalent of {@link Stream#reduce(BinaryOperator)}.
     */
    default Optional<E> reduce(BinaryOperator<E> accumulator) {
        requireNonNull(accumulator);
        return Sources.reduce(spliterator(), accumulator);
    }

    /**
     * Equivalent of {@link Stream#reduce(Object, BiFunction, BinaryOperator)}
     * except the last argument is not required since the operation
     * is never parallel.
     */
    default <U> U reduce(
            U identity,
            BiFunction<U, ? super E, U> accumulator) {
        requireNonNull(accumulator);
        return Sources.reduce(spliterator(), identity, accumulator);
    }

    /**
     * Equivalent of {@link Stream#collect(Supplier, BiConsumer, BiConsumer)}
     * except the last argument is not required since the operation
     * is never parallel.
     */
    default <U> U collect(
            Supplier<U> supplier,
            BiConsumer<U, ? super E> accumulator) {
        requireNonNull(supplier);
        requireNonNull(accumulator);
        return Sources.collect(spliterator(), supplier, accumulator);
    }

    /**
     * Equivalent of {@link Stream#collect(Collector)}.
     */
    default <R, A> R collect(Collector<? super E, A, R> collector) {
        requireNonNull(collector);
        return Sources.collect(spliterator(), collector);
    }

    /**
     * Like {@link #collect(Supplier, BiConsumer)}, but stops when the
     * accumulator returns {@code false}. Returns the accumulated result,
     * including changes made by the stopping invocation.
     */
    default <U> U collectWhile(
            Supplier<U> supplier,
            BiPredicate<U, ? super E> accumulator) {
        requireNonNull(supplier);
        requireNonNull(accumulator);
        return Sources.collectWhile(spliterator(), supplier, accumulator);
    }

    /**
     * Returns the sum of the {@code int} values produced by applying the
     * given mapper to the elements in encounter order. Equivalent to
     * {@code stream().mapToInt(mapper).reduce(0, Integer::sum)}.
     */
    default int sumOfInt(ToIntFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Sources.sumOfInt(spliterator(), mapper);
    }

    /**
     * Returns the sum of the {@code long} values produced by applying the
     * given mapper to the elements in encounter order. Equivalent to
     * {@code stream().mapToLong(mapper).reduce(0L, Long::sum)}.
     */
    default long sumOfLong(ToLongFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Sources.sumOfLong(spliterator(), mapper);
    }

    /**
     * Returns the sum of the {@code double} values produced by applying the
     * given mapper to the elements in encounter order. Equivalent to
     * {@code stream().mapToDouble(mapper).reduce(0.0, Double::sum)}.
     */
    default double sumOfDouble(ToDoubleFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Sources.sumOfDouble(spliterator(), mapper);
    }

    /**
     * Returns the product of the {@code int} values produced by applying the
     * given mapper to the elements in encounter order. Equivalent to
     * {@code stream().mapToInt(mapper).reduce(1, (a, b) -> a * b)}.
     */
    default int productOfInt(ToIntFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Sources.productOfInt(spliterator(), mapper);
    }

    /**
     * Returns the product of the {@code long} values produced by applying the
     * given mapper to the elements in encounter order. Equivalent to
     * {@code stream().mapToLong(mapper).reduce(1L, (a, b) -> a * b)}.
     */
    default long productOfLong(ToLongFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Sources.productOfLong(spliterator(), mapper);
    }

    /**
     * Returns the product of the {@code double} values produced by applying
     * the given mapper to the elements in encounter order. Equivalent to
     * {@code stream().mapToDouble(mapper).reduce(1.0, (a, b) -> a * b)}.
     */
    default double productOfDouble(ToDoubleFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Sources.productOfDouble(spliterator(), mapper);
    }

    /**
     * Equivalent of {@link Stream#min(Comparator)} with natural ordering.
     */
    default Optional<E> min() {
        return Sources.min(spliterator());
    }

    /**
     * Equivalent of {@link Stream#min(Comparator)}.
     */
    default Optional<E> min(Comparator<? super E> comparator) {
        requireNonNull(comparator);
        return Sources.min(spliterator(), comparator);
    }

    /**
     * Equivalent of {@link Stream#max(Comparator)} with natural ordering.
     */
    default Optional<E> max() {
        return Sources.max(spliterator());
    }

    /**
     * Equivalent of {@link Stream#max(Comparator)}.
     */
    default Optional<E> max(Comparator<? super E> comparator) {
        requireNonNull(comparator);
        return Sources.max(spliterator(), comparator);
    }

    /**
     * Equivalent of {@link Stream#count()}.
     */
    default long count() {
        return Sources.count(spliterator());
    }

    /**
     * Equivalent of {@link Stream#anyMatch(Predicate)}.
     */
    default boolean anyMatch(Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return Sources.anyMatch(spliterator(), predicate);
    }

    /**
     * Equivalent of {@link Stream#allMatch(Predicate)}.
     */
    default boolean allMatch(Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return Sources.allMatch(spliterator(), predicate);
    }

    /**
     * Equivalent of {@link Stream#noneMatch(Predicate)}.
     */
    default boolean noneMatch(Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return Sources.noneMatch(spliterator(), predicate);
    }

    /**
     * Returns an {@code Optional} containing the first element of
     * this {@code Seq} if this {@code Seq} is not empty,
     * otherwise returns an empty {@code Optional}.
     * See also {@link #toOptional()}.
     */
    default Optional<E> findFirst() {
        return Sources.findFirst(spliterator());
    }

    /**
     * Returns an {@code Optional} containing the single element of
     * this {@code Seq} if this {@code Seq} contains exactly one element,
     * otherwise returns an empty {@code Optional}.
     * See also {@link #toOptional()}.
     */
    default Optional<E> findOnly() {
        return Sources.findOnly(spliterator());
    }

    /**
     * Returns an {@code Optional} containing the last element of
     * this {@code Seq} if this {@code Seq} is not empty,
     * otherwise returns an empty {@code Optional}.
     * See also {@link #toOptional()}.
     */
    default Optional<E> findLast() {
        return Sources.findLast(spliterator());
    }

    /**
     * Returns the first element of this {@code Seq} if this {@code Seq} is
     * not empty, otherwise throws {@code NoSuchElementException}.
     */
    default E getFirst() {
        return Sources.getFirst(spliterator());
    }

    /**
     * Returns the last element of this {@code Seq} if this {@code Seq} is
     * not empty, otherwise throws {@code NoSuchElementException}.
     */
    default E getLast() {
        return Sources.getLast(spliterator());
    }

    /**
     * Returns the single element of this {@code Seq} if this {@code Seq}
     * contains exactly one element,
     * otherwise throws {@code NoSuchElementException}.
     */
    default E getOnly() {
        return Sources.getOnly(spliterator());
    }

    /**
     * Returns fixed windows according to
     * {@code Gatherers.windowFixed(int)}.
     */
    default Seq<Seq<E>> windowFixed(int size) {
        Sources.requirePositiveArgument("size", size);
        return copyOf(Sources.map(
                Sources.windowFixed(spliterator(), size), Seq::viewOf));
    }

    /**
     * Returns sliding windows according to
     * {@code Gatherers.windowSliding(int)}.
     */
    default Seq<Seq<E>> windowSliding(int size) {
        Sources.requirePositiveArgument("size", size);
        return copyOf(Sources.map(
                Sources.windowSliding(spliterator(), size), Seq::viewOf));
    }

    /**
     * Returns successive accumulations according to
     * {@code Gatherers.scan(Supplier, BiFunction)}.
     */
    default <R> Seq<R> scan(
            Supplier<R> initial,
            BiFunction<? super R, ? super E, ? extends R> scanner) {
        requireNonNull(initial);
        requireNonNull(scanner);
        return copyOf(Sources.scan(spliterator(), initial, scanner));
    }

    /**
     * Eager equivalent of {@link Stream#peek(Consumer)}.
     */
    default Seq<E> peek(Consumer<? super E> action) {
        requireNonNull(action);
        spliterator().forEachRemaining(action);
        return this;
    }

    /**
     * Returns a {@code String} using
     * {@link Collectors#joining(CharSequence, CharSequence, CharSequence)}.
     */
    default String toString(
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        requireNonNull(delimiter);
        requireNonNull(prefix);
        requireNonNull(suffix);
        return Sources.toString(spliterator(), delimiter, prefix, suffix);
    }

    /**
     * {@inheritDoc}
     */
    default int size() {
        return Sources.size(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    default boolean isEmpty() {
        return Sources.isEmpty(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    default boolean contains(Object object) {
        return Sources.contains(spliterator(), object);
    }

    /**
     * {@inheritDoc}
     */
    default <T> T[] toArray(T[] ts) {
        return Sources.toArray(spliterator(), ts);
    }

    /**
     * Throws {@code UnsupportedOperationException}.
     */
    default boolean add(E element) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws {@code UnsupportedOperationException}.
     */
    default boolean remove(Object element) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code true} if this {@code Seq} contains all of the elements
     * in the specified collection.
     * See also {@link #containsMultiset(Iterable)}.
     */
    default boolean containsAll(Collection<?> that) {
        requireNonNull(that);
        return Sources.containsAll(spliterator(), Sources.toSource(that));
    }

    /**
     * Throws {@code UnsupportedOperationException}.
     */
    default boolean addAll(Collection<? extends E> that) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws {@code UnsupportedOperationException}.
     */
    default boolean removeAll(Collection<?> that) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws {@code UnsupportedOperationException}.
     */
    default boolean removeIf(Predicate<? super E> filter) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws {@code UnsupportedOperationException}.
     */
    default boolean retainAll(Collection<?> that) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws {@code UnsupportedOperationException}.
     */
    default void clear() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a {@code SeqStream} with this collection as its source.
     */
    default SeqStream<E> stream() {
        return SeqStream.viewOf(spliterator());
    }

    /**
     * Returns a {@code SeqStream} with this collection as its source, in
     * parallel mode. As with any {@code SeqStream}, it is still evaluated
     * sequentially; see {@link SeqStream#parallel}.
     */
    default SeqStream<E> parallelStream() {
        return stream().parallel();
    }

    /**
     * Returns an {@code Iterator} over the elements of this {@code Seq}.
     */
    default Iterator<E> iterator() {
        return Spliterators.iterator(spliterator());
    }

    /**
     * A builder for creating {@link Seq} instances.
     */
    interface Builder<E> extends Consumer<E> {

        /**
         * Adds the element.
         */
        void accept(E element);

        /**
         * Builds the {@link Seq} instance.
         * An {@link IllegalStateException} is thrown on further attempts to
         * operate on this builder.
         */
        Seq<E> build();

        /**
         * Adds the element and returns {@code this}.
         */
        default Builder<E> add(E element) {
            accept(element);
            return this;
        }
    }
}
