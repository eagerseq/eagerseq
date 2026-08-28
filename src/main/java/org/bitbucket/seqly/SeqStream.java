package org.bitbucket.seqly;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.bitbucket.seqly.Split.toStream;

/**
 * <p>The subtype of {@code Stream} returned by
 * {@link Seq#stream()}. In addition to {@code Stream} methods like
 * {@code map}, {@code filter} and {@code reduce}, {@code SeqStream} defines
 * lazy versions of most other {@link Seq} methods like
 * {@code slice}, {@code intersection} and {@code zip}. Intermediate operations
 * return {@code SeqStream} so they can be chained and a no-args
 * {@code toSeq()} method converts back to {@code Seq}.
 */
public interface SeqStream<E> extends Stream<E> {

    /**
     * Returns an empty {@code SeqStream}.
     */
    static <E> SeqStream<E> of() {
        return new SpliteratorSeqStream<>(
                Split.toSpliterator(SeqBuilder.EMPTY));
    }

    /**
     * Returns a {@code SeqStream} containing the given element.
     */
    static <E> SeqStream<E> of(E element) {
        return new SpliteratorSeqStream<>(
                Split.toSpliterator(new Object[]{element}));
    }

    /**
     * Returns a {@code SeqStream} containing the given elements.
     */
    @SafeVarargs
    static <E> SeqStream<E> of(E... elements) {
        return new SpliteratorSeqStream<>(
                Split.toSpliterator(Arrays.copyOf(elements, elements.length)));
    }

    /**
     * Returns a {@code SeqStream} containing the given element if not null
     * or no elements otherwise.
     */
    static <E> SeqStream<E> ofNullable(E element) {
        return element == null ? of() : of(element);
    }

    /**
     * Returns a {@code SeqStream} containing the given elements.
     */
    static <E> SeqStream<E> view(Iterator<? extends E> iterator) {
        return new SpliteratorSeqStream<>(Split.toSpliterator(iterator));
    }

    /**
     * Returns a {@code SeqStream} containing the given elements.
     */
    static <E> SeqStream<E> view(Spliterator<? extends E> spliterator) {
        return new SpliteratorSeqStream<>(spliterator);
    }

    /**
     * Returns a {@code SeqStream} containing the given elements.
     * Note, {@code SeqStream} does not support {@link #close} and will not call
     * any close handlers of the given {@code Stream}.
     */
    static <E> SeqStream<E> view(Stream<? extends E> stream) {
        return new SpliteratorSeqStream<>(stream.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#range(int, int)}.
     */
    static SeqStream<Integer> range(int from, int to) {
        return view(Split.range(from, to));
    }

    /**
     * Stream equivalent of {@link Seq#concat(Iterable...)}.
     */
    @SafeVarargs
    static <E> SeqStream<E> concat(
            Stream<? extends E>... streams) {
        return flatten(of(streams));
    }

    /**
     * Stream equivalent of {@link Seq#flatten(Iterable)}.
     */
    static <E> SeqStream<E> flatten(
            Stream<? extends Stream<? extends E>> streams) {
        return view(Split.flatten(
                Split.map(streams.spliterator(), Stream::spliterator)));
    }

    /**
     * {@inheritDoc}
     */
    Spliterator<E> spliterator();

    /**
     * Stream equivalent of {@link Seq#listEquals(Iterable)}.
     */
    default boolean listEquals(Stream<?> that) {
        return Split.listEquals(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#setEquals(Iterable)}.
     */
    default boolean setEquals(Stream<?> that) {
        return Split.setEquals(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#multisetEquals(Iterable)}.
     */
    default boolean multisetEquals(Stream<?> that) {
        return Split.multisetEquals(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#zip(Iterable, BiFunction)}.
     */
    default <F, R> SeqStream<R> zip(
            Stream<? extends F> that,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        return view(Split.zip(spliterator(), that.spliterator(), mapper));
    }

    /**
     * Stream equivalent of {@link Seq#indexes()}.
     */
    default SeqStream<Integer> indexes() {
        return view(Split.indexes(spliterator()));
    }

    /**
     * Stream equivalent of {@link Seq#intersection(Iterable)}.
     */
    default SeqStream<E> intersection(Stream<?> that) {
        return view(Split.intersection(spliterator(), that.spliterator()));
    }

    /**
     * Stream equivalent of {@link Seq#difference(Iterable)}.
     */
    default SeqStream<E> difference(Stream<?> that) {
        return view(Split.difference(spliterator(), that.spliterator()));
    }

    /**
     * Stream equivalent of {@link Seq#union(Iterable)}.
     */
    default SeqStream<E> union(Stream<? extends E> that) {
        return view(Split.union(spliterator(), that.spliterator()));
    }

    /**
     * Stream equivalent of {@link Seq#sum(Iterable)}.
     */
    default SeqStream<E> sum(Stream<? extends E> that) {
        return flatten(of(this, that));
    }

    /**
     * Stream equivalent of {@link Seq#containsMultiset(Iterable)}.
     */
    default boolean containsMultiset(Stream<?> that) {
        return Split.containsMultiset(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#permutations()}.
     */
    default SeqStream<? extends SeqStream<E>> permutations() {
        return view(Split.map(
                Split.permutations(toArray()), Split::toSeqStream));
    }

    /**
     * Stream equivalent of {@link Seq#combinations(int)}.
     */
    default SeqStream<? extends SeqStream<E>> combinations(int size) {
        return view(Split.map(Split.combinations(toArray(), size),
                Split::toSeqStream));
    }

    /**
     * Stream equivalent of {@link Seq#powerSet()}.
     */
    default SeqStream<? extends SeqStream<E>> powerSet() {
        return view(Split.map(Split.powerSet(toArray()), Split::toSeqStream));
    }

    /**
     * Stream equivalent of {@link Seq#slice(int, int)}.
     */
    default SeqStream<E> slice(int from, int to) {
        return view(Split.slice(spliterator(), from, to));
    }

    /**
     * Stream equivalent of {@link Seq#indexesOfSlice(Iterable)}.
     */
    default SeqStream<Integer> indexesOfSlice(Stream<?> that) {
        return view(Split.indexesOfSlice(spliterator(), that.spliterator()));
    }

    /**
     * Stream equivalent of {@link Seq#indexesOfSlice(Iterable)}.
     */
    default int indexOfSlice(Stream<?> that) {
        return Split.indexOfSlice(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#lastIndexOfSlice(Iterable)}.
     */
    default int lastIndexOfSlice(Stream<?> that) {
        return Split.lastIndexOfSlice(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#containsSlice(Iterable)}.
     */
    default boolean containsSlice(Stream<?> that) {
        return Split.containsSlice(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#startsWith(Iterable)}.
     */
    default boolean startsWith(Stream<?> that) {
        return Split.startsWith(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#endsWith(Iterable)}.
     */
    default boolean endsWith(Stream<?> that) {
        return Split.endsWith(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#get(int)}.
     */
    default E get(int index) {
        return Split.get(spliterator(), index);
    }

    /**
     * Stream equivalent of {@link Seq#indexOf(Object)}.
     */
    default int indexOf(Object object) {
        return Split.indexOf(spliterator(), object);
    }

    /**
     * Stream equivalent of {@link Seq#lastIndexOf(Object)}.
     */
    default int lastIndexOf(Object object) {
        return Split.lastIndexOf(spliterator(), object);
    }

    /**
     * Stream equivalent of {@link Seq#indexesOf(Object)}.
     */
    default SeqStream<Integer> indexesOf(Object object) {
        return view(Split.indexesOf(spliterator(), object));
    }

    /**
     * Stream equivalent of {@link Seq#reversed()}.
     */
    default SeqStream<E> reversed() {
        return Split.toSeqStream(Split.reversed(spliterator()));
    }

    /**
     * Stream equivalent of {@link Seq#rotated(int)}.
     */
    default SeqStream<E> rotated(int size) {
        return Split.toSeqStream(Split.rotated(spliterator(), size));
    }

    /**
     * Stream equivalent of {@link Seq#shuffled(Random)}.
     */
    default SeqStream<E> shuffled(Random random) {
        return Split.toSeqStream(Split.shuffled(spliterator(), random));
    }

    /**
     * Stream equivalent of {@link Seq#size()}.
     */
    default int size() {
        return Split.size(spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#isEmpty()}.
     */
    default boolean isEmpty() {
        return Split.isEmpty(spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#contains(Object)}.
     */
    default boolean contains(Object object) {
        return Split.contains(spliterator(), object);
    }

    /**
     * Stream equivalent of {@link Seq#toArray(Object[])}.
     */
    default <T> T[] toArray(T[] ts) {
        return Split.toArray(spliterator(), ts);
    }

    /**
     * Stream equivalent of {@link Seq#containsAll(Collection)}.
     */
    default boolean containsAll(Stream<?> that) {
        return Split.containsAll(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#limitLast(long)}.
     */
    default SeqStream<E> limitLast(long size) {
        return view(Split.limitLast(spliterator(), size));
    }

    /**
     * Stream equivalent of {@link Seq#skipLast(long)}.
     */
    default SeqStream<E> skipLast(long size) {
        return view(Split.skipLast(spliterator(), size));
    }

    /**
     * Stream equivalent of {@link Seq#takeWhile(Predicate)}.
     */
    default SeqStream<E> takeWhile(Predicate<? super E> predicate) {
        return view(Split.takeWhile(spliterator(), predicate));
    }

    /**
     * Stream equivalent of {@link Seq#dropWhile(Predicate)}.
     */
    default SeqStream<E> dropWhile(Predicate<? super E> predicate) {
        return view(Split.dropWhile(spliterator(), predicate));
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> filter(Predicate<? super E> predicate) {
        return view(Split.filter(spliterator(), predicate));
    }

    /**
     * {@inheritDoc}
     */
    default <R> SeqStream<R> map(
            Function<? super E, ? extends R> mapper) {
        return view(Split.map(spliterator(), mapper));
    }

    /**
     * {@inheritDoc}
     */
    default IntStream mapToInt(ToIntFunction<? super E> mapper) {
        return toStream(this).mapToInt(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default LongStream mapToLong(ToLongFunction<? super E> mapper) {
        return toStream(this).mapToLong(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default DoubleStream mapToDouble(
            ToDoubleFunction<? super E> mapper) {
        return toStream(this).mapToDouble(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default <R> SeqStream<R> flatMap(
            Function<? super E, ? extends Stream<? extends R>> mapper) {
        return view(Split.flatMap(spliterator(), mapper.andThen(stream ->
                stream == null ? null : stream.spliterator())));
    }

    /**
     * Stream equivalent of {@link Seq#mapMulti(BiConsumer)}.
     */
    default <R> SeqStream<R> mapMulti(
            BiConsumer<? super E, ? super Consumer<R>> mapper) {
        return view(Split.mapMulti(spliterator(), requireNonNull(mapper)));
    }

    /**
     * {@inheritDoc}
     */
    default IntStream flatMapToInt(
            Function<? super E, ? extends IntStream> mapper) {
        return toStream(this).flatMapToInt(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default LongStream flatMapToLong(
            Function<? super E, ? extends LongStream> mapper) {
        return toStream(this).flatMapToLong(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default DoubleStream flatMapToDouble(
            Function<? super E, ? extends DoubleStream> mapper) {
        return toStream(this).flatMapToDouble(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> distinct() {
        return view(Split.distinct(spliterator()));
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> sorted() {
        return Split.toSeqStream(Split.sorted(spliterator(), null));
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> sorted(Comparator<? super E> comparator) {
        return Split.toSeqStream(
                Split.sorted(spliterator(), requireNonNull(comparator)));
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> limit(long size) {
        return view(Split.limit(spliterator(), size));
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> skip(long size) {
        return view(Split.skip(spliterator(), size));
    }

    /**
     * {@inheritDoc}
     */
    default void forEach(Consumer<? super E> action) {
        spliterator().forEachRemaining(action);
    }

    /**
     * {@inheritDoc}
     */
    default void forEachOrdered(Consumer<? super E> action) {
        spliterator().forEachRemaining(action);
    }

    /**
     * {@inheritDoc}
     */
    default Object[] toArray() {
        return Split.toArray(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    default <A> A[] toArray(IntFunction<A[]> generator) {
        return Split.toArray(spliterator(), requireNonNull(generator));
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> reduce(BinaryOperator<E> accumulator) {
        return Split.reduce(spliterator(), accumulator);
    }

    /**
     * {@inheritDoc}
     */
    default E reduce(E identity, BinaryOperator<E> accumulator) {
        return Split.reduce(spliterator(), identity, accumulator);
    }

    /**
     * Stream equivalent of {@link Seq#reduce(Object, BiFunction)}.
     */
    default <U> U reduce(
            U identity,
            BiFunction<U, ? super E, U> accumulator) {
        return Split.reduce(spliterator(), identity, accumulator);
    }

    /**
     * {@inheritDoc}
     */
    default <U> U reduce(
            U identity,
            BiFunction<U, ? super E, U> accumulator,
            BinaryOperator<U> ignored) {
        return Split.reduce(spliterator(), identity, accumulator);
    }

    /**
     * Equivalent to {@code collect(Seq.toSeq())}.
     */
    default Seq<E> toSeq() {
        return Seq.copy(spliterator());
    }

    /**
     * See {@link Seq#toList()}.
     */
    default List<E> toList() {
        return Split.toList(spliterator());
    }

    /**
     * See {@link Seq#toSet()}.
     */
    default Set<E> toSet() {
        return Split.toSet(spliterator());
    }

    /**
     * See {@link Seq#toMap()}.
     */
    default Map<E, E> toMap() {
        return Split.toMap(spliterator());
    }

    /**
     * See {@link Seq#toMap(Function)}.
     */
    default <K> Map<K, E> toMap(
            Function<? super E, ? extends K> keyMapper) {
        return Split.toMap(spliterator(), keyMapper);
    }

    /**
     * See {@link Seq#toMap(Function, Function)}.
     */
    default <K, V> Map<K, V> toMap(
            Function<? super E, ? extends K> keyMapper,
            Function<? super E, ? extends V> valueMapper) {
        return Split.toMap(spliterator(), keyMapper, valueMapper);
    }

    /**
     * See {@link Seq#toMap(Function, Function, BinaryOperator)}.
     */
    default <K, V> Map<K, V> toMap(
            Function<? super E, ? extends K> keyMapper,
            Function<? super E, ? extends V> valueMapper,
            BinaryOperator<V> mergeFunction) {
        return Split.toMap(
                spliterator(), keyMapper, valueMapper, mergeFunction);
    }

    /**
     * Stream equivalent of {@link Seq#collect(Supplier, BiConsumer)}.
     */
    default <U> U collect(
            Supplier<U> supplier,
            BiConsumer<U, ? super E> accumulator) {
        return Split.collect(spliterator(), supplier, accumulator);
    }

    /**
     * {@inheritDoc}
     */
    default <R> R collect(
            Supplier<R> supplier,
            BiConsumer<R, ? super E> accumulator,
            BiConsumer<R, R> ignored) {
        return Split.collect(spliterator(), supplier, accumulator);
    }

    /**
     * {@inheritDoc}
     */
    default <R, A> R collect(Collector<? super E, A, R> collector) {
        return Split.collect(spliterator(), collector);
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> min(Comparator<? super E> comparator) {
        return Split.min(spliterator(), comparator);
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> max(Comparator<? super E> comparator) {
        return Split.max(spliterator(), comparator);
    }

    /**
     * {@inheritDoc}
     */
    default long count() {
        return Split.count(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    default boolean anyMatch(Predicate<? super E> predicate) {
        return Split.anyMatch(spliterator(), predicate);
    }

    /**
     * {@inheritDoc}
     */
    default boolean allMatch(Predicate<? super E> predicate) {
        return Split.allMatch(spliterator(), predicate);
    }

    /**
     * {@inheritDoc}
     */
    default boolean noneMatch(Predicate<? super E> predicate) {
        return Split.noneMatch(spliterator(), predicate);
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> findFirst() {
        return Split.findFirst(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> findAny() {
        return Split.findFirst(spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#findOnly()}.
     */
    default Optional<E> findOnly() {
        return Split.findOnly(spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#findLast()}.
     */
    default Optional<E> findLast() {
        return Split.findLast(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> peek(Consumer<? super E> action) {
        return view(Split.peek(spliterator(), action));
    }

    /**
     * Stream equivalent of
     * {@link Seq#toString(CharSequence, CharSequence, CharSequence)}.
     */
    default String toString(
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        return Split.toString(spliterator(), delimiter, prefix, suffix);
    }

    /**
     * {@inheritDoc}
     */
    default Iterator<E> iterator() {
        return Spliterators.iterator(spliterator());
    }

    /**
     * Returns {@code false}.
     */
    default boolean isParallel() {
        return false;
    }

    /**
     * Returns {@code this}.
     */
    default SeqStream<E> sequential() {
        return this;
    }

    /**
     * Returns {@code this}, which is <em>not</em> a parallel {@code Stream}.
     */
    default SeqStream<E> parallel() {
        return this;
    }

    /**
     * Returns {@code this}.
     */
    default SeqStream<E> unordered() {
        return this;
    }

    /**
     * Throws {@code UnsupportedOperationException} unconditionally.
     * See {@link #close}.
     */
    default SeqStream<E> onClose(Runnable closeHandler) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@link SeqStream} does not support close. This method does nothing.
     */
    default void close() {
    }
}
