package seqly;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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
import java.util.stream.Collector;
import java.util.stream.Stream;

public interface Seq<E> extends Collection<E> {

    @SafeVarargs
    public static <E> Seq<E> of(E... elements) {
        return new ArraySeq<>(Objects.requireNonNull(elements));
    }

    public static <E> Seq<E> copy(E[] elements) {
        return new ArraySeq<>(Arrays.copyOf(elements, elements.length));
    }

    public static <E> Seq<E> view(E[] elements) {
        return new ArraySeq<>(Objects.requireNonNull(elements));
    }

    public static <E> Seq<E> copy(Iterable<? extends E> iterable) {
        return new ArraySeq<>(Util.toArray(iterable));
    }

    public static <E> Seq<E> view(Iterable<? extends E> iterable) {
        return new IterableSeq<>(Objects.requireNonNull(iterable));
    }

    public static <E> Seq<E> copy(Collection<? extends E> collection) {
        return new ArraySeq<>(collection.toArray());
    }

    public static <E> Seq<E> view(Collection<? extends E> collection) {
        return new CollectionSeq<>(Objects.requireNonNull(collection));
    }

    public static <E> Seq<E> view(Optional<? extends E> optional) {
        return new OptionalSeq<>(Objects.requireNonNull(optional));
    }

    public static <E> Seq<E> copy(Iterator<? extends E> iterator) {
        return new ArraySeq<>(Util.toArray(iterator));
    }

    public static <E> Seq<E> copy(Spliterator<? extends E> spliterator) {
        return new ArraySeq<>(Util.toArray(spliterator));
    }

    public static <E> Seq<E> copy(Stream<? extends E> stream) {
        return new ArraySeq<>(stream.toArray());
    }

    public static <E> Builder<E> builder() {
        return new ArraySeq.Builder<>();
    }

    public static <E> Collector<E, ?, Seq<E>> toSeq() {
        return Collector.<E, Builder<E>, Seq<E>>of(
                Seq::builder, Builder::add, (b, c) -> {
                    c.build().forEach(b::add);
                    return b;
                }, Builder::build);
    }

    public static Seq<Integer> range(int begin, int end) {
        return SeqStream.range(begin, end).collect();
    }

    public static <E> Seq<E> concat(
            Collection<? extends E> first, Collection<? extends E> second) {
        return SeqStream.concat(first.stream(), second.stream()).collect();
    }

    /**
     * Returns the intersection of two sets. When there are repeated elements,
     * this returns the multiset definition of the intersection. That is,
     * when there are <tt>a</tt> equal elements in <tt>first</tt> and
     * <tt>b</tt> in <tt>second</tt>, the result contains <tt>a - b</tt>
     * elements or none if that would be negative. Specifically, it contains
     * those elements which occur earliest in the encounter order. The
     * {@link #difference} contains the remaining elements.
     * Note, this multiset definition is consistent with the ordinary set
     * definition when there are not repeated elements. However it is
     * not exactly equivalent to the mutable methods
     * {@link java.util.Collection#containsAll(Collection) containsAll},
     * {@link java.util.Collection#removeAll(Collection) removeAll} and
     * {@link java.util.Collection#retainAll(Collection) retainAll}.
     */
    public static <E> Seq<E> intersection(
            Collection<? extends E> first, Collection<? extends E> second) {
        return copy(Util.intersection(first.spliterator(), second.spliterator()));
    }

    /**
     * Returns the difference of two sets. When there are repeated elements,
     * this returns the multiset definition of the difference. See
     * {@link #intersection}.
     */
    public static <E> Seq<E> difference(
            Collection<? extends E> first, Collection<? extends E> second) {
        return copy(Util.difference(first.spliterator(), second.spliterator()));
    }

    /**
     * Returns the union of two sets. When there are repeated elements,
     * this returns the multiset definition of the union. See
     * {@link #intersection}.
     */
    public static <E> Seq<E> union(
            Collection<? extends E> first, Collection<? extends E> second) {
        return concat(difference(first, second), second);
    }

    public abstract Spliterator<E> spliterator();

    public default List<E> asList() {
        return new SeqList<>(this);
    }

    public default <U> U reduce(
            U identity,
            BiFunction<U, ? super E, U> accumulator) {
        return stream().reduce(identity, accumulator);
    }

    public default <U> U collect(
            Supplier<U> supplier,
            BiConsumer<U, ? super E> accumulator) {
        return stream().collect(supplier, accumulator);
    }

    public default <F> Seq<F> flattenIterables(
            Function<? super E, ? extends Iterable<? extends F>> function) {
        return stream().<F>flattenIterables(function).collect();
    }

    public default <F> Seq<F> flattenOptionals(
            Function<? super E, ? extends Optional<? extends F>> function) {
        return stream().<F>flattenOptionals(function).collect();
    }

    /**
     * Equivalent to limit(to).skip(from) and therefore is not a view
     * and does not check indices like subList and substring.
     */
    public default Seq<E> subseq(int from, int to) {
        return stream().subseq(from, to).collect();
    }

    public default E get(int index) {
        return stream().get(index);
    }

    public default int indexOf(Object object) {
        return stream().indexOf(object);
    }

    public default int lastIndexOf(Object object) {
        return stream().lastIndexOf(object);
    }

    public default Seq<Integer> indexesOf(Object object) {
        return stream().indexesOf(object).collect();
    }

    public default Optional<E> findOnly() {
        return stream().findOnly();
    }

    public default Optional<E> findLast() {
        return stream().findLast();
    }

    public default Seq<E> reversed() {
        return stream().reversed().collect();
    }

    public default Seq<E> rotated(int size) {
        return stream().rotated(size).collect();
    }

    public default Seq<E> shuffled(Random random) {
        return stream().shuffled(random).collect();
    }

    public default Seq<E> takeWhile(Predicate<? super E> predicate) {
        return stream().takeWhile(predicate).collect();
    }

    public default Seq<E> dropWhile(Predicate<? super E> predicate) {
        return stream().dropWhile(predicate).collect();
    }

    public default Seq<E> filter(Predicate<? super E> predicate) {
        return stream().filter(predicate).collect();
    }

    public default <R> Seq<R> map(Function<? super E, ? extends R> mapper) {
        return stream().<R>map(mapper).collect();
    }

    public default <R> Seq<R> flatMap(
            Function<? super E, ? extends Stream<? extends R>> mapper) {
        return stream().<R>flatMap(mapper).collect();
    }

    public default Seq<E> distinct() {
        return stream().distinct().collect();
    }

    public default Seq<E> sorted() {
        return stream().sorted().collect();
    }

    public default Seq<E> sorted(Comparator<? super E> comparator) {
        return stream().sorted(comparator).collect();
    }

    public default Seq<E> limit(long size) {
        return stream().limit(size).collect();
    }

    public default Seq<E> skip(long size) {
        return stream().skip(size).collect();
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
        return stream().count();
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

    public default int size() {
        return stream().size();
    }

    public default boolean isEmpty() {
        return stream().isEmpty();
    }

    public default boolean contains(Object object) {
        return stream().contains(object);
    }

    public default <T> T[] toArray(T[] ts) {
        return stream().toArray(ts);
    }

    public default boolean add(E element) {
        throw new UnsupportedOperationException();
    }

    public default boolean remove(Object element) {
        throw new UnsupportedOperationException();
    }

    public default boolean containsAll(Collection<?> collection) {
        for (Object element : collection) {
            if (!contains(element)) {
                return false;
            }
        }
        return true;
    }

    public default boolean addAll(Collection<? extends E> collection) {
        throw new UnsupportedOperationException();
    }

    public default boolean removeAll(Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    public default boolean removeIf(Predicate<? super E> filter) {
        throw new UnsupportedOperationException();
    }

    public default boolean retainAll(Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    public default void clear() {
        throw new UnsupportedOperationException();
    }

    public default SeqStream<E> stream() {
        return SeqStream.view(spliterator());
    }

    public default SeqStream<E> parallelStream() {
        throw new UnsupportedOperationException();
    }

    public default Iterator<E> iterator() {
        return Spliterators.iterator(spliterator());
    }

    public static interface Builder<E> {
        public abstract Builder<E> add(E element);
        public abstract Seq<E> build();
    }
}
