package io.github.eagerseq;

import java.util.Arrays;
import java.util.Collection;
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
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;

/**
 * <p>The subtype of {@code Stream} returned by
 * {@link Seq#stream()}. In addition to {@code Stream} methods like
 * {@code map}, {@code filter} and {@code reduce}, {@code SeqStream} defines
 * lazy versions of most other {@link Seq} methods like
 * {@code slice}, {@code intersection} and {@code zip}. Intermediate operations
 * return {@code SeqStream} so they can be chained and a no-args
 * {@code toSeq()} method converts back to {@code Seq}.
 *
 * <p>As in {@code Stream}, close handlers, whether the pipeline is closed
 * and its parallel mode belong to a whole pipeline rather than to a single
 * stage, so {@link #onClose}, {@link #close}, {@link #parallel} and
 * {@link #sequential} affect every stage derived from the same source.
 * A {@code SeqStream} is always evaluated sequentially; see
 * {@link #parallel}.
 *
 * <p>An operation that takes another {@code Stream} directly, such as
 * {@link #zip} or {@link #listEquals}, adopts that stream into this pipeline,
 * which therefore closes it when it is closed. The operation itself does not
 * close either stream merely because traversal completes. The inner streams
 * of {@link #flatten} and {@link #flatMap} are temporary traversal resources
 * and are closed as they are consumed, or when this pipeline is closed.
 */
public interface SeqStream<E> extends Stream<E> {

    /**
     * Returns an empty {@code SeqStream}.
     */
    static <E> SeqStream<E> of() {
        return new SpliteratorSeqStream<>(
                Split.toSpliterator(ArrayBuilder.EMPTY));
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
                Split.toSpliterator(Arrays.copyOf(
                        requireNonNull(elements), elements.length)));
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
    static <E> SeqStream<E> viewOf(Iterator<? extends E> iterator) {
        return new SpliteratorSeqStream<>(
                Split.toSpliterator(requireNonNull(iterator)));
    }

    /**
     * Returns a {@code SeqStream} containing the given elements.
     */
    static <E> SeqStream<E> viewOf(Spliterator<? extends E> spliterator) {
        return new SpliteratorSeqStream<>(requireNonNull(spliterator));
    }

    /**
     * Returns a {@code SeqStream} containing the given elements
     * and using the given shared {@code Pipeline}.
     */
    static <E> SeqStream<E> viewOf(
            Spliterator<? extends E> spliterator, Pipeline pipeline) {
        requireNonNull(spliterator);
        requireNonNull(pipeline);
        return new SpliteratorSeqStream<>(spliterator, pipeline);
    }

    /**
     * Returns a {@code SeqStream} containing the given elements. The result
     * takes its initial parallel mode from {@code stream} and closes
     * {@code stream} when the result is closed.
     */
    static <E> SeqStream<E> viewOf(Stream<? extends E> stream) {
        requireNonNull(stream);
        SeqStream<E> result = viewOf(stream.spliterator());
        result.closes(stream);
        return stream.isParallel() ? result.parallel() : result;
    }

    /**
     * Returns a {@link Builder}.
     */
    static <E> Builder<E> builder() {
        return new SeqStreamBuilder<>();
    }

    /**
     * Stream equivalent of {@link Seq#range(int, int)}.
     */
    static SeqStream<Integer> range(int from, int to) {
        return viewOf(Split.range(from, to));
    }

    /**
     * Stream equivalent of {@link Seq#range(long, long)}.
     */
    static SeqStream<Long> range(long from, long to) {
        return viewOf(Split.range(from, to));
    }

    /**
     * Stream equivalent of {@link Seq#rangeClosed(int, int)}.
     */
    static SeqStream<Integer> rangeClosed(int from, int to) {
        return viewOf(Split.rangeClosed(from, to));
    }

    /**
     * Stream equivalent of {@link Seq#rangeClosed(long, long)}.
     */
    static SeqStream<Long> rangeClosed(long from, long to) {
        return viewOf(Split.rangeClosed(from, to));
    }

    /**
     * Returns an infinite ordered {@code SeqStream} containing the given
     * element repeated indefinitely.
     */
    static <E> SeqStream<E> repeat(E element) {
        return viewOf(Split.repeat(element));
    }

    /**
     * Stream equivalent of {@link Seq#repeat(Object, int)}.
     */
    static <E> SeqStream<E> repeat(E element, int count) {
        Split.requireNonNegativeArgument("count", count);
        return viewOf(Split.repeat(element, count));
    }

    /**
     * Returns an infinite ordered {@code SeqStream} containing the results of
     * calling {@code supplier}, which is called once for each element as the
     * result is traversed.
     */
    static <E> SeqStream<E> generate(Supplier<? extends E> supplier) {
        requireNonNull(supplier);
        return viewOf(Split.generate(supplier));
    }

    /**
     * Stream equivalent of {@link Seq#generate(Supplier, int)}.
     */
    static <E> SeqStream<E> generate(
            Supplier<? extends E> supplier, int count) {
        requireNonNull(supplier);
        Split.requireNonNegativeArgument("count", count);
        return viewOf(Split.generate(supplier, count));
    }

    /**
     * Returns an infinite ordered {@code SeqStream} produced by repeatedly
     * applying {@code operator} to {@code seed}. The first element is
     * {@code seed}, the second is {@code operator.apply(seed)}, and so on.
     */
    static <E> SeqStream<E> iterate(
            E seed, UnaryOperator<E> operator) {
        requireNonNull(operator);
        return viewOf(Split.iterate(seed, operator));
    }

    /**
     * Stream equivalent of {@link Seq#iterate(Object, Predicate,
     * UnaryOperator)}.
     */
    static <E> SeqStream<E> iterate(
            E seed, Predicate<? super E> hasNext, UnaryOperator<E> next) {
        requireNonNull(hasNext);
        requireNonNull(next);
        return viewOf(Split.iterate(seed, hasNext, next));
    }

    /**
     * Stream equivalent of {@link Seq#concat(Iterable...)}.
     * As in {@link Stream#concat(Stream, Stream)}, the result is initially
     * parallel if any input is, and closing it closes every input.
     */
    @SafeVarargs
    static <E> SeqStream<E> concat(
            Stream<? extends E>... streams) {
        requireNonNull(streams);
        Arrays.stream(streams).forEach(Objects::requireNonNull);
        SeqStream<E> result = viewOf(
                Split.concat(Stream::spliterator, streams));
        Arrays.stream(streams).forEach(result::closes);
        return Arrays.stream(streams).anyMatch(Stream::isParallel)
                ? result.parallel()
                : result;
    }

    /**
     * Stream equivalent of {@link Seq#flatten(Iterable)}.
     * The result takes its initial parallel mode from {@code streams} and
     * closing it closes {@code streams} and any inner stream still open.
     * Inner streams are also closed as they are consumed, but closing the
     * result is the only way to close every inner stream in every case, so
     * if inner streams hold resources it is enough, and necessary, to close
     * the result.
     */
    static <E> SeqStream<E> flatten(
            Stream<? extends Stream<? extends E>> streams) {
        requireNonNull(streams);
        Pipeline pipeline = new SeqStreamPipeline();
        Spliterator<E> flattened = Split.flatten(
                streams.spliterator(),
                Stream::spliterator,
                Stream::close,
                pipeline::onClose);
        SeqStream<E> result = viewOf(flattened, pipeline);
        result.closes(streams);
        return streams.isParallel() ? result.parallel() : result;
    }

    Spliterator<E> spliterator();

    /**
     * Returns the state shared by every stage of this stream pipeline.
     */
    Pipeline pipeline();

    SeqStream<E> onClose(Runnable closeHandler);

    /**
     * Stream equivalent of {@link Seq#listEquals(Iterable)}.
     */
    default boolean listEquals(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        return Split.listEquals(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#setEquals(Iterable)}.
     */
    default boolean setEquals(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        return Split.setEquals(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#multisetEquals(Iterable)}.
     */
    default boolean multisetEquals(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        return Split.multisetEquals(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#zip(Iterable, BiFunction)}.
     */
    default <F, R> SeqStream<R> zip(
            Stream<? extends F> that,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        requireNonNull(that);
        requireNonNull(mapper);
        closes(that);
        return viewOf(Split.zip(spliterator(), that.spliterator(), mapper),
                pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#indexes()}.
     */
    default SeqStream<Integer> indexes() {
        return viewOf(Split.indexes(spliterator()), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#intersection(Iterable)}.
     */
    default SeqStream<E> intersection(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        return viewOf(Split.intersection(spliterator(), that.spliterator()),
                pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#difference(Iterable)}.
     */
    default SeqStream<E> difference(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        return viewOf(Split.difference(spliterator(), that.spliterator()),
                pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#union(Iterable)}.
     */
    default SeqStream<E> union(Stream<? extends E> that) {
        requireNonNull(that);
        closes(that);
        return viewOf(Split.union(spliterator(), that.spliterator()),
                pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#sum(Iterable)}.
     */
    default SeqStream<E> sum(Stream<? extends E> that) {
        requireNonNull(that);
        closes(that);
        return viewOf(Split.concat(Stream::spliterator, this, that),
                pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#containsMultiset(Iterable)}.
     */
    default boolean containsMultiset(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        return Split.containsMultiset(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#permutations()}.
     */
    default SeqStream<Seq<E>> permutations() {
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.map(Split.defer(
                () -> Split.<E>permutations(Split.toArray(spliterator)),
                Split.ordered(spliterator)), Seq::viewOf), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#permutations(int)}.
     */
    default SeqStream<Seq<E>> permutations(int k) {
        Split.requireNonNegativeArgument("k", k);
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.map(Split.defer(
                () -> Split.<E>permutations(Split.toArray(spliterator), k),
                Split.ordered(spliterator)), Seq::viewOf), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#allPermutations()}.
     */
    default SeqStream<Seq<E>> allPermutations() {
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.map(Split.defer(
                () -> Split.<E>allPermutations(Split.toArray(spliterator)),
                Split.ordered(spliterator)), Seq::viewOf), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#combinations(int)}.
     */
    default SeqStream<Seq<E>> combinations(int k) {
        Split.requireNonNegativeArgument("k", k);
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.map(Split.defer(
                () -> Split.<E>combinations(Split.toArray(spliterator), k),
                Split.ordered(spliterator)), Seq::viewOf), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#allCombinations()}.
     */
    default SeqStream<Seq<E>> allCombinations() {
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.map(Split.defer(
                () -> Split.<E>allCombinations(Split.toArray(spliterator)),
                Split.ordered(spliterator)), Seq::viewOf), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#power(int)}.
     */
    default SeqStream<Seq<E>> power(int k) {
        Split.requireNonNegativeArgument("k", k);
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.map(Split.defer(
                () -> Split.<E>power(Split.toArray(spliterator), k),
                Split.ordered(spliterator)), Seq::viewOf), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#product(Iterable, BiFunction)}.
     * The {@code Stream} argument must be finite.
     * It is buffered when the result is first traversed.
     */
    default <F, R> SeqStream<R> product(
            Stream<? extends F> that,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        requireNonNull(that);
        requireNonNull(mapper);
        closes(that);
        Spliterator<E> first = spliterator();
        Spliterator<? extends F> second = that.spliterator();
        return viewOf(Split.defer(
                () -> Split.<E, F, R>product(
                        first, Split.toArray(second), mapper),
                Split.ordered(first)), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#slice(int, int)}.
     */
    default SeqStream<E> slice(int from, int to) {
        Split.requireNonNegativeIndex("from", from);
        Split.requireNonNegativeIndex("to", to);
        return viewOf(Split.slice(spliterator(), from, to), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#indexesOfSlice(Iterable)}.
     * The {@code Stream} argument must be finite.
     * It is buffered when the result is first traversed.
     */
    default SeqStream<Integer> indexesOfSlice(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        Spliterator<E> spliterator = spliterator();
        Spliterator<?> slice = that.spliterator();
        return viewOf(Split.defer(
                () -> Split.indexesOfSlice(spliterator, slice),
                Spliterator.ORDERED), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#indexOfSlice(Iterable)}.
     */
    default int indexOfSlice(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        return Split.indexOfSlice(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#lastIndexOfSlice(Iterable)}.
     */
    default int lastIndexOfSlice(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        return Split.lastIndexOfSlice(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#containsSlice(Iterable)}.
     */
    default boolean containsSlice(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        return Split.containsSlice(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#startsWith(Iterable)}.
     */
    default boolean startsWith(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        return Split.startsWith(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#endsWith(Iterable)}.
     */
    default boolean endsWith(Stream<?> that) {
        requireNonNull(that);
        closes(that);
        return Split.endsWith(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#get(int)}.
     */
    default E get(int index) {
        Split.requireNonNegativeIndex("index", index);
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
        return viewOf(Split.indexesOf(spliterator(), object), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#reversed()}.
     */
    default SeqStream<E> reversed() {
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.defer(
                () -> Split.toSpliterator(Split.reversed(spliterator)),
                Split.ordered(spliterator)), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#rotated(int)}.
     */
    default SeqStream<E> rotated(int distance) {
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.defer(
                () -> Split.toSpliterator(
                        Split.rotated(spliterator, distance)),
                Split.ordered(spliterator)), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#shuffled(Random)}.
     */
    default SeqStream<E> shuffled(Random random) {
        requireNonNull(random);
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.defer(
                () -> Split.toSpliterator(
                        Split.shuffled(spliterator, random)),
                Split.ordered(spliterator)), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#size()}.
     * Consider {@link #count()}.
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
        requireNonNull(that);
        closes(that);
        return Split.containsAll(spliterator(), that.spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#limitLast(long)}.
     */
    default SeqStream<E> limitLast(long size) {
        Split.requireNonNegativeArgument("size", size);
        return viewOf(Split.limitLast(spliterator(), size), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#skipLast(long)}.
     */
    default SeqStream<E> skipLast(long size) {
        Split.requireNonNegativeArgument("size", size);
        return viewOf(Split.skipLast(spliterator(), size), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#takeWhile(Predicate)}.
     */
    default SeqStream<E> takeWhile(Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return viewOf(Split.takeWhile(spliterator(), predicate), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#dropWhile(Predicate)}.
     */
    default SeqStream<E> dropWhile(Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return viewOf(Split.dropWhile(spliterator(), predicate), pipeline());
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> filter(Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return viewOf(Split.filter(spliterator(), predicate), pipeline());
    }

    /**
     * {@inheritDoc}
     */
    default <R> SeqStream<R> map(
            Function<? super E, ? extends R> mapper) {
        requireNonNull(mapper);
        return viewOf(Split.map(spliterator(), mapper), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#mapIndexed(BiFunction)}.
     */
    default <R> SeqStream<R> mapIndexed(
            BiFunction<? super Integer, ? super E, ? extends R> mapper) {
        requireNonNull(mapper);
        return viewOf(Split.mapIndexed(spliterator(), mapper), pipeline());
    }

    /**
     * {@inheritDoc}
     */
    default IntStream mapToInt(ToIntFunction<? super E> mapper) {
        requireNonNull(mapper);
        return toStream().mapToInt(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default LongStream mapToLong(ToLongFunction<? super E> mapper) {
        requireNonNull(mapper);
        return toStream().mapToLong(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default DoubleStream mapToDouble(
            ToDoubleFunction<? super E> mapper) {
        requireNonNull(mapper);
        return toStream().mapToDouble(mapper);
    }

    /**
     * {@inheritDoc}
     * As with {@link #flatten}, closing this pipeline closes any mapped
     * stream still open. Mapped streams are also closed as they are
     * consumed, but closing this pipeline is the only way to close every
     * mapped stream in every case, so if mapped streams hold resources it
     * is enough, and necessary, to close this pipeline. This differs from
     * {@link Stream#flatMap}, which closes each mapped stream itself
     * whether traversal completes, short-circuits, is abandoned or throws.
     */
    default <R> SeqStream<R> flatMap(
            Function<? super E, ? extends Stream<? extends R>> mapper) {
        requireNonNull(mapper);
        Pipeline pipeline = pipeline();
        return viewOf(Split.flatMap(
                spliterator(), mapper, Stream::spliterator, Stream::close,
                pipeline::onClose), pipeline);
    }

    /**
     * Stream equivalent of {@link Seq#mapMulti(BiConsumer)}.
     */
    default <R> SeqStream<R> mapMulti(
            BiConsumer<? super E, ? super Consumer<R>> mapper) {
        requireNonNull(mapper);
        return viewOf(Split.mapMulti(spliterator(), mapper), pipeline());
    }

    /**
     * {@inheritDoc}
     */
    default IntStream flatMapToInt(
            Function<? super E, ? extends IntStream> mapper) {
        requireNonNull(mapper);
        return toStream().flatMapToInt(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default LongStream flatMapToLong(
            Function<? super E, ? extends LongStream> mapper) {
        requireNonNull(mapper);
        return toStream().flatMapToLong(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default DoubleStream flatMapToDouble(
            Function<? super E, ? extends DoubleStream> mapper) {
        requireNonNull(mapper);
        return toStream().flatMapToDouble(mapper);
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> distinct() {
        return viewOf(Split.distinct(spliterator()), pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#distinctBy(Function)}.
     */
    default SeqStream<E> distinctBy(Function<? super E, ?> keyMapper) {
        requireNonNull(keyMapper);
        return viewOf(Split.distinctBy(spliterator(), keyMapper), pipeline());
    }

    /**
     * See {@link Seq#groupBy(Function)}.
     */
    default <K> Map<K, Seq<E>> groupBy(
            Function<? super E, ? extends K> keyMapper) {
        requireNonNull(keyMapper);
        return Split.groupBy(spliterator(), keyMapper, Seq::viewOf);
    }

    /**
     * See {@link Seq#groupBy(Function, Function)}.
     */
    default <K, V> Map<K, V> groupBy(
            Function<? super E, ? extends K> keyMapper,
            Function<? super Seq<E>, ? extends V> valueMapper) {
        requireNonNull(keyMapper);
        requireNonNull(valueMapper);
        return Split.groupBy(spliterator(), keyMapper,
                valueMapper.compose(Seq::viewOf));
    }

    /**
     * See {@link Seq#partitionBy(Predicate)}.
     */
    default Map<Boolean, Seq<E>> partitionBy(
            Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return Split.partitionBy(spliterator(), predicate, Seq::viewOf);
    }

    /**
     * See {@link Seq#partitionBy(Predicate, Function)}.
     */
    default <V> Map<Boolean, V> partitionBy(
            Predicate<? super E> predicate,
            Function<? super Seq<E>, ? extends V> valueMapper) {
        requireNonNull(predicate);
        requireNonNull(valueMapper);
        return Split.partitionBy(spliterator(), predicate,
                valueMapper.compose(Seq::viewOf));
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> sorted() {
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.defer(
                () -> Split.toSpliterator(Split.sorted(spliterator)),
                Spliterator.ORDERED), pipeline());
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> sorted(Comparator<? super E> comparator) {
        requireNonNull(comparator);
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.defer(
                () -> Split.toSpliterator(
                        Split.sorted(spliterator, comparator)),
                Spliterator.ORDERED), pipeline());
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> limit(long size) {
        Split.requireNonNegativeArgument("size", size);
        return viewOf(Split.limit(spliterator(), size), pipeline());
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> skip(long size) {
        Split.requireNonNegativeArgument("size", size);
        return viewOf(Split.skip(spliterator(), size), pipeline());
    }

    /**
     * {@inheritDoc}
     */
    default void forEach(Consumer<? super E> action) {
        requireNonNull(action);
        spliterator().forEachRemaining(action);
    }

    /**
     * {@inheritDoc}
     */
    default void forEachOrdered(Consumer<? super E> action) {
        requireNonNull(action);
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
        requireNonNull(generator);
        return Split.toArray(spliterator(), generator);
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> reduce(BinaryOperator<E> accumulator) {
        requireNonNull(accumulator);
        return Split.reduce(spliterator(), accumulator);
    }

    /**
     * {@inheritDoc}
     */
    default E reduce(E identity, BinaryOperator<E> accumulator) {
        requireNonNull(accumulator);
        return Split.reduce(spliterator(), identity, accumulator);
    }

    /**
     * Stream equivalent of {@link Seq#reduce(Object, BiFunction)}.
     */
    default <U> U reduce(
            U identity,
            BiFunction<U, ? super E, U> accumulator) {
        requireNonNull(accumulator);
        return Split.reduce(spliterator(), identity, accumulator);
    }

    /**
     * {@inheritDoc}
     */
    default <U> U reduce(
            U identity,
            BiFunction<U, ? super E, U> accumulator,
            BinaryOperator<U> ignored) {
        requireNonNull(accumulator);
        requireNonNull(ignored);
        return Split.reduce(spliterator(), identity, accumulator);
    }

    /**
     * Equivalent to {@code collect(Seq.toSeq())}.
     */
    default Seq<E> toSeq() {
        return Seq.copyOf(spliterator());
    }

    /**
     * Returns an ordinary {@code Stream} over the same elements, which
     * supports parallel evaluation unlike this {@code SeqStream}. It takes
     * this pipeline's current parallel mode and closes this pipeline when
     * it is closed.
     */
    default Stream<E> toStream() {
        return StreamSupport.stream(spliterator(), isParallel())
                .onClose(this::close);
    }

    /**
     * See {@link Seq#toOptional()}.
     */
    default Optional<E> toOptional() {
        return Split.toOptional(spliterator());
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
        requireNonNull(keyMapper);
        return Split.toMap(spliterator(), keyMapper);
    }

    /**
     * See {@link Seq#toMap(Function, Function)}.
     */
    default <K, V> Map<K, V> toMap(
            Function<? super E, ? extends K> keyMapper,
            Function<? super E, ? extends V> valueMapper) {
        requireNonNull(keyMapper);
        requireNonNull(valueMapper);
        return Split.toMap(spliterator(), keyMapper, valueMapper);
    }

    /**
     * See {@link Seq#toMap(Function, Function, BinaryOperator)}.
     */
    default <K, V> Map<K, V> toMap(
            Function<? super E, ? extends K> keyMapper,
            Function<? super E, ? extends V> valueMapper,
            BinaryOperator<V> mergeFunction) {
        requireNonNull(keyMapper);
        requireNonNull(valueMapper);
        requireNonNull(mergeFunction);
        return Split.toMap(
                spliterator(), keyMapper, valueMapper, mergeFunction);
    }

    /**
     * Stream equivalent of {@link Seq#collect(Supplier, BiConsumer)}.
     */
    default <U> U collect(
            Supplier<U> supplier,
            BiConsumer<U, ? super E> accumulator) {
        requireNonNull(supplier);
        requireNonNull(accumulator);
        return Split.collect(spliterator(), supplier, accumulator);
    }

    /**
     * {@inheritDoc}
     */
    default <R> R collect(
            Supplier<R> supplier,
            BiConsumer<R, ? super E> accumulator,
            BiConsumer<R, R> ignored) {
        requireNonNull(supplier);
        requireNonNull(accumulator);
        requireNonNull(ignored);
        return Split.collect(spliterator(), supplier, accumulator);
    }

    /**
     * {@inheritDoc}
     */
    default <R, A> R collect(Collector<? super E, A, R> collector) {
        requireNonNull(collector);
        return Split.collect(spliterator(), collector);
    }

    /**
     * Stream equivalent of {@link Seq#collectWhile(Supplier, BiPredicate)}.
     */
    default <U> U collectWhile(
            Supplier<U> supplier,
            BiPredicate<U, ? super E> accumulator) {
        requireNonNull(supplier);
        requireNonNull(accumulator);
        return Split.collectWhile(spliterator(), supplier, accumulator);
    }

    /**
     * Stream equivalent of {@link Seq#sumOfInt(ToIntFunction)}.
     */
    default int sumOfInt(ToIntFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Split.sumOfInt(spliterator(), mapper);
    }

    /**
     * Stream equivalent of {@link Seq#sumOfLong(ToLongFunction)}.
     */
    default long sumOfLong(ToLongFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Split.sumOfLong(spliterator(), mapper);
    }

    /**
     * Stream equivalent of {@link Seq#sumOfDouble(ToDoubleFunction)}.
     */
    default double sumOfDouble(ToDoubleFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Split.sumOfDouble(spliterator(), mapper);
    }

    /**
     * Stream equivalent of {@link Seq#productOfInt(ToIntFunction)}.
     */
    default int productOfInt(ToIntFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Split.productOfInt(spliterator(), mapper);
    }

    /**
     * Stream equivalent of {@link Seq#productOfLong(ToLongFunction)}.
     */
    default long productOfLong(ToLongFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Split.productOfLong(spliterator(), mapper);
    }

    /**
     * Stream equivalent of {@link Seq#productOfDouble(ToDoubleFunction)}.
     */
    default double productOfDouble(ToDoubleFunction<? super E> mapper) {
        requireNonNull(mapper);
        return Split.productOfDouble(spliterator(), mapper);
    }

    /**
     * Stream equivalent of {@link Seq#min()}.
     */
    default Optional<E> min() {
        return Split.min(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> min(Comparator<? super E> comparator) {
        requireNonNull(comparator);
        return Split.min(spliterator(), comparator);
    }

    /**
     * Stream equivalent of {@link Seq#max()}.
     */
    default Optional<E> max() {
        return Split.max(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    default Optional<E> max(Comparator<? super E> comparator) {
        requireNonNull(comparator);
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
        requireNonNull(predicate);
        return Split.anyMatch(spliterator(), predicate);
    }

    /**
     * {@inheritDoc}
     */
    default boolean allMatch(Predicate<? super E> predicate) {
        requireNonNull(predicate);
        return Split.allMatch(spliterator(), predicate);
    }

    /**
     * {@inheritDoc}
     */
    default boolean noneMatch(Predicate<? super E> predicate) {
        requireNonNull(predicate);
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
     * Stream equivalent of {@link Seq#getFirst()}.
     */
    default E getFirst() {
        return Split.getFirst(spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#getLast()}.
     */
    default E getLast() {
        return Split.getLast(spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#getOnly()}.
     */
    default E getOnly() {
        return Split.getOnly(spliterator());
    }

    /**
     * Stream equivalent of {@link Seq#windowFixed(int)}.
     */
    default SeqStream<Seq<E>> windowFixed(int size) {
        Split.requirePositiveArgument("size", size);
        return viewOf(Split.map(
                Split.windowFixed(spliterator(), size), Seq::viewOf),
                pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#windowSliding(int)}.
     */
    default SeqStream<Seq<E>> windowSliding(int size) {
        Split.requirePositiveArgument("size", size);
        return viewOf(Split.map(
                Split.windowSliding(spliterator(), size), Seq::viewOf),
                pipeline());
    }

    /**
     * Stream equivalent of {@link Seq#scan(Supplier, BiFunction)}.
     * The initial supplier is invoked when the result is first traversed.
     */
    default <R> SeqStream<R> scan(
            Supplier<R> initial,
            BiFunction<? super R, ? super E, ? extends R> scanner) {
        requireNonNull(initial);
        requireNonNull(scanner);
        Spliterator<E> spliterator = spliterator();
        return viewOf(Split.defer(
                () -> Split.scan(spliterator, initial, scanner),
                Split.ordered(spliterator)), pipeline());
    }

    /**
     * {@inheritDoc}
     */
    default SeqStream<E> peek(Consumer<? super E> action) {
        requireNonNull(action);
        return viewOf(Split.peek(spliterator(), action), pipeline());
    }

    /**
     * Stream equivalent of
     * {@link Seq#toString(CharSequence, CharSequence, CharSequence)}.
     */
    default String toString(
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        return Split.toString(spliterator(), delimiter, prefix, suffix);
    }

    default Iterator<E> iterator() {
        return Spliterators.iterator(spliterator());
    }

    default boolean isParallel() {
        return pipeline().isParallel();
    }

    default SeqStream<E> sequential() {
        pipeline().setParallel(false);
        return this;
    }

    /**
     * Puts this pipeline into parallel mode and returns {@code this}.
     * {@code SeqStream} intentionally does not implement parallel
     * evaluation: its own operations always run on the calling thread,
     * whatever the mode. Parallel mode is tracked so that
     * {@code isParallel()} is consistent and so that the mode survives
     * conversion to and from {@code Stream}: {@link #toStream} and the
     * primitive bridges such as {@link #mapToInt} hand off to an ordinary
     * {@code Stream} pipeline, which does evaluate in parallel when the
     * mode is parallel.
     */
    default SeqStream<E> parallel() {
        pipeline().setParallel(true);
        return this;
    }

    default SeqStream<E> unordered() {
        return viewOf(Split.unordered(spliterator()), pipeline());
    }

    default void close() {
        pipeline().close();
    }

    /**
     * Registers the given stream to be closed when this pipeline is closed
     * and returns {@code this}. This method does not traverse or close the
     * given stream.
     */
    default SeqStream<E> closes(Stream<?> stream) {
        return onClose(stream::close);
    }

    /**
     * State shared by all stages of one {@code SeqStream} pipeline. Each
     * method here is the pipeline-wide operation behind the like-named
     * {@code SeqStream} method and is documented there.
     */
    interface Pipeline {

        /**
         * See {@link SeqStream#isParallel}.
         */
        boolean isParallel();

        /**
         * See {@link SeqStream#parallel} and {@link SeqStream#sequential}.
         */
        void setParallel(boolean parallel);

        /**
         * Returns whether {@link SeqStream#close} has been called.
         */
        boolean isClosed();

        /**
         * See {@link SeqStream#onClose}.
         */
        void onClose(Runnable closeHandler);

        /**
         * See {@link SeqStream#close}.
         */
        void close();
    }

    /**
     * A builder for creating {@link SeqStream} instances.
     */
    interface Builder<E> extends Stream.Builder<E> {

        /**
         * Adds the element.
         */
        void accept(E element);

        /**
         * Builds the {@link SeqStream} instance.
         * An {@link IllegalStateException} is thrown on further attempts to
         * operate on this builder.
         */
        SeqStream<E> build();

        /**
         * Adds the element and returns {@code this}.
         */
        default Builder<E> add(E element) {
            accept(element);
            return this;
        }
    }
}
