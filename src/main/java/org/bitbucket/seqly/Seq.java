package org.bitbucket.seqly;

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
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static org.bitbucket.seqly.Util.toSeqStream;

/**
 * <p>{@code Seq} extends {@code Collection} and defines eager versions
 * of almost all {@link java.util.stream.Stream Stream} methods like
 * {@code map}, {@code filter} and {@code reduce}. For example:
 *
 * <pre>{@code
 *     Seq<Integer> lengths = words.map(String::length);
 * }</pre>
 *
 * <p>Compare with the {@code Stream} version:
 *
 * <pre>{@code
 *     List<Integer> lengths = words.stream()
 *             .map(String::length)
 *             .collect(toList());
 * }</pre>
 *
 * <p>These are extremely common operations, and laziness is often not required.
 * {@code Stream}s are extremely verbose
 * for this case, hence {@code Seq}.
 *
 * <h2>Usage</h2>
 *
 * <p>{@code Seq} can be used as the default choice of {@code Collection}
 * most of the time unless specific features
 * (such as constant-time {@code contains}) are required.
 *
 * <pre>{@code
 *     Seq.of(0, 1, 2);
 *
 *     Seq.Builder<String> builder = Seq.builder();
 *     while (scanner.hasNext()) {
 *         builder.add(scanner.next());
 *     }
 *     builder.build();
 * }</pre>
 *
 * <p>Most {@code Seq} implementations
 * support constant-time indexing and are backed by an array or similar.
 * Only {@code Seq.view(Iterable)} in this library does not.
 *
 * <h2>Collections</h2>
 *
 * <p>Factory methods convert from existing types.
 *
 * <pre>{@code
 *     Seq.copy(new Integer[]{4, 5});
 *     Seq.view(Optional.of(6));
 *     Seq.view(List.of(7, 8));
 *     Seq.copy(List.of(9, 10).iterator());
 *     Seq.copy(Stream.of(11, 12, 13));
 * }</pre>
 *
 * <p>Other methods convert to existing types.
 *
 * <pre>{@code
 *     seq.asList();
 *     seq.asSet();
 *     seq.asMap(Entity::getId);
 *     seq.toArray();
 *     seq.toArray(new String[5]);
 *     seq.toArray(String[]::new);
 *     seq.findFirst();
 *     seq.findLast();
 *     seq.findOnly();
 *     seq.collect(toList());
 *     seq.collect(toSet());
 * }</pre>
 *
 * <h2>Streams</h2>
 *
 * <p>When laziness is desired, {@link Seq#stream()}
 * can be called as usual, and the resulting type {@link SeqStream}
 * retains the additional methods of {@code Seq}.
 *
 * <pre>{@code
 *     Seq<Integer> lengths = words.stream()
 *             .filter(w -> w.contains("e"))
 *             .map(String::length)
 *             .reversed()
 *             .collect();
 * }</pre>
 *
 * <p>All functional operations defined on {@code Seq} have the same
 * signature as the {@code Stream} version, so code using both types looks
 * natural.
 *
 * <h2>Equality</h2>
 *
 * <p>{@code Seq} defines {@code equals()}
 * such that two {@code Seq}s are equal only if they have the same elements in the same
 * order. This is like {@code List}, though a {@code Seq} is never equal
 * to a {@code List} and vice versa, as required by
 * {@code List.equals()}.
 * The methods {@code listEquals()}, {@code setEquals()} and {@code multisetEquals()}
 * may be used for other definitions of equality
 * and do not depend on the subtype of the given {@code Iterable}.
 * The methods {@code asList()},
 * {@code asSet()} and {@code asMap()} may be useful for equality comparisons
 * in other cases.
 *
 * <h2>Immutability</h2>
 *
 * <p>The {@code Seq} interface itself does not have default implementations
 * of any mutating methods.
 * All inherited mutating methods from {@code Collection} throw
 * {@code UnsupportedOperationException}.
 * But there is no restriction on what additional methods {@code Seq}
 * implementations may contain.
 * Note that factory methods can create views of mutable collections, in which
 * case mutations to the underlying collection are reflected in {@code Seq}.
 *
 * <h2>Implementation</h2>
 *
 * <p>Most of {@code Seq} simply delegates to existing classes.
 * {@code Seq} is an interface whose only abstract method is
 * {@code spliterator()}, ie all other methods are have default implementations
 * defined in terms of {@code spliterator()}. For example, the most common
 * implementation of {@code Seq} is {@code ArraySeq}, which wraps an
 * array and uses {@code Spliterators.spliterator()} to
 * implement {@code spliterator()}. Methods like {@code map} and {@code filter}
 * ultimately delegate to a {@code Stream} created by
 * {@code StreamSupport} using this {@code Spliterator}.
 * Other methods have original implementations.
 *
 * <h2>Examples</h2>
 *
 * <pre>{@code
 *     seq.filter(Objects::isNull);
 *     seq.flatMap(s -> s);
 *     seq.reduce(0, (len, str) -> len + str.length());
 *     seq.intersection(otherSeq);
 *     seq.shuffled(new Random());
 *     seq.zip(seq.indexes(), (elem, idx) -> idx + ": " + elem);
 *     seq.get(2);
 *     seq.indexesOf(element);
 *     seq.limitLast(3);
 *     seq.toString("; ", "<", ">");
 * }</pre>
 */
public interface Seq<E> extends Collection<E> {

    /**
     * Returns a {@code Seq} containing the given elements.
     * Does not copy the argument, so subsequent mutations to the argument
     * are reflected in the returned {@code Seq}.
     */
    @SafeVarargs
    static <E> Seq<E> of(E... elements) {
        return new ArraySeq<>(Objects.requireNonNull(elements));
    }

    /**
     * Returns a {@code Seq} containing the given elements.
     */
    static <E> Seq<E> copy(E[] array) {
        return new ArraySeq<>(Arrays.copyOf(array, array.length));
    }

    /**
     * Returns a {@code Seq} containing the given elements in constant time.
     * Does not copy the argument, so subsequent mutations to the argument
     * are reflected in the returned {@code Seq}.
     */
    static <E> Seq<E> view(E[] array) {
        return new ArraySeq<>(Objects.requireNonNull(array));
    }

    /**
     * Returns a {@code Seq} containing the given elements.
     */
    static <E> Seq<E> copy(Iterable<? extends E> iterable) {
        return new ArraySeq<>(Util.toArray(iterable));
    }

    /**
     * Returns a {@code Seq} containing the given elements in constant time.
     * Does not copy the argument, so subsequent mutations to the argument
     * are reflected in the returned {@code Seq}.
     * Unlike most {@code Seq} instances, the returned instance will not
     * support constant-time {@link #size()} or {@link #get(int)}.
     */
    static <E> Seq<E> view(Iterable<? extends E> iterable) {
        return new IterableSeq<>(Objects.requireNonNull(iterable));
    }

    /**
     * Returns a {@code Seq} containing the value of the optional
     * if it is present, otherwise returns an empty {@code Seq}.
     */
    static <E> Seq<E> view(Optional<? extends E> optional) {
        return optional.isPresent() ? of(optional.get()) : of();
    }

    /**
     * Returns a {@code Seq} containing the given elements.
     */
    static <E> Seq<E> copy(Iterator<? extends E> iterator) {
        return new ArraySeq<>(Util.toArray(iterator));
    }

    /**
     * Returns a {@code Seq} containing the given elements.
     */
    static <E> Seq<E> copy(Spliterator<? extends E> spliterator) {
        return new ArraySeq<>(Util.toArray(spliterator));
    }

    /**
     * Returns a {@code Seq} containing the given elements.
     */
    static <E> Seq<E> copy(Stream<? extends E> stream) {
        return new ArraySeq<>(stream.toArray());
    }

    /**
     * Returns a {@link Builder}.
     */
    static <E> Builder<E> builder() {
        return new DefaultBuilder<>();
    }

    /**
     * Returns a {@code Collector} that accumulates input elements
     * into a {@code Seq}.
     */
    static <E> Collector<E, ?, Seq<E>> toSeq() {
        return Collector.<E, Builder<E>, Seq<E>>of(
                Seq::builder, Builder::add,
                (b, c) -> b.addAll(c.build()), Builder::build);
    }

    /**
     * Returns a {@code Seq} containing consecutive integers
     * from the first argument (inclusive) to the second argument (exclusive)
     * if ascending, otherwise returns an empty {@code Seq}.
     */
    static Seq<Integer> range(int begin, int end) {
        return SeqStream.range(begin, end).collect();
    }

    /**
     * Returns a {@code Seq} containing the result of concatenating each of
     * the given {@code Iterable} arguments in order.
     * Equivalent to {@link #sum(Iterable)} but as a static varargs method.
     */
    @SafeVarargs
    static <E> Seq<E> concat(
            Iterable<? extends E>... iterables) {
        return flatten(view(iterables));
    }

    /**
     * Returns a {@code Seq} containing the result of concatenating each
     * {@code Iterable} element in the given {@code Iterable} in order.
     */
    static <E> Seq<E> flatten(
            Iterable<? extends Iterable<? extends E>> iterables) {
        return SeqStream.<E>flatten(
                toSeqStream(iterables).map(Util::toSeqStream))
                .collect();
    }

    /**
     * Returns a {@code Spliterator} over the elements of this {@code Seq}.
     */
    Spliterator<E> spliterator();

    /**
     * See {@link #equals(Object)}.
     */
    int hashCode();

    /**
     * Returns {@code true} only if the argument is a {@code Seq}
     * that contains the same elements in the same order
     * according to the {@code equals} method of individual elements.
     */
    boolean equals(Object o);

    /**
     * Equivalent to {@code asList().toString()}.
     */
    String toString();

    /**
     * Returns a view of this {@code Seq} as a {@link List} in constant time.
     */
    default List<E> asList() {
        return new SeqList<>(this);
    }

    /**
     * Returns a view of this {@code Seq} as a {@link Set} in constant time.
     * Does not check whether duplicate elements will exist
     * and therefore may return a {@code Set} that violates its contract.
     */
    default Set<E> asSet() {
        return new SeqSet<>(this);
    }

    /**
     * Equivalent to {@link #asMap(Function, Function) asMap(e -> e, e -> e)}.
     */
    default Map<E, E> asMap() {
        return asMap(identity(), identity());
    }

    /**
     * Equivalent to {@link #asMap(Function, Function) asMap(keyMapper, e -> e)}.
     */
    default <K> Map<K, E> asMap(
            Function<? super E, ? extends K> keyMapper) {
        return asMap(keyMapper, identity());
    }

    /**
     * Returns a view of this {@code Seq} as a {@link Map} in constant time.
     * Does not check whether duplicate keys will exist
     * and therefore may return a {@code Map} that violates its contract.
     */
    @SuppressWarnings("unchecked")
    default <K, V> Map<K, V> asMap(
            Function<? super E, ? extends K> keyMapper,
            Function<? super E, ? extends V> valueMapper) {
        return new SeqMap<>(this,
                (Function<Object, ? extends K>) keyMapper,
                (Function<Object, ? extends V>) valueMapper);
    }

    /**
     * Returns {@code true} only if the given {@code Iterable}
     * contains the same elements in the same order
     * according to the {@code equals} method of individual elements.
     * The type of the given {@code Iterable} is irrelevant.
     */
    default boolean listEquals(Iterable<?> that) {
        return stream().listEquals(toSeqStream(that));
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
        return stream().setEquals(toSeqStream(that));
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
        return stream().multisetEquals(toSeqStream(that));
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
        return stream().<F, R>zip(toSeqStream(that), mapper).collect();
    }

    /**
     * Returns consecutive integers
     * from zero (inclusive) to the size of this {@code Seq} (exclusive).
     */
    default Seq<Integer> indexes() {
        return stream().indexes().collect();
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
     * Implementation converts the argument to a {@code HashMap} of multiplicities.
     * See also {@link #union(Iterable)}, {@link #sum(Iterable)}.
     */
    default Seq<E> intersection(Iterable<?> that) {
        return stream().intersection(toSeqStream(that)).collect();
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
     * Implementation converts the argument to a {@code HashMap} of multiplicities.
     * See also {@link #union(Iterable)}, {@link #sum(Iterable)}.
     */
    default Seq<E> difference(Iterable<?> that) {
        return stream().difference(toSeqStream(that)).collect();
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
     * Implementation converts {@code this} to a {@code HashMap} of multiplicities.
     * See also {@link #intersection(Iterable)}, {@link #difference(Iterable)},
     * {@link #sum(Iterable)}.
     */
    default Seq<E> union(Iterable<? extends E> that) {
        return stream().union(toSeqStream(that)).collect();
    }

    /**
     * Returns the elements of this {@code Seq} followed by
     * the elements of the given {@code Iterable}.
     * Equivalent to {@link #concat(Iterable...)} but as an instance method.
     * See also {@link #intersection(Iterable)}, {@link #difference(Iterable)},
     * {@link #union(Iterable)}.
     */
    default Seq<E> sum(Iterable<? extends E> that) {
        return stream().sum(toSeqStream(that)).collect();
    }

    /**
     * Equivalent to {@link #difference(Iterable) that.difference(this).isEmpty()}.
     * See also {@link #intersection(Iterable)},
     * {@link #union(Iterable)}, {@link #sum(Iterable)}.
     */
    default boolean containsMultiset(Iterable<?> that) {
        return stream().containsMultiset(toSeqStream(that));
    }

    /**
     * Returns all permutations
     * in lexical order of the original index of each element.
     */
    // wildcard gives subtypes more flexibility and makes zero restrictions on callers
    // but given how rare subtype overrides would be, maybe the tiny benefit of not
    // needing the wildcard in client code that assigns the result to a variable would be better?
    default Seq<? extends Seq<E>> permutations() {
        return stream().permutations().map(SeqStream::collect).collect();
    }

    /**
     * Returns combinations of the given {@code size}
     * in lexical order of the original index of each element.
     */
    default Seq<? extends Seq<E>> combinations(int size) {
        return stream().combinations(size).map(SeqStream::collect).collect();
    }

    /**
     * Returns all subsets
     * in shortlex order of the original index of each element.
     * That is, the subsets are ordered by length then lexically.
     */
    default Seq<? extends Seq<E>> powerSet() {
        return stream().powerSet().map(SeqStream::collect).collect();
    }

    /**
     * Eager equivalent of {@link Stream#flatMap(Function)}.
     */
    default <R> Seq<R> flatMap(
            Function<? super E, ? extends Iterable<? extends R>> mapper) {
        return stream().<R>flatMap(mapper.andThen(Util::toSeqStream)).collect();
    }

    /**
     * Returns those consecutive elements whose indexes
     * are from the first argument (inclusive) to the second (exclusive).
     * Throws only if either argument is negative. Is not a view.
     */
    default Seq<E> slice(int from, int to) {
        return stream().slice(from, to).collect();
    }

    /**
     * Returns in ascending order those values of {@code from}
     * for which there exists some {@code to} such that
     * {@link #slice(int, int) slice(from, to)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals}.
     */
    default Seq<Integer> indexesOfSlice(Iterable<?> that) {
        return stream().indexesOfSlice(toSeqStream(that)).collect();
    }

    /**
     * Returns the smallest value of {@code from}
     * for which there exists some {@code to} such that
     * {@link #slice(int, int) slice(from, to)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals},
     * or {@code -1} if no such value exists.
     */
    default int indexOfSlice(Iterable<?> that) {
        return stream().indexOfSlice(toSeqStream(that));
    }

    /**
     * Returns the largest value of {@code from}
     * for which there exists some {@code to} such that
     * {@link #slice(int, int) slice(from, to)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals},
     * or {@code -1} if no such value exists.
     */
    default int lastIndexOfSlice(Iterable<?> that) {
        return stream().lastIndexOfSlice(toSeqStream(that));
    }

    /**
     * Returns {@code true} only if there exists some value of {@code from}
     * and {@code to} such that
     * {@link #slice(int, int) slice(from, to)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals}.
     */
    default boolean containsSlice(Iterable<?> that) {
        return stream().containsSlice(toSeqStream(that));
    }

    /**
     * Returns {@code true} only if there exists some value of {@code size} such that
     * {@link #limit(long) limit(size)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals}.
     */
    default boolean startsWith(Iterable<?> that) {
        return stream().startsWith(toSeqStream(that));
    }

    /**
     * Returns {@code true} only if there exists some value of {@code size} such that
     * {@link #limitLast(long) limitLast(size)} is equal to the given
     * {@code Iterable} according to {@link #listEquals(Iterable) listEquals}.
     */
    default boolean endsWith(Iterable<?> that) {
        return stream().endsWith(toSeqStream(that));
    }

    /**
     * Return the element at the given index, or throws
     * {@code IndexOutOfBoundsException}
     * if the index is not between zero and the size of this {@code Seq}.
     */
    default E get(int index) {
        return stream().get(index);
    }

    /**
     * Returns the index of the first element equal to the given argument
     * or {@code -1} if no such element exists.
     */
    default int indexOf(Object object) {
        return stream().indexOf(object);
    }

    /**
     * Returns the index of the last element equal to the given argument
     * or {@code -1} if no such element exists.
     */
    default int lastIndexOf(Object object) {
        return stream().lastIndexOf(object);
    }

    /**
     * Returns the indexes of all elements equal to the given argument.
     */
    default Seq<Integer> indexesOf(Object object) {
        return stream().indexesOf(object).collect();
    }

    /**
     * Returns a new {@code Seq} with elements reversed.
     * See {@link Collections#reverse(List)}.
     */
    default Seq<E> reversed() {
        return stream().reversed().collect();
    }

    /**
     * Returns a new {@code Seq} with elements rotated according to
     * {@link Collections#rotate(List, int)}.
     */
    default Seq<E> rotated(int size) {
        return stream().rotated(size).collect();
    }

    /**
     * Returns a new {@code Seq} with elements shuffled.
     * See {@link Collections#shuffle(List)}.
     */
    default Seq<E> shuffled(Random random) {
        return stream().shuffled(random).collect();
    }

    /**
     * Returns the last {@code size} elements.
     */
    default Seq<E> limitLast(long size) {
        return stream().limitLast(size).collect();
    }

    /**
     * Returns all except the last {@code size} elements.
     */
    default Seq<E> skipLast(long size) {
        return stream().skipLast(size).collect();
    }

    /**
     * Returns all elements from the start to the first element for which
     * the given {@code Predicate} returns false (exclusive).
     */
    default Seq<E> takeWhile(Predicate<? super E> predicate) {
        return stream().takeWhile(predicate).collect();
    }

    /**
     * Returns all elements between the first element for which the given
     * {@code Predicate} returns false (inclusive) and the end.
     */
    default Seq<E> dropWhile(Predicate<? super E> predicate) {
        return stream().dropWhile(predicate).collect();
    }

    /**
     * Eager equivalent of {@link Stream#filter(Predicate)}.
     */
    default Seq<E> filter(Predicate<? super E> predicate) {
        return stream().filter(predicate).collect();
    }

    /**
     * Eager equivalent of {@link Stream#map(Function)}.
     */
    default <R> Seq<R> map(Function<? super E, ? extends R> mapper) {
        return stream().<R>map(mapper).collect();
    }

    /**
     * Equivalent of {@link Stream#distinct()}.
     */
    default Seq<E> distinct() {
        return stream().distinct().collect();
    }

    /**
     * Equivalent of {@link Stream#sorted()}.
     */
    default Seq<E> sorted() {
        return stream().sorted().collect();
    }

    /**
     * Equivalent of {@link Stream#sorted(Comparator)}.
     */
    default Seq<E> sorted(Comparator<? super E> comparator) {
        return stream().sorted(comparator).collect();
    }

    /**
     * Eager equivalent of {@link Stream#limit(long)}.
     */
    default Seq<E> limit(long size) {
        return stream().limit(size).collect();
    }

    /**
     * Eager equivalent of {@link Stream#skip(long)}.
     */
    default Seq<E> skip(long size) {
        return stream().skip(size).collect();
    }

    /**
     * {@inheritDoc}
     */
    default void forEach(Consumer<? super E> action) {
        stream().forEach(action);
    }

    /**
     * Equivalent of {@link Stream#forEachOrdered(Consumer)}.
     */
    default void forEachOrdered(Consumer<? super E> action) {
        stream().forEachOrdered(action);
    }

    /**
     * {@inheritDoc}
     */
    default Object[] toArray() {
        return stream().toArray();
    }

    /**
     * Equivalent of {@link Stream#toArray(IntFunction)}.
     */
    default <A> A[] toArray(IntFunction<A[]> generator) {
        return stream().toArray(generator);
    }

    /**
     * Equivalent of {@link Stream#reduce(Object, BinaryOperator)}.
     */
    default E reduce(E identity, BinaryOperator<E> accumulator) {
        return stream().reduce(identity, accumulator);
    }

    /**
     * Equivalent of {@link Stream#reduce(BinaryOperator)}.
     */
    default Optional<E> reduce(BinaryOperator<E> accumulator) {
        return stream().reduce(accumulator);
    }

    /**
     * Equivalent of {@link Stream#reduce(Object, BiFunction, BinaryOperator)}
     * except the last argument is not required since the operation
     * is never parallel.
     */
    default <U> U reduce(
            U identity,
            BiFunction<U, ? super E, U> accumulator) {
        return stream().reduce(identity, accumulator);
    }

    /**
     * Returns a copy of this {@code Seq}.
     */
    default Seq<E> collect() {
        return stream().collect();
    }

    /**
     * Equivalent of {@link Stream#collect(Supplier, BiConsumer, BiConsumer)}
     * except the last argument is not required since the operation
     * is never parallel.
     */
    default <U> U collect(
            Supplier<U> supplier,
            BiConsumer<U, ? super E> accumulator) {
        return stream().collect(supplier, accumulator);
    }

    /**
     * Equivalent of {@link Stream#collect(Collector)}.
     */
    default <R, A> R collect(Collector<? super E, A, R> collector) {
        return stream().collect(collector);
    }

    /**
     * Equivalent of {@link Stream#min(Comparator)}.
     */
    default Optional<E> min(Comparator<? super E> comparator) {
        return stream().min(comparator);
    }

    /**
     * Equivalent of {@link Stream#max(Comparator)}.
     */
    default Optional<E> max(Comparator<? super E> comparator) {
        return stream().max(comparator);
    }

    /**
     * Equivalent of {@link Stream#count()}.
     */
    default long count() {
        return stream().count();
    }

    /**
     * Equivalent of {@link Stream#anyMatch(Predicate)}.
     */
    default boolean anyMatch(Predicate<? super E> predicate) {
        return stream().anyMatch(predicate);
    }

    /**
     * Equivalent of {@link Stream#allMatch(Predicate)}.
     */
    default boolean allMatch(Predicate<? super E> predicate) {
        return stream().allMatch(predicate);
    }

    /**
     * Equivalent of {@link Stream#noneMatch(Predicate)}.
     */
    default boolean noneMatch(Predicate<? super E> predicate) {
        return stream().noneMatch(predicate);
    }

    /**
     * Returns an {@code Optional} containing the first element of
     * this {@code Seq} if this {@code Seq} is not empty,
     * otherwise returns an empty {@code Optional}.
     */
    default Optional<E> findFirst() {
        return stream().findFirst();
    }

    /**
     * Returns an {@code Optional} containing any element of
     * this {@code Seq} if this {@code Seq} is not empty,
     * otherwise returns an empty {@code Optional}.
     */
    default Optional<E> findAny() {
        return stream().findAny();
    }

    /**
     * Returns an {@code Optional} containing the only element of
     * this {@code Seq} if this {@code Seq} contains exactly one element,
     * otherwise returns an empty {@code Optional}.
     */
    default Optional<E> findOnly() {
        return stream().findOnly();
    }

    /**
     * Returns an {@code Optional} containing the last element of
     * this {@code Seq} if this {@code Seq} is not empty,
     * otherwise returns an empty {@code Optional}.
     */
    default Optional<E> findLast() {
        return stream().findLast();
    }

    /**
     * Eager equivalent of {@link Stream#peek(Consumer)}.
     */
    default Seq<E> peek(Consumer<? super E> action) {
        forEach(action);
        return this;
    }

    /**
     * Returns a {@code String} using
     * {@link java.util.stream.Collectors#joining(CharSequence, CharSequence, CharSequence)}.
     */
    default String toString(
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        return stream().toString(delimiter, prefix, suffix);
    }

    /**
     * {@inheritDoc}
     */
    default int size() {
        return stream().size();
    }

    /**
     * {@inheritDoc}
     */
    default boolean isEmpty() {
        return stream().isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    default boolean contains(Object object) {
        return stream().contains(object);
    }

    /**
     * {@inheritDoc}
     */
    default <T> T[] toArray(T[] ts) {
        return stream().toArray(ts);
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
        return stream().containsAll(that.stream());
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
        return SeqStream.view(spliterator());
    }

    /**
     * Returns a {@code SeqStream} with this collection as its source.
     * The returned {@code Stream} is <em>not</em> parallel.
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
    interface Builder<E> {

        /**
         * Adds the element and returns {@code this}.
         */
        Builder<E> add(E element);

        /**
         * Builds the {@link Seq} instance.
         * May be called multiple times and interleaved with {@link #add}.
         */
        Seq<E> build();

        /**
         * Adds all elements and returns {@code this}.
         */
        default Builder<E> addAll(Iterable<E> iterable) {
            iterable.forEach(this::add);
            return this;
        }
    }
}
