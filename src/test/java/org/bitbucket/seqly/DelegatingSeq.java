package org.bitbucket.seqly;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static org.bitbucket.seqly.Split.toSeqStream;

/**
 * An implementation of {@code Seq} that delegates to {@code SeqStream}
 * in order to get test coverage of {@code SeqStream} with the same set of
 * tests that are used for {@code Seq}. Though {@code Seq} itself could have
 * been written this way, that would typically require one or two more object
 * allocations per method invocation (perhaps insignificant).
 * If, say, map was not overridden in this class, it would be known because
 * of the test coverage report for {@code SeqStream}.
 */
public interface DelegatingSeq<E> extends Seq<E> {

    default List<E> toList() {
        return stream().toList();
    }

    default Set<E> toSet() {
        return stream().toSet();
    }

    default Map<E, E> toMap() {
        return stream().toMap();
    }

    default <K> Map<K, E> toMap(
            Function<? super E, ? extends K> keyMapper) {
        return stream().toMap(keyMapper);
    }

    default <K, V> Map<K, V> toMap(
            Function<? super E, ? extends K> keyMapper,
            Function<? super E, ? extends V> valueMapper) {
        return stream().toMap(keyMapper, valueMapper);
    }

    default <K, V> Map<K, V> toMap(
            Function<? super E, ? extends K> keyMapper,
            Function<? super E, ? extends V> valueMapper,
            BinaryOperator<V> mergeFunction) {
        return stream().toMap(keyMapper, valueMapper, mergeFunction);
    }

    default boolean listEquals(Iterable<?> that) {
        return stream().listEquals(toSeqStream(that));
    }

    default boolean setEquals(Iterable<?> that) {
        return stream().setEquals(toSeqStream(that));
    }

    default boolean multisetEquals(Iterable<?> that) {
        return stream().multisetEquals(toSeqStream(that));
    }

    default <F, R> Seq<R> zip(
            Iterable<? extends F> that,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        return stream().<F, R>zip(toSeqStream(that), mapper).toSeq();
    }

    default Seq<Integer> indexes() {
        return stream().indexes().toSeq();
    }

    default Seq<E> intersection(Iterable<?> that) {
        return stream().intersection(toSeqStream(that)).toSeq();
    }

    default Seq<E> difference(Iterable<?> that) {
        return stream().difference(toSeqStream(that)).toSeq();
    }

    default Seq<E> union(Iterable<? extends E> that) {
        return stream().union(toSeqStream(that)).toSeq();
    }

    default Seq<E> sum(Iterable<? extends E> that) {
        return stream().sum(toSeqStream(that)).toSeq();
    }

    default boolean containsMultiset(Iterable<?> that) {
        return stream().containsMultiset(toSeqStream(that));
    }

    default Seq<? extends Seq<E>> permutations() {
        return stream().permutations().map(SeqStream::toSeq).toSeq();
    }

    default Seq<? extends Seq<E>> combinations(int size) {
        return stream().combinations(size).map(SeqStream::toSeq).toSeq();
    }

    default Seq<? extends Seq<E>> powerSet() {
        return stream().powerSet().map(SeqStream::toSeq).toSeq();
    }

    default <R> Seq<R> flatMap(
            Function<? super E, ? extends Iterable<? extends R>> mapper) {
        return stream().<R>flatMap(mapper.andThen(iterable ->
                iterable == null ? null : toSeqStream(iterable))).toSeq();
    }

    default Seq<E> slice(int from, int to) {
        return stream().slice(from, to).toSeq();
    }

    default Seq<Integer> indexesOfSlice(Iterable<?> that) {
        return stream().indexesOfSlice(toSeqStream(that)).toSeq();
    }

    default int indexOfSlice(Iterable<?> that) {
        return stream().indexOfSlice(toSeqStream(that));
    }

    default int lastIndexOfSlice(Iterable<?> that) {
        return stream().lastIndexOfSlice(toSeqStream(that));
    }

    default boolean containsSlice(Iterable<?> that) {
        return stream().containsSlice(toSeqStream(that));
    }

    default boolean startsWith(Iterable<?> that) {
        return stream().startsWith(toSeqStream(that));
    }

    default boolean endsWith(Iterable<?> that) {
        return stream().endsWith(toSeqStream(that));
    }

    default E get(int index) {
        return stream().get(index);
    }

    default int indexOf(Object object) {
        return stream().indexOf(object);
    }

    default int lastIndexOf(Object object) {
        return stream().lastIndexOf(object);
    }

    default Seq<Integer> indexesOf(Object object) {
        return stream().indexesOf(object).toSeq();
    }

    default Seq<E> reversed() {
        return stream().reversed().toSeq();
    }

    default Seq<E> rotated(int size) {
        return stream().rotated(size).toSeq();
    }

    default Seq<E> shuffled(Random random) {
        return stream().shuffled(random).toSeq();
    }

    default Seq<E> limitLast(long size) {
        return stream().limitLast(size).toSeq();
    }

    default Seq<E> skipLast(long size) {
        return stream().skipLast(size).toSeq();
    }

    default Seq<E> takeWhile(Predicate<? super E> predicate) {
        return stream().takeWhile(predicate).toSeq();
    }

    default Seq<E> dropWhile(Predicate<? super E> predicate) {
        return stream().dropWhile(predicate).toSeq();
    }

    default Seq<E> filter(Predicate<? super E> predicate) {
        return stream().filter(predicate).toSeq();
    }

    default <R> Seq<R> map(Function<? super E, ? extends R> mapper) {
        return stream().<R>map(mapper).toSeq();
    }

    default <R> Seq<R> mapMulti(
            BiConsumer<? super E, ? super Consumer<R>> mapper) {
        return stream().<R>mapMulti(mapper).toSeq();
    }

    default Seq<E> distinct() {
        return stream().distinct().toSeq();
    }

    default Seq<E> sorted() {
        return stream().sorted().toSeq();
    }

    default Seq<E> sorted(Comparator<? super E> comparator) {
        return stream().sorted(comparator).toSeq();
    }

    default Seq<E> limit(long size) {
        return stream().limit(size).toSeq();
    }

    default Seq<E> skip(long size) {
        return stream().skip(size).toSeq();
    }

    default void forEach(Consumer<? super E> action) {
        // only delegate because this is for testing
        stream().forEach(action);
    }

    default void forEachOrdered(Consumer<? super E> action) {
        // only delegate because this is for testing
        stream().forEachOrdered(action);
    }

    default Object[] toArray() {
        return stream().toArray();
    }

    default <A> A[] toArray(IntFunction<A[]> generator) {
        return stream().toArray(generator);
    }

    default E reduce(E identity, BinaryOperator<E> accumulator) {
        return stream().reduce(identity, accumulator);
    }

    default Optional<E> reduce(BinaryOperator<E> accumulator) {
        return stream().reduce(accumulator);
    }

    default <U> U reduce(U identity, BiFunction<U, ? super E, U> accumulator) {
        return stream().reduce(identity, accumulator);
    }

    default Seq<E> collect() {
        return stream().toSeq();
    }

    default <U> U collect(Supplier<U> supplier, BiConsumer<U, ? super E> accumulator) {
        return stream().collect(supplier, accumulator);
    }

    default <R, A> R collect(Collector<? super E, A, R> collector) {
        return stream().collect(collector);
    }

    default Optional<E> min(Comparator<? super E> comparator) {
        return stream().min(comparator);
    }

    default Optional<E> max(Comparator<? super E> comparator) {
        return stream().max(comparator);
    }

    default long count() {
        return stream().count();
    }

    default boolean anyMatch(Predicate<? super E> predicate) {
        return stream().anyMatch(predicate);
    }

    default boolean allMatch(Predicate<? super E> predicate) {
        return stream().allMatch(predicate);
    }

    default boolean noneMatch(Predicate<? super E> predicate) {
        return stream().noneMatch(predicate);
    }

    default Optional<E> findFirst() {
        return stream().findFirst();
    }

    default Optional<E> findAny() {
        return stream().findAny();
    }

    default Optional<E> findOnly() {
        return stream().findOnly();
    }

    default Optional<E> findLast() {
        return stream().findLast();
    }

    default Seq<E> peek(Consumer<? super E> action) {
        // only delegate because this is for testing
        return stream().peek(action).toSeq();
    }

    default String toString(
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        return stream().toString(delimiter, prefix, suffix);
    }

    default int size() {
        return stream().size();
    }

    default boolean isEmpty() {
        return stream().isEmpty();
    }

    default boolean contains(Object object) {
        return stream().contains(object);
    }

    default <T> T[] toArray(T[] ts) {
        return stream().toArray(ts);
    }

    default boolean containsAll(Collection<?> that) {
        return stream().containsAll(toSeqStream(that));
    }

    default Iterator<E> iterator() {
        // only delegate because this is for testing
        return stream().iterator();
    }
}
