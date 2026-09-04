package io.github.eagerseq;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static io.github.eagerseq.SeqTest.assertThrows;
import static java.util.function.Function.identity;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SeqStreamTest {

    @Test
    public void testOf() {
        assertTrue(SeqStream.of().isEmpty());
        assertTrue(SeqStream.of(0).listEquals(streamOf(0)));
        assertTrue(SeqStream.of(0, 1, 2, null)
                .listEquals(streamOf(0, 1, 2, null)));
        Integer[] elements = {0, 1};
        SeqStream<Integer> stream = SeqStream.of(elements);
        elements[0] = 2;
        assertTrue(stream.listEquals(streamOf(0, 1)));
        assertThrows(() -> SeqStream.of((Object[]) null));
    }

    @Test
    public void testOfNullable() {
        assertTrue(SeqStream.ofNullable(0).listEquals(streamOf(0)));
        assertTrue(SeqStream.ofNullable(null).isEmpty());
    }

    @Test
    public void testRange() {
        assertTrue(SeqStream.range(4, 7).listEquals(streamOf(4, 5, 6)));
        assertTrue(SeqStream.range(7, 4).isEmpty());
        assertTrue(SeqStream.range(-12, -10).listEquals(streamOf(-12, -11)));
        assertTrue(SeqStream.range(4L, 7L).listEquals(streamOf(4L, 5L, 6L)));
        assertTrue(SeqStream.range(7L, 4L).isEmpty());
        assertTrue(SeqStream.range(-12L, -10L)
                .listEquals(streamOf(-12L, -11L)));
    }

    @Test
    public void testRangeClosed() {
        assertTrue(SeqStream.rangeClosed(4, 7)
                .listEquals(streamOf(4, 5, 6, 7)));
        assertTrue(SeqStream.rangeClosed(4, 4).listEquals(streamOf(4)));
        assertTrue(SeqStream.rangeClosed(7, 4).isEmpty());
        assertTrue(SeqStream.rangeClosed(Integer.MAX_VALUE - 1,
                Integer.MAX_VALUE).listEquals(
                        streamOf(Integer.MAX_VALUE - 1, Integer.MAX_VALUE)));
        assertTrue(SeqStream.rangeClosed(4L, 7L)
                .listEquals(streamOf(4L, 5L, 6L, 7L)));
        assertTrue(SeqStream.rangeClosed(4L, 4L).listEquals(streamOf(4L)));
        assertTrue(SeqStream.rangeClosed(7L, 4L).isEmpty());
        assertTrue(SeqStream.rangeClosed(Long.MAX_VALUE - 1, Long.MAX_VALUE)
                .listEquals(streamOf(Long.MAX_VALUE - 1, Long.MAX_VALUE)));
    }

    @Test(timeout = 5000)
    public void testRepeat() {
        Spliterator<String> spliterator = SeqStream.repeat("a").spliterator();
        assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED));
        assertFalse(spliterator.hasCharacteristics(Spliterator.SIZED));

        assertThat(SeqStream.repeat("a").limit(3).toSeq(),
                equalTo(Seq.of("a", "a", "a")));
        assertThat(SeqStream.repeat("a", 3).toSeq(),
                equalTo(Seq.of("a", "a", "a")));
        assertTrue(SeqStream.repeat("a", 0).isEmpty());
        assertThat(SeqStream.repeat(null, 2).toSeq(),
                equalTo(Seq.of(null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> SeqStream.repeat("a", -1));
    }

    @Test(timeout = 5000)
    public void testGenerate() {
        int[] calls = new int[1];
        assertThat(SeqStream.generate(() -> calls[0]++).limit(3).toSeq(),
                equalTo(Seq.of(0, 1, 2)));
        assertThat(calls[0], equalTo(3));

        calls[0] = 0;
        SeqStream<Integer> deferred = SeqStream.generate(() -> calls[0]++, 3);
        assertThat(calls[0], equalTo(0));
        assertThat(deferred.toSeq(), equalTo(Seq.of(0, 1, 2)));
        assertThat(calls[0], equalTo(3));

        assertTrue(SeqStream.generate(() -> "a", 0).isEmpty());
        assertThrows(() -> SeqStream.generate(null));
        assertThrows(() -> SeqStream.generate(null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> SeqStream.generate(() -> "a", -1));
    }

    @Test(timeout = 5000)
    public void testIterateWithHasNext() {
        assertThat(SeqStream.iterate(1, n -> n < 20, n -> n * 2).toSeq(),
                equalTo(Seq.of(1, 2, 4, 8, 16)));
        assertTrue(SeqStream.iterate(1, n -> false, n -> n * 2).isEmpty());

        Spliterator<Integer> spliterator = SeqStream
                .iterate(0, n -> n < 1, n -> n + 1).spliterator();
        assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED));
        assertFalse(spliterator.hasCharacteristics(Spliterator.SIZED));
        assertTrue(spliterator.tryAdvance(e -> {}));
        assertFalse(spliterator.tryAdvance(e -> {}));
        assertFalse(spliterator.tryAdvance(e -> {}));

        assertThrows(() -> SeqStream.iterate(0, null, n -> n));
        assertThrows(() -> SeqStream.iterate(0, n -> true, null));
    }

    @Test(timeout = 5000)
    public void testIterate() {
        Spliterator<Integer> spliterator = SeqStream.iterate(0, n -> n + 1)
                .spliterator();
        assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED));
        assertFalse(spliterator.hasCharacteristics(Spliterator.SIZED));

        int[] applications = new int[1];
        assertThat(SeqStream.iterate(1, n -> {
            applications[0]++;
            return n * 2;
        }).limit(4).toSeq(),
                equalTo(Seq.of(1, 2, 4, 8)));
        assertThat(applications[0], equalTo(3));

        assertThat(SeqStream.iterate(null, ignored -> "next")
                .limit(3).toSeq(),
                equalTo(Seq.of(null, "next", "next")));
        assertThrows(() -> SeqStream.iterate(0, null));
    }

    @Test(timeout = 5000)
    public void testProductWithInfiniteReceiver() {
        assertThat(SeqStream.iterate(0, i -> i + 1)
                .product(
                        SeqStream.of("x", "y"), (i, s) -> i + s)
                .limit(4).toSeq(),
                equalTo(Seq.of("0x", "0y", "1x", "1y")));

        assertTrue(SeqStream.iterate(0, i -> i + 1)
                .product(SeqStream.of(), Integer::sum)
                .isEmpty());
    }

    @Test
    public void testConcat() {
        assertTrue(SeqStream.concat().isEmpty());
        assertTrue(
                SeqStream.concat(streamOf(0, 1))
                        .listEquals(streamOf(0, 1)));
        assertTrue(
                SeqStream.concat(streamOf(0, 1), streamOf(2, 3))
                        .listEquals(streamOf(0, 1, 2, 3)));
        assertTrue(
                SeqStream.concat(streamOf(0, 1), streamOf(2, 3), streamOf(4, 5))
                        .listEquals(streamOf(0, 1, 2, 3, 4, 5)));
        SeqStream<Integer> beforeNull = streamOf(0);
        assertNullRejected(() -> SeqStream.concat(beforeNull, null));
        assertThat(beforeNull.toSeq(), equalTo(Seq.of(0)));
        assertNullRejected(() -> SeqStream.concat(null, streamOf()));

        int[] traversed = {0};
        SeqStream<Integer> first = streamOf(0, 1)
                .peek(ignored -> traversed[0]++);
        SeqStream<Integer> second = streamOf(2, 3)
                .peek(ignored -> traversed[0]++);
        SeqStream<Integer> concatenated = SeqStream.concat(first, second);
        assertThat(traversed[0], equalTo(0));
        assertConsumed(first::count);
        assertConsumed(second::count);
        assertThat(concatenated.toSeq(), equalTo(Seq.of(0, 1, 2, 3)));
        assertThat(traversed[0], equalTo(4));
    }

    @Test
    public void testFlatten() {
        assertTrue(SeqStream.flatten(streamOf()).isEmpty());
        assertTrue(
                SeqStream.flatten(streamOf(streamOf(0, 1)))
                        .listEquals(streamOf(0, 1)));
        assertTrue(
                SeqStream.flatten(streamOf(streamOf(0, 1), streamOf(2, 3)))
                        .listEquals(streamOf(0, 1, 2, 3)));
        assertTrue(
                SeqStream.flatten(streamOf(streamOf(0, 1), streamOf(2, 3),
                        streamOf(4, 5)))
                        .listEquals(streamOf(0, 1, 2, 3, 4, 5)));
        assertThat(SeqStream.flatten(
                streamOf(streamOf(0), null, streamOf(1))).toSeq(),
                equalTo(Seq.of(0, 1)));
        assertNullRejected(() -> SeqStream.flatten(null));
    }

    @Test
    public void testReduce() {
        assertThat(streamOf(4, 6, 11).reduce(0, Integer::sum, Integer::sum),
                equalTo(21));
    }

    @Test
    public void testCollect() {
        assertThat(streamOf(4, 6, 11).collect(
                () -> new int[1],
                (a, b) -> a[0] += b,
                (a, b) -> a[0] += b[0])[0],
                equalTo(21));
    }

    @Test
    public void testIsParallel() {
        assertFalse(streamOf(0).isParallel());
        assertFalse(streamOf(0).parallel().isParallel());
    }

    @Test
    public void testParallel() {
        SeqStream<Integer> stream = streamOf(0);
        assertThat(stream.parallel(), sameInstance(stream));
    }

    @Test
    public void testSequential() {
        SeqStream<Integer> stream = streamOf(0);
        assertThat(stream.sequential(), sameInstance(stream));
    }

    @Test
    public void testUnordered() {
        Spliterator<Integer> spliterator = streamOf(0)
                .unordered().spliterator();
        assertFalse(spliterator.hasCharacteristics(Spliterator.ORDERED));
        assertTrue(spliterator.tryAdvance(n -> assertThat(n, equalTo(0))));
    }

    @Test
    public void testOnClose() {
        assertThrows(() -> streamOf().onClose(() -> {}));
    }

    @Test
    public void testClose() {
        streamOf().close();
    }

    @Test
    public void testMapToInt() {
        assertArrayEquals(new int[]{0, 1, 2},
                streamOf(0, 1, 2).mapToInt(i -> i).toArray());
    }

    @Test
    public void testMapToLong() {
        assertArrayEquals(new long[]{0, 1, 2},
                streamOf(0, 1, 2).mapToLong(i -> i).toArray());
    }

    @Test
    public void testMapToDouble() {
        assertArrayEquals(new double[]{0, 1, 2},
                streamOf(0, 1, 2).mapToDouble(i -> i).toArray(), 1e-12);
    }

    @Test
    public void testFlatMapToInt() {
        assertArrayEquals(new int[]{0, 1, 2},
                streamOf(0, 1, 2).flatMapToInt(IntStream::of).toArray());
    }

    @Test
    public void testFlatMapToLong() {
        assertArrayEquals(new long[]{0, 1, 2},
                streamOf(0, 1, 2).flatMapToLong(LongStream::of).toArray());
    }

    @Test
    public void testFlatMapToDouble() {
        assertArrayEquals(new double[]{0, 1, 2},
                streamOf(0, 1, 2).flatMapToDouble(DoubleStream::of).toArray(),
                1e-12);
    }

    @Test(timeout = 5000)
    public void testGetRejectsNegativeIndexWithoutTraversing() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> SeqStream.iterate(0, i -> i + 1).get(-1));
    }

    @Test(timeout = 5000)
    public void testInfiniteSourceSupportsFinitePrefixOperations() {
        assertThat(SeqStream.iterate(0, i -> i + 1)
                .slice(3, 6).toSeq(),
                equalTo(Seq.of(3, 4, 5)));
        assertThat(SeqStream.iterate(0, i -> i + 1)
                .indexes().limit(4).toSeq(),
                equalTo(Seq.of(0, 1, 2, 3)));
        assertThat(SeqStream.iterate(0, i -> i + 1)
                .skipLast(2).limit(3).toSeq(),
                equalTo(Seq.of(0, 1, 2)));
        assertThat(SeqStream.iterate(0, i -> i + 1)
                .takeWhile(i -> i < 3).toSeq(),
                equalTo(Seq.of(0, 1, 2)));
        assertThat(SeqStream.iterate(0, i -> i + 1)
                .dropWhile(i -> i < 3).limit(3).toSeq(),
                equalTo(Seq.of(3, 4, 5)));
    }

    @Test(timeout = 5000)
    public void testInfiniteSourceSupportsShortCircuitingTerminals() {
        assertThat(SeqStream.iterate(0, i -> i + 1).get(5), equalTo(5));
        assertThat(SeqStream.iterate(0, i -> i + 1).indexOf(5), equalTo(5));
        assertTrue(SeqStream.iterate(0, i -> i + 1).contains(5));
        assertTrue(SeqStream.iterate(0, i -> i + 1).anyMatch(i -> i == 5));
        assertFalse(SeqStream.iterate(0, i -> i + 1).allMatch(i -> i < 5));
        assertFalse(SeqStream.iterate(0, i -> i + 1).noneMatch(i -> i == 5));
        assertTrue(SeqStream.iterate(0, i -> i + 1)
                .startsWith(streamOf(0, 1, 2)));
        assertTrue(SeqStream.iterate(0, i -> i + 1)
                .containsSlice(streamOf(4, 5, 6)));
    }

    @Test(timeout = 5000)
    public void testIntermediateOperationsAreLazy() {
        int[] traversed = new int[1];
        SeqStream<Integer> result = SeqStream.iterate(0, i -> i + 1)
                .peek(i -> traversed[0]++)
                .filter(i -> i % 2 == 0)
                .map(i -> i + 1)
                .flatMap(i -> SeqStream.of(i, i))
                .distinct()
                .skip(1)
                .limit(3);

        assertThat(traversed[0], equalTo(0));
        assertThat(result.toSeq(), equalTo(Seq.of(3, 5, 7)));
        assertThat(traversed[0], equalTo(7));
    }

    @Test
    public void testWholeSourceIntermediateOperationsAreLazy() {
        for (DeferredOperation operation : deferredOperations()) {
            assertDeferred(operation);
        }
    }

    @Test
    public void testWholeSourceIntermediateOperationOrder() {
        for (DeferredOperation operation : deferredOperations()) {
            assertTrue(operation.name, isOrdered(
                    operation.apply(SeqStream.of(0, 1))));
            assertThat(operation.name, isOrdered(
                    operation.apply(SeqStream.of(0, 1).unordered())),
                    equalTo(operation.establishesOrder));
        }
    }

    private static boolean isOrdered(SeqStream<?> stream) {
        return stream.spliterator().hasCharacteristics(Spliterator.ORDERED);
    }

    private static void assertDeferred(DeferredOperation operation) {
        for (Function<SeqStream<?>, BooleanSupplier> cursorFactory : deferredCursors()) {
            int[] traversed = new int[1];
            SeqStream<?> result = operation.apply(
                    SeqStream.of(1, 0).peek(i -> traversed[0]++));

            assertThat(operation.name, traversed[0], equalTo(0));
            BooleanSupplier advance = cursorFactory.apply(result);
            assertThat(operation.name, traversed[0], equalTo(0));
            assertTrue(operation.name, advance.getAsBoolean());
            assertThat(operation.name, traversed[0], equalTo(2));
        }
    }

    private static List<Function<SeqStream<?>, BooleanSupplier>> deferredCursors() {
        return Arrays.asList(
                stream -> {
                    Spliterator<?> spliterator = stream.spliterator();
                    return () -> spliterator.tryAdvance(element -> {});
                },
                stream -> {
                    Iterator<?> iterator = stream.iterator();
                    return () -> {
                        if (!iterator.hasNext()) return false;
                        iterator.next();
                        return true;
                    };
                });
    }

    private static List<DeferredOperation> deferredOperations() {
        return Arrays.asList(
                new DeferredOperation("sorted()", true, SeqStream::sorted),
                new DeferredOperation("sorted(comparator)", true,
                        stream -> stream.sorted(Integer::compareTo)),
                new DeferredOperation("reversed()", false,
                        SeqStream::reversed),
                new DeferredOperation("rotated(distance)", false,
                        stream -> stream.rotated(1)),
                new DeferredOperation("shuffled(random)", false,
                        stream -> stream.shuffled(new Random(0))),
                new DeferredOperation("permutations()", false,
                        SeqStream::permutations),
                new DeferredOperation("permutations(k)", false,
                        stream -> stream.permutations(1)),
                new DeferredOperation("allPermutations()", false,
                        SeqStream::allPermutations),
                new DeferredOperation("combinations(k)", false,
                        stream -> stream.combinations(1)),
                new DeferredOperation("allCombinations()", false,
                        SeqStream::allCombinations),
                new DeferredOperation("power(k)", false,
                        stream -> stream.power(1)));
    }

    private static final class DeferredOperation {
        private final String name;
        private final boolean establishesOrder;
        private final Function<SeqStream<Integer>, SeqStream<?>> function;

        DeferredOperation(
                String name,
                boolean establishesOrder,
                Function<SeqStream<Integer>, SeqStream<?>> function) {
            this.name = name;
            this.establishesOrder = establishesOrder;
            this.function = function;
        }

        SeqStream<?> apply(SeqStream<Integer> stream) {
            return function.apply(stream);
        }
    }

    @Test
    public void testNextLength() {
        assertThat(SeqBuilder.nextLength(Integer.MAX_VALUE / 4 * 3),
                equalTo(Integer.MAX_VALUE - 8));
        try {
            SeqBuilder.nextLength(Integer.MAX_VALUE - 4);
            fail("expected OutOfMemoryError");
        } catch (OutOfMemoryError expected) {
            assertThat(expected.getMessage(),
                    equalTo("Seq size exceeds the maximum array length"));
        }
    }

    @Test
    public void testRequireNonNegativeArgument() {
        Split.requireNonNegativeArgument("size", 0);
        Split.requireNonNegativeArgument("size", Long.MAX_VALUE);
        try {
            Split.requireNonNegativeArgument("size", -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(),
                    equalTo("size -1 was negative"));
        }
        try {
            Split.requireNonNegativeArgument("size", Long.MIN_VALUE);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(),
                    equalTo("size -9223372036854775808 was negative"));
        }
    }

    @Test
    public void testRequireNonNegativeIndex() {
        Split.requireNonNegativeIndex("from", 0);
        Split.requireNonNegativeIndex("from", Integer.MAX_VALUE);
        try {
            Split.requireNonNegativeIndex("from", -1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            assertThat(expected.getMessage(), equalTo("from -1 was negative"));
        }
        try {
            Split.requireNonNegativeIndex("from", Integer.MIN_VALUE);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            assertThat(expected.getMessage(),
                    equalTo("from -2147483648 was negative"));
        }
    }

    @Test
    public void testTerminalOperationAfterTerminalOperation() {
        SeqStream<Integer> stream = streamOf(0, 1, 2);
        assertThat(stream.toSeq(), equalTo(Seq.of(0, 1, 2)));
        assertConsumed(stream::toSeq);
    }

    @Test
    public void testIntermediateOperationAfterTerminalOperation() {
        SeqStream<Integer> stream = streamOf(0, 1, 2);
        stream.count();
        assertConsumed(() -> stream.filter(e -> true));
    }

    @Test
    public void testTerminalOperationAfterIntermediateOperation() {
        SeqStream<Integer> stream = streamOf(0, 1, 2);
        SeqStream<Integer> filtered = stream.filter(e -> true);
        assertConsumed(stream::toSeq);
        assertThat(filtered.toSeq(), equalTo(Seq.of(0, 1, 2)));
    }

    @Test
    public void testShortCircuitedStreamIsAlsoConsumed() {
        SeqStream<Integer> stream = streamOf(0, 1, 2);
        stream.findFirst();
        assertConsumed(stream::toSeq);
    }

    @Test
    public void testFindAny() {
        assertThat(streamOf().findAny(), equalTo(java.util.Optional.empty()));
        assertThat(streamOf(0, 1, 2).findAny(),
                equalTo(java.util.Optional.of(0)));
    }

    @Test
    public void testSeqIsReusableAfterItsStreamIsConsumed() {
        Seq<Integer> seq = Seq.of(0, 1, 2);
        assertThat(seq.stream().toSeq(), equalTo(seq));
        assertThat(seq.stream().toSeq(), equalTo(seq));
    }

    @Test
    public void testViewOfRejectsNullSpliterator() {
        assertThrows(() -> SeqStream.viewOf((Spliterator<Object>) null));
    }

    /**
     * The {@code SeqStream} half of
     * {@link SeqTest#testNullFunctionalArgumentsAreRejectedOnAnEmptySeq}.
     */
    @Test
    public void testNullFunctionalArgumentsAreRejectedOnAnEmptyStream() {
        assertNullRejected(() -> emptyStream().filter(null));
        assertNullRejected(() -> emptyStream().map(null));
        assertNullRejected(() -> emptyStream().flatMap(null));
        assertNullRejected(() -> emptyStream().mapMulti(null));
        assertNullRejected(() -> emptyStream().mapToInt(null));
        assertNullRejected(() -> emptyStream().mapToLong(null));
        assertNullRejected(() -> emptyStream().mapToDouble(null));
        assertNullRejected(() -> emptyStream().flatMapToInt(null));
        assertNullRejected(() -> emptyStream().flatMapToLong(null));
        assertNullRejected(() -> emptyStream().flatMapToDouble(null));
        assertNullRejected(() -> emptyStream().takeWhile(null));
        assertNullRejected(() -> emptyStream().dropWhile(null));
        assertNullRejected(() -> emptyStream().peek(null));
        assertNullRejected(() -> emptyStream().forEach(null));
        assertNullRejected(() -> emptyStream().forEachOrdered(null));
        assertNullRejected(() -> emptyStream().distinctBy(null));
        assertNullRejected(() -> emptyStream().groupBy(null));
        assertNullRejected(() -> emptyStream().groupBy(null, Seq::size));
        assertNullRejected(() -> emptyStream().groupBy(e -> e, null));
        assertNullRejected(() -> emptyStream().sorted(null));
        assertNullRejected(() -> emptyStream().shuffled(null));
        assertNullRejected(() -> emptyStream().min(null));
        assertNullRejected(() -> emptyStream().max(null));
        assertNullRejected(() -> emptyStream().reduce(null));
        assertNullRejected(
                () -> emptyStream().reduce(0, (BinaryOperator<Integer>) null));
        assertNullRejected(() -> emptyStream()
                .reduce(0, (BiFunction<Integer, Integer, Integer>) null));
        assertNullRejected(() -> emptyStream()
                .reduce(0, null, (BinaryOperator<Integer>) null));
        assertNullRejected(() -> emptyStream()
                .reduce(0, Integer::sum, null));
        assertNullRejected(() -> emptyStream().collect(ArrayList::new, null));
        assertNullRejected(
                () -> emptyStream().<List<Integer>>collect(null, List::add));
        assertNullRejected(() -> emptyStream().collect(ArrayList::new, null,
                (a, b) -> {}));
        assertNullRejected(
                () -> emptyStream().collect(ArrayList::new, List::add, null));
        assertNullRejected(() -> emptyStream().collect(null));
        assertNullRejected(() -> emptyStream().sumOfInt(null));
        assertNullRejected(() -> emptyStream().sumOfLong(null));
        assertNullRejected(() -> emptyStream().sumOfDouble(null));
        assertNullRejected(() -> emptyStream().productOfInt(null));
        assertNullRejected(() -> emptyStream().productOfLong(null));
        assertNullRejected(() -> emptyStream().productOfDouble(null));
        assertNullRejected(() -> emptyStream().anyMatch(null));
        assertNullRejected(() -> emptyStream().allMatch(null));
        assertNullRejected(() -> emptyStream().noneMatch(null));
        assertNullRejected(
                () -> emptyStream().toArray((IntFunction<Integer[]>) null));
        assertNullRejected(() -> emptyStream().toMap(null));
        assertNullRejected(() -> emptyStream().toMap(null, identity()));
        assertNullRejected(() -> emptyStream().toMap(identity(), null));
        assertNullRejected(
                () -> emptyStream().toMap(null, identity(), (a, b) -> a));
        assertNullRejected(
                () -> emptyStream().toMap(identity(), null, (a, b) -> a));
        assertNullRejected(
                () -> emptyStream().toMap(identity(), identity(), null));
        assertNullRejected(() -> emptyStream().zip(emptyStream(), null));
        assertNullRejected(() -> emptyStream().product(emptyStream(), null));
        // sorted(), min() and max() are separate overloads that take no
        // comparator.
        assertTrue(emptyStream().sorted().isEmpty());
        assertThat(emptyStream().min(), equalTo(Optional.empty()));
        assertThat(emptyStream().max(), equalTo(Optional.empty()));
    }

    @Test
    public void testInvalidIntermediateArgumentsDoNotClaimStream() {
        SeqStream<Integer> nullArgument = emptyStream();
        assertNullRejected(() -> nullArgument.filter(null));
        assertTrue(nullArgument.isEmpty());

        for (Function<SeqStream<Integer>, ?> operation : Arrays
                .<Function<SeqStream<Integer>, ?>>asList(
                        stream -> stream.permutations(-1),
                        stream -> stream.combinations(-1),
                        stream -> stream.power(-1))) {
            SeqStream<Integer> stream = emptyStream();
            assertThrows(IllegalArgumentException.class,
                    () -> operation.apply(stream));
            assertTrue(stream.isEmpty());
        }
    }

    @Test
    public void testNullSecondarySourcesDoNotClaimStream() {
        for (Function<SeqStream<Integer>, ?> operation : Arrays
                .<Function<SeqStream<Integer>, ?>>asList(
                        stream -> stream.listEquals(null),
                        stream -> stream.setEquals(null),
                        stream -> stream.multisetEquals(null),
                        stream -> stream.zip(null, Integer::sum),
                        stream -> stream.intersection(null),
                        stream -> stream.difference(null),
                        stream -> stream.union(null),
                        stream -> stream.sum(null),
                        stream -> stream.containsMultiset(null),
                        stream -> stream.product(null, Integer::sum),
                        stream -> stream.indexesOfSlice(null),
                        stream -> stream.indexOfSlice(null),
                        stream -> stream.lastIndexOfSlice(null),
                        stream -> stream.containsSlice(null),
                        stream -> stream.startsWith(null),
                        stream -> stream.endsWith(null),
                        stream -> stream.containsAll(null))) {
            SeqStream<Integer> stream = streamOf(0);
            assertNullRejected(() -> operation.apply(stream));
            assertThat(stream.toSeq(), equalTo(Seq.of(0)));
        }
    }

    private static void assertNullRejected(Runnable action) {
        assertThrows(NullPointerException.class, action);
    }

    private SeqStream<Integer> emptyStream() {
        return streamOf();
    }

    private static void assertConsumed(Runnable action) {
        try {
            action.run();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    @SafeVarargs
    private final <E> SeqStream<E> streamOf(E... elements) {
        return SeqStream.viewOf(Arrays.stream(elements));
    }
}
