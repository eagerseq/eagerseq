package seqly;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Spliterator;
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

import static java.util.function.Function.identity;

public interface SeqStream<E> extends Stream<E> {

    public static <E> SeqStream<E> view(Iterator<? extends E> iterator) {
        return new StreamSeqStream<>(Util.stream(iterator));
    }

    public static <E> SeqStream<E> view(Spliterator<? extends E> spliterator) {
        return new StreamSeqStream<>(Util.stream(spliterator));
    }

    public static <E> SeqStream<E> view(Stream<? extends E> stream) {
        if (stream.isParallel()) {
            throw new IllegalArgumentException("stream was parallel");
        }
        return new StreamSeqStream<>(stream);
    }

    public static SeqStream<Integer> range(int begin, int end) {
        return view(IntStream.range(begin, end).boxed());
    }

    public static <E> SeqStream<E> concat(
            Stream<? extends E> first, Stream<? extends E> second) {
        return view(Stream.concat(first, second));
    }

    public static <E> SeqStream<E> concat(
            Stream<? extends Stream<? extends E>> streams) {
        return view(streams.<E>flatMap(identity()));
    }

    public static <E> SeqStream<E> cycle(Iterable<E> iterable) {
        return view(Util.cycle(iterable));
    }

    /**
     * Returns the underlying stream. This {@link SeqStream} object
     * cannot be used after calling this method.
     */
    public abstract Stream<E> stream();

    public default Seq<E> collect() {
        return new ArraySeq<>(toArray());
    }

    public default <U> U reduce(
            U identity,
            BiFunction<U, ? super E, U> accumulator) {
        return reduce(identity, accumulator, (a, b) -> {
            throw new RuntimeException();
        });
    }

    public default <U> U collect(
            Supplier<U> supplier,
            BiConsumer<U, ? super E> accumulator) {
        return collect(supplier, accumulator, (a, b) -> {
            throw new RuntimeException();
        });
    }

    public default SeqStream<Seq<E>> grouped(int size) {
        return view(Util.grouped(spliterator(), size)).map(Seq::view);
    }

    public default <R> SeqStream<R> zip(
            BiFunction<? super E, Integer, ? extends R> function) {
        return view(Util.zip(spliterator(), function));
    }

    public default <F, R> SeqStream<R> zip(
            Stream<? extends F> that,
            BiFunction<? super E, ? super F, ? extends R> function) {
        return view(Util.zip(spliterator(), that.spliterator(), function));
    }

    public default SeqStream<E> intersection(Iterable<?> that) {
        return view(Util.intersection(spliterator(), that.spliterator()));
    }

    public default SeqStream<E> difference(Iterable<?> that) {
        return view(Util.difference(spliterator(), that.spliterator()));
    }

    public default SeqStream<E> union(Iterable<? extends E> that) {
        return concat(difference(that), Util.stream(that));
    }

    public default <F> SeqStream<F> flattenOptionals(
            Function<? super E, ? extends Optional<? extends F>> function) {
        return flatMap(function.andThen(Util::stream));
    }

    public default SeqStream<E> subseq(int from, int to) {
        return limit(to).skip(from);
    }

    public default E get(int index) {
        return skip(index).findFirst().get();
    }

    public default int indexOf(Object object) {
        return indexesOf(object).findFirst().orElse(-1);
    }

    public default int lastIndexOf(Object object) {
        return indexesOf(object).findLast().orElse(-1);
    }

    public default SeqStream<Integer> indexesOf(Object object) {
        return view(Util.indexesOf(spliterator(), object));
    }

    public default Optional<E> findOnly() {
        return Util.findOnly(spliterator());
    }

    public default Optional<E> findLast() {
        return Util.findLast(spliterator());
    }

    // Should not process until first use of stream?
    // seq.stream().reversed().limit(0).findFirst()
    // seq.stream().sorted().limit(0).findFirst()
    public default SeqStream<E> reversed() {
        Object[] array = toArray();
        Util.reverse(array);
        return view(Util.stream(array));
    }

    // See reversed()
    public default SeqStream<E> rotated(int size) {
        // SeqStream.concat(limitLast(size), skipLast(size))
        // SeqStream.concat(skip(size() - size), limit(size() - size))
        // with mod
        Object[] array = toArray();
        Util.rotate(array, size);
        return view(Util.stream(array));
    }

    // See reversed()
    public default SeqStream<E> shuffled(Random random) {
        Object[] array = toArray();
        Util.shuffle(array, random);
        return view(Util.stream(array));
    }

    public default int size() {
        return Util.toInt(count());
    }

    public default boolean isEmpty() {
        return noneMatch(element -> true);
    }

    public default boolean contains(Object object) {
        return anyMatch(object == null ? Objects::isNull : object::equals);
    }

    public default <T> T[] toArray(T[] ts) {
        return Util.toArray(this::toArray, ts);
    }

    public default SeqStream<E> limitLast(int size) {
        return view(Util.limitLast(spliterator(), size));
    }

    public default SeqStream<E> skipLast(int size) {
        return view(Util.skipLast(spliterator(), size));
    }

    public default SeqStream<E> takeWhile(Predicate<? super E> predicate) {
        return view(Util.takeWhile(spliterator(), predicate));
    }

    public default SeqStream<E> dropWhile(Predicate<? super E> predicate) {
        return view(Util.dropWhile(spliterator(), predicate));
    }

    public default SeqStream<E> filter(Predicate<? super E> predicate) {
        return view(stream().filter(predicate));
    }

    public default <R> SeqStream<R> map(Function<? super E, ? extends R> mapper) {
        return view(stream().map(mapper));
    }

    public default IntStream mapToInt(ToIntFunction<? super E> mapper) {
        return stream().mapToInt(mapper);
    }

    public default LongStream mapToLong(ToLongFunction<? super E> mapper) {
        return stream().mapToLong(mapper);
    }

    public default DoubleStream mapToDouble(ToDoubleFunction<? super E> mapper) {
        return stream().mapToDouble(mapper);
    }

    public default <R> SeqStream<R> flatMap(
            Function<? super E, ? extends Stream<? extends R>> mapper) {
        return view(stream().flatMap(mapper));
    }

    public default IntStream flatMapToInt(Function<? super E, ? extends IntStream> mapper) {
        return stream().flatMapToInt(mapper);
    }

    public default LongStream flatMapToLong(Function<? super E, ? extends LongStream> mapper) {
        return stream().flatMapToLong(mapper);
    }

    public default DoubleStream flatMapToDouble(Function<? super E, ? extends DoubleStream> mapper) {
        return stream().flatMapToDouble(mapper);
    }

    public default SeqStream<E> distinct() {
        return view(stream().distinct());
    }

    public default SeqStream<E> sorted() {
        return view(stream().sorted());
    }

    public default SeqStream<E> sorted(Comparator<? super E> comparator) {
        return view(stream().sorted(comparator));
    }

    public default SeqStream<E> limit(long size) {
        return view(stream().limit(size));
    }

    public default SeqStream<E> skip(long size) {
        return view(stream().skip(size));
    }

    public default void forEach(Consumer<? super E> action) {
        stream().forEach(action);
    }

    public default void forEachOrdered(Consumer<? super E> action) {
        stream().forEachOrdered(action);
    }

    public default Object[] toArray() {
        return stream().toArray();
    }

    public default <A> A[] toArray(IntFunction<A[]> generator) {
        return stream().toArray(generator);
    }

    public default E reduce(E identity, BinaryOperator<E> accumulator) {
        return stream().reduce(identity, accumulator);
    }

    public default Optional<E> reduce(BinaryOperator<E> accumulator) {
        return stream().reduce(accumulator);
    }

    public default <U> U reduce(
            U identity,
            BiFunction<U, ? super E, U> accumulator,
            BinaryOperator<U> combiner) {
        return stream().reduce(identity, accumulator, combiner);
    }

    public default <R> R collect(
            Supplier<R> supplier,
            BiConsumer<R, ? super E> accumulator,
            BiConsumer<R, R> combiner) {
        return stream().collect(supplier, accumulator, combiner);
    }

    public default <R, A> R collect(Collector<? super E, A, R> collector) {
        return stream().collect(collector);
    }

    public default Optional<E> min(Comparator<? super E> comparator) {
        return stream().min(comparator);
    }

    public default Optional<E> max(Comparator<? super E> comparator) {
        return stream().max(comparator);
    }

    public default long count() {
        return Util.count(spliterator());
    }

    public default boolean anyMatch(Predicate<? super E> predicate) {
        return stream().anyMatch(predicate);
    }

    public default boolean allMatch(Predicate<? super E> predicate) {
        return stream().allMatch(predicate);
    }

    public default boolean noneMatch(Predicate<? super E> predicate) {
        return stream().noneMatch(predicate);
    }

    public default Optional<E> findFirst() {
        return stream().findFirst();
    }

    public default Optional<E> findAny() {
        return stream().findAny();
    }

    public default SeqStream<E> peek(Consumer<? super E> action) {
        return view(stream().peek(action));
    }

    public default Iterator<E> iterator() {
        return stream().iterator();
    }

    public default Spliterator<E> spliterator() {
        return stream().spliterator();
    }

    public default boolean isParallel() {
        return false;
    }

    public default SeqStream<E> sequential() {
        return this;
    }

    /**
     * Throws {@code UnsupportedOperationException} unconditionally.
     * @throws UnsupportedOperationException
     */
    public default SeqStream<E> parallel() {
        throw new UnsupportedOperationException();
    }

    public default SeqStream<E> unordered() {
        return view(stream().unordered());
    }

    /**
     * Throws {@code UnsupportedOperationException} unconditionally.
     * See {@link #close}.
     * @throws UnsupportedOperationException
     */
    public default SeqStream<E> onClose(Runnable closeHandler) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@link SeqStream} does not support close. This method is a no-op.
     * Close-handlers of any underlying
     * {@link java.util.stream.Stream Stream} will not be called.
     */
    public default void close() {
    }
}
