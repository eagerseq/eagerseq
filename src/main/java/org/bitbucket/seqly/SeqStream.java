package org.bitbucket.seqly;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Random;
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

import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.joining;

/**
 * <p>The subtype of {@code Stream} returned by
 * {@link Seq#stream()}. In addition to {@code Stream} methods like
 * {@code map}, {@code filter} and {@code reduce}, {@code SeqStream} defines
 * lazy versions of most other {@link Seq} methods like
 * {@code slice}, {@code intersection} and {@code zip}. Intermediate operations
 * return {@code SeqStream} so they can be chained and a no-args
 * {@code collect()} method converts back to {@code Seq}.
 */
public interface SeqStream<E> extends Stream<E> {

    /**
     * Returns a {@code SeqStream} containing the given elements.
     */
    static <E> SeqStream<E> view(Iterator<? extends E> iterator) {
        return new SpliteratorSeqStream<>(Util.spliterator(iterator));
    }

    /**
     * Returns a {@code SeqStream} containing the given elements.
     */
    static <E> SeqStream<E> view(Spliterator<? extends E> spliterator) {
        return new SpliteratorSeqStream<>(spliterator);
    }

    /**
     * Returns a {@code SeqStream} containing the given elements.
     */
    // javadoc, mention close of given stream never called, (and parallel not respected?)
    static <E> SeqStream<E> view(Stream<? extends E> stream) {
        return new SpliteratorSeqStream<>(stream.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#range(int, int)}.
     */
    static SeqStream<Integer> range(int begin, int end) {
        return view(IntStream.range(begin, end).boxed());
    }

    /**
     * Stream equivalent of {@link Seq#concat(Iterable...)}.
     */
    @SafeVarargs
    static <E> SeqStream<E> concat(
            Stream<? extends E>... streams) {
        return flatten(Stream.of(streams));
    }

    /**
     * Stream equivalent of {@link Seq#flatten(Iterable)}.
     */
    static <E> SeqStream<E> flatten(
            Stream<? extends Stream<? extends E>> streams) {
        // avoid Stream.flatMap bug
        return view(Util.flatten(
                streams.map(Stream::spliterator).spliterator()));
    }

    /**
     * {@inheritDoc}
     */
    Spliterator<E> spliterator();

    /**
     * Returns an ordinary {@code Stream} that is not a {@code SeqStream}.
     */
    default Stream<E> stream() {
        return Util.stream(spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#listEquals(Iterable)}.
     */
    default boolean listEquals(Stream<?> that) {
        return Util.listEquals(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#setEquals(Iterable)}.
     */
    default boolean setEquals(Stream<?> that) {
        return Util.setEquals(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#multisetEquals(Iterable)}.
     */
    default boolean multisetEquals(Stream<?> that) {
        return Util.multisetEquals(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#zip(Iterable, BiFunction)}.
     */
    default <F, R> SeqStream<R> zip(
            Stream<? extends F> that,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        return view(Util.zip(spliterator(), that.spliterator(), mapper));
    }

    /**
     * Stream equivalent of {@link Seq#indexes()}.
     */
    default SeqStream<Integer> indexes() {
        return view(Util.indexes(spliterator()));
    }

    /**
     * Stream equivalent of {@link Seq#intersection(Iterable)}.
     */
    default SeqStream<E> intersection(Stream<?> that) {
        return view(Util.intersection(spliterator(), that.spliterator()));
    }

    /**
     * Stream equivalent of {@link Seq#difference(Iterable)}.
     */
    default SeqStream<E> difference(Stream<?> that) {
        return view(Util.difference(spliterator(), that.spliterator()));
    }

    /**
     * Stream equivalent of {@link Seq#union(Iterable)}.
     */
    default SeqStream<E> union(Stream<? extends E> that) {
        return view(Util.union(spliterator(), that.spliterator()));
    }

    /**
     * Stream equivalent of {@link Seq#sum(Iterable)}.
     */
    default SeqStream<E> sum(Stream<? extends E> that) {
        return flatten(Stream.of(this, that));
    }

    /**
     * Stream equivalent of {@link Seq#containsMultiset(Iterable)}.
     */
    default boolean containsMultiset(Stream<?> that) {
        return !Util.difference(that.spliterator(), spliterator())
                .tryAdvance(e -> {});
    }

    /**
     * Stream equivalent of {@link Seq#slice(int, int)}.
     */
    default SeqStream<E> slice(int from, int to) {
        return limit(to).skip(from);
    }

    /**
     * Stream equivalent of {@link Seq#indexesOfSlice(Iterable)}.
     */
    default SeqStream<Integer> indexesOfSlice(Stream<?> that) {
        return view(Util.indexesOfSlice(spliterator(), that.spliterator()));
    }

    /**
     * Stream equivalent of {@link Seq#indexesOfSlice(Iterable)}.
     */
    default int indexOfSlice(Stream<?> that) {
        return indexesOfSlice(that).findFirst().orElse(-1);
    }

    /**
     * Stream equivalent of {@link Seq#lastIndexOfSlice(Iterable)}.
     */
    default int lastIndexOfSlice(Stream<?> that) {
        return indexesOfSlice(that).findLast().orElse(-1);
    }

    /**
     * Stream equivalent of {@link Seq#containsSlice(Iterable)}.
     */
    default boolean containsSlice(Stream<?> that) {
        return !indexesOfSlice(that).isEmpty();
    }

    /**
     * Stream equivalent of {@link Seq#startsWith(Iterable)}.
     */
    default boolean startsWith(Stream<?> that) {
        // return zip(that, Objects::equals).allMatch(e -> e);
        Object[] array = that.toArray();
        return limit(array.length)
                .listEquals(view(Util.spliterator(array)));
    }

    /**
     * Stream equivalent of {@link Seq#endsWith(Iterable)}.
     */
    default boolean endsWith(Stream<?> that) {
        Object[] array = that.toArray();
        return limitLast(array.length)
                .listEquals(view(Util.spliterator(array)));
    }

    /**
     * Stream equivalent of {@link Seq#get(int)}.
     */
    @SuppressWarnings("unchecked")
    default E get(int index) {
        if (index < 0) throw new IndexOutOfBoundsException();
        // findFirst throws for null
        Object[] some = skip(index).limit(1).toArray();
        if (some.length == 0) throw new IndexOutOfBoundsException();
        return (E) some[0];
    }

    /**
     * Stream equivalent of {@link Seq#indexOf(Object)}.
     */
    default int indexOf(Object object) {
        // findFirst throws for null
        Object[] some = indexesOf(object).limit(1).toArray();
        return some.length == 0 ? -1 : (int) some[0];
    }

    /**
     * Stream equivalent of {@link Seq#lastIndexOf(Object)}.
     */
    default int lastIndexOf(Object object) {
        // findLast throws for null
        Object[] some = indexesOf(object).limitLast(1).toArray();
        return some.length == 0 ? -1 : (int) some[0];
    }

    /**
     * Stream equivalent of {@link Seq#indexesOf(Object)}.
     */
    default SeqStream<Integer> indexesOf(Object object) {
        return view(Util.indexesOf(spliterator(), object));
    }

    /**
     * Stream equivalent of {@link Seq#reversed()}.
     */
    default SeqStream<E> reversed() {
        Object[] array = toArray();
        Util.reverse(array);
        return view(Util.spliterator(array));
    }

    /**
     * Stream equivalent of {@link Seq#rotated(int)}.
     */
    default SeqStream<E> rotated(int size) {
        Object[] array = toArray();
        Util.rotate(array, size);
        return view(Util.spliterator(array));
    }

    /**
     * Stream equivalent of {@link Seq#shuffled(Random)}.
     */
    default SeqStream<E> shuffled(Random random) {
        Object[] array = toArray();
        Util.shuffle(array, random);
        return view(Util.spliterator(array));
    }

    /**
     * Stream equivalent of {@link Seq#size()}.
     */
    default int size() {
        return Util.toInt(count());
    }

    /**
     * Stream equivalent of {@link Seq#isEmpty()}.
     */
    default boolean isEmpty() {
        return noneMatch(element -> true);
    }

    /**
     * Stream equivalent of {@link Seq#contains(Object)}.
     */
    default boolean contains(Object object) {
        return anyMatch(isEqual(object));
    }

    /**
     * Stream equivalent of {@link Seq#toArray(Object[])}.
     */
    default <T> T[] toArray(T[] ts) {
        return Util.toArray(this::toArray, ts);
    }

    /**
     * Stream equivalent of {@link Seq#containsAll(Collection)}.
     */
    default boolean containsAll(Stream<?> that) {
        return containsMultiset(that.distinct());
    }

    /**
     * Stream equivalent of {@link Seq#limitLast(long)}.
     */
    default SeqStream<E> limitLast(long size) {
        return view(Util.limitLast(spliterator(), size));
    }

    /**
     * Stream equivalent of {@link Seq#skipLast(long)}.
     */
    default SeqStream<E> skipLast(long size) {
        return view(Util.skipLast(spliterator(), size));
    }

    /**
     * Stream equivalent of {@link Seq#takeWhile(Predicate)}.
     */
    default SeqStream<E> takeWhile(Predicate<? super E> predicate) {
        return view(Util.takeWhile(spliterator(), predicate));
    }

    /**
     * Stream equivalent of {@link Seq#dropWhile(Predicate)}.
     */
    default SeqStream<E> dropWhile(Predicate<? super E> predicate) {
        return view(Util.dropWhile(spliterator(), predicate));
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> filter(Predicate<? super E> predicate) {
        return view(stream().filter(predicate));
    }

    /**
     * {@inheritDoc}
     */
    default <R> SeqStream<R> map(
            Function<? super E, ? extends R> mapper) {
        return view(stream().map(mapper));
    }

    /**
     * {@inheritDoc}
     */
    default IntStream mapToInt(ToIntFunction<? super E> mapper) {
        return stream().mapToInt(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default LongStream mapToLong(ToLongFunction<? super E> mapper) {
        return stream().mapToLong(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default DoubleStream mapToDouble(
            ToDoubleFunction<? super E> mapper) {
        return stream().mapToDouble(mapper);
    }

    /**
     * Stream equivalent of {@link Seq#flatMap(Function)}.
     */
    default <R> SeqStream<R> flatMap(
            Function<? super E, ? extends Stream<? extends R>> mapper) {
        // avoid Stream.flatMap bug
        return flatten(map(mapper));
    }

    /**
     * {@inheritDoc}
     */
    default IntStream flatMapToInt(
            Function<? super E, ? extends IntStream> mapper) {
        return stream().flatMapToInt(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default LongStream flatMapToLong(
            Function<? super E, ? extends LongStream> mapper) {
        return stream().flatMapToLong(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default DoubleStream flatMapToDouble(
            Function<? super E, ? extends DoubleStream> mapper) {
        return stream().flatMapToDouble(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> distinct() {
        return view(stream().distinct());
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> sorted() {
        return view(stream().sorted());
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> sorted(Comparator<? super E> comparator) {
        return view(stream().sorted(comparator));
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> limit(long size) {
        return view(stream().limit(size));
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> skip(long size) {
        return view(stream().skip(size));
    }

    /**
     * {@inheritDoc}
     */
    default void forEach(Consumer<? super E> action) {
        stream().forEach(action);
    }

    /**
     * {@inheritDoc}
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
     * {@inheritDoc}
     */
    default <A> A[] toArray(IntFunction<A[]> generator) {
        return stream().toArray(generator);
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> reduce(BinaryOperator<E> accumulator) {
        return stream().reduce(accumulator);
    }

    /**
     * {@inheritDoc}
     */
    default E reduce(E identity, BinaryOperator<E> accumulator) {
        return stream().reduce(identity, accumulator);
    }

    /**
     * Stream equivalent of {@link Seq#reduce(Object, BiFunction)}.
     */
    default <U> U reduce(
            U identity,
            BiFunction<U, ? super E, U> accumulator) {
        return stream().reduce(identity, accumulator, (a, b) -> {
            throw new RuntimeException();
        });
    }

    /**
     * {@inheritDoc}
     */
    default <U> U reduce(
            U identity,
            BiFunction<U, ? super E, U> accumulator,
            BinaryOperator<U> combiner) {
        return stream().reduce(identity, accumulator, combiner);
    }

    /**
     * Equivalent to {@code collect(Seq.toSeq())}.
     */
    default Seq<E> collect() {
        return collect(Seq.toSeq());
    }

    /**
     * Stream equivalent of {@link Seq#collect(Supplier, BiConsumer)}.
     */
    default <U> U collect(
            Supplier<U> supplier,
            BiConsumer<U, ? super E> accumulator) {
        return stream().collect(supplier, accumulator, (a, b) -> {
            throw new RuntimeException();
        });
    }

    /**
     * {@inheritDoc}
     */
    default <R> R collect(
            Supplier<R> supplier,
            BiConsumer<R, ? super E> accumulator,
            BiConsumer<R, R> combiner) {
        return stream().collect(supplier, accumulator, combiner);
    }

    /**
     * {@inheritDoc}
     */
    default <R, A> R collect(Collector<? super E, A, R> collector) {
        return stream().collect(collector);
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> min(Comparator<? super E> comparator) {
        return stream().min(comparator);
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> max(Comparator<? super E> comparator) {
        return stream().max(comparator);
    }

    /**
     * {@inheritDoc}
     */
    default long count() {
        return Util.count(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    default boolean anyMatch(Predicate<? super E> predicate) {
        return stream().anyMatch(predicate);
    }

    /**
     * {@inheritDoc}
     */
    default boolean allMatch(Predicate<? super E> predicate) {
        return stream().allMatch(predicate);
    }

    /**
     * {@inheritDoc}
     */
    default boolean noneMatch(Predicate<? super E> predicate) {
        return stream().noneMatch(predicate);
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> findFirst() {
        return stream().findFirst();
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> findAny() {
        return stream().findAny();
    }

    /**
     * Stream equivalent of {@link Seq#findOnly()}.
     */
    default Optional<E> findOnly() {
        return Util.findOnly(spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#findLast()}.
     */
    default Optional<E> findLast() {
        return Util.findLast(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> peek(Consumer<? super E> action) {
        return view(stream().peek(action));
    }

    /**
     * Stream equivalent of
     * {@link Seq#toString(CharSequence, CharSequence, CharSequence)}.
     */
    default String toString(
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        return stream().map(Object::toString)
                .collect(joining(delimiter, prefix, suffix));
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
     * Throws {@code UnsupportedOperationException} unconditionally.
     */
    default SeqStream<E> parallel() {
        throw new UnsupportedOperationException();
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
     * Close-handlers of any underlying
     * {@link java.util.stream.Stream Stream} will not be called.
     * Call {@code close} on the underlying stream directly if required.
     */
    default void close() {
    }
}
