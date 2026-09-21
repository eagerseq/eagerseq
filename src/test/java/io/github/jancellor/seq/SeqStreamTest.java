package io.github.jancellor.seq;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static io.github.jancellor.seq.SeqTest.assertThrows;
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
        assertTrue(streamOf(0).parallel().isParallel());
        assertFalse(streamOf(0).parallel().sequential().isParallel());
        assertTrue(Seq.of(0).parallelStream().isParallel());

        // the mode belongs to the pipeline, not to a stage boundary
        SeqStream<Integer> source = streamOf(0);
        SeqStream<Integer> derived = source.map(identity());
        derived.parallel();
        assertTrue(source.isParallel());
        source.sequential();
        assertFalse(derived.isParallel());
    }

    @Test
    public void testParallel() {
        SeqStream<Integer> stream = streamOf(0);
        assertThat(stream.parallel(), sameInstance(stream));
        assertTrue(stream.isParallel());
        // a parallel SeqStream is still evaluated sequentially
        assertThat(stream.toSeq(), equalTo(Seq.of(0)));
    }

    @Test
    public void testSequential() {
        SeqStream<Integer> stream = streamOf(0).parallel();
        assertThat(stream.sequential(), sameInstance(stream));
        assertFalse(stream.isParallel());
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
        List<String> closed = new ArrayList<>();
        SeqStream<Integer> source = streamOf(0, 1);
        assertThat(source.onClose(() -> closed.add("first")),
                sameInstance(source));
        SeqStream<Integer> derived = source.map(identity());
        assertConsumed(() -> source.onClose(() -> {}));
        assertThat(derived.onClose(() -> closed.add("second")),
                sameInstance(derived));
        assertThat(derived.toSeq(), equalTo(Seq.of(0, 1)));
        assertTrue(closed.isEmpty());
        assertConsumed(() -> derived.onClose(() -> {}));

        // handlers belong to the pipeline and run in registration order
        source.close();
        assertThat(closed, equalTo(Arrays.asList("first", "second")));
        derived.close();
        assertThat(closed, equalTo(Arrays.asList("first", "second")));

        assertNullRejected(() -> streamOf().onClose(null));

        SeqStream<Integer> closedStream = streamOf(0);
        closedStream.close();
        assertConsumed(() -> closedStream.onClose(() -> {}));
        assertConsumed(() -> closedStream.pipeline()
                .onClose(() -> {}));
    }

    @Test
    public void testClose() {
        streamOf().close();

        int[] closes = {0};
        SeqStream<Integer> stream = streamOf(0).onClose(() -> closes[0]++);
        stream.close();
        stream.close();
        assertThat(closes[0], equalTo(1));
        assertConsumed(stream::toSeq);
        assertConsumed(() -> stream.filter(e -> true));

        // closing any stage closes every stage of the same pipeline
        SeqStream<Integer> source = streamOf(0);
        int[] pipelineCloses = {0};
        SeqStream<Integer> derived = source.map(identity())
                .onClose(() -> pipelineCloses[0]++);
        source.close();
        assertConsumed(derived::toSeq);
        derived.close();
        assertThat(pipelineCloses[0], equalTo(1));

        // every handler runs; the first exception wins, the rest suppressed
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        int[] third = {0};
        SeqStream<Integer> failing = streamOf(0)
                .onClose(() -> {
                    throw first;
                })
                .onClose(() -> {
                    throw second;
                })
                .onClose(() -> third[0]++);
        try {
            failing.close();
            fail("expected RuntimeException");
        } catch (RuntimeException expected) {
            assertThat(expected, sameInstance(first));
            assertArrayEquals(new Throwable[]{second},
                    expected.getSuppressed());
        }
        assertThat(third[0], equalTo(1));

        // Errors and even sneakily thrown checked exceptions behave the same.
        AssertionError error = new AssertionError("error");
        Exception checked = new Exception("checked");
        int[] afterFailures = {0};
        SeqStream<Integer> failingWithError = streamOf(0)
                .onClose(() -> {
                    throw error;
                })
                .onClose(() -> SeqStreamTest
                        .<RuntimeException>throwUnchecked(checked))
                .onClose(() -> afterFailures[0]++);
        try {
            failingWithError.close();
            fail("expected AssertionError");
        } catch (AssertionError expected) {
            assertThat(expected, sameInstance(error));
            assertArrayEquals(new Throwable[]{checked},
                    expected.getSuppressed());
        }
        assertThat(afterFailures[0], equalTo(1));

        RuntimeException repeated = new RuntimeException("repeated");
        SeqStream<Integer> failingTwice = streamOf(0)
                .onClose(() -> {
                    throw repeated;
                })
                .onClose(() -> {
                    throw repeated;
                });
        try {
            failingTwice.close();
            fail("expected RuntimeException");
        } catch (RuntimeException expected) {
            assertThat(expected, sameInstance(repeated));
            assertArrayEquals(new Throwable[0], expected.getSuppressed());
        }
    }

    @Test
    public void testPipeline() {
        int[] closes = {0};
        SeqStream<Integer> source = SeqStream
                .viewOf(Arrays.asList(0, 1).spliterator())
                .onClose(() -> closes[0]++);
        SeqStream<Integer> derived = source.map(identity());
        assertThat(derived.pipeline(), sameInstance(source.pipeline()));
        assertConsumed(() -> source.onClose(() -> {}));

        derived.parallel();
        assertTrue(source.isParallel());
        source.close();
        assertThat(closes[0], equalTo(1));
        assertConsumed(derived::toSeq);
    }

    @Test
    public void testFlattenOwnsItsOuterStream() {
        List<String> closes = new ArrayList<>();
        SeqStream<Integer> flattened = SeqStream.flatten(
                Stream.of(
                        Stream.of(0, 1)
                                .onClose(() -> closes.add("first")),
                        Stream.<Integer>empty()
                                .onClose(() -> closes.add("empty")))
                        .onClose(() -> closes.add("outer")));
        assertFalse(flattened.isParallel());
        assertThat(flattened.toSeq(), equalTo(Seq.of(0, 1)));
        assertThat(closes, equalTo(Arrays.asList("first", "empty")));
        flattened.close();
        assertThat(closes,
                equalTo(Arrays.asList("first", "empty", "outer")));
        flattened.close();
        assertThat(closes,
                equalTo(Arrays.asList("first", "empty", "outer")));

        assertTrue(SeqStream
                .flatten(Stream.of(Stream.of(0)).parallel()).isParallel());
    }

    @Test
    public void testFlattenClosesCurrentInnerOnTheNextPullOrClose() {
        int[] innerCloses = {0};
        int[] outerCloses = {0};
        SeqStream<Integer> flattened = SeqStream.flatten(
                Stream.of(Stream.of(0, 1)
                        .onClose(() -> innerCloses[0]++))
                        .onClose(() -> outerCloses[0]++));
        Spliterator<Integer> cursor = flattened.spliterator();

        assertTrue(cursor.tryAdvance(i -> assertThat(i, equalTo(0))));
        assertTrue(cursor.tryAdvance(i -> assertThat(i, equalTo(1))));
        assertThat(innerCloses[0], equalTo(0));
        assertFalse(cursor.tryAdvance(i -> {}));
        assertThat(innerCloses[0], equalTo(1));
        assertThat(outerCloses[0], equalTo(0));

        flattened.close();
        assertThat(innerCloses[0], equalTo(1));
        assertThat(outerCloses[0], equalTo(1));
    }

    @Test
    public void testFlattenCursorIsExhaustedOnceClosed() {
        int[] innerCloses = {0};
        int[] laterOpens = {0};
        SeqStream<Integer> flattened = SeqStream.flatten(Stream.of(
                Stream.of(0, 1, 2).onClose(() -> innerCloses[0]++),
                Stream.of(3, 4).peek(i -> laterOpens[0]++)));
        Spliterator<Integer> cursor = flattened.spliterator();

        assertTrue(cursor.tryAdvance(i -> assertThat(i, equalTo(0))));
        flattened.close();
        assertThat(innerCloses[0], equalTo(1));

        // neither the rest of the current inner stream nor any later inner
        // stream, which nothing could close, is traversed after closing
        assertFalse(cursor.tryAdvance(i -> fail()));
        assertThat(laterOpens[0], equalTo(0));
    }

    @Test
    public void testFlattenClosesAnInfiniteInnerWhenClosed() {
        int[] reads = {0};
        int[] innerCloses = {0};
        int[] outerCloses = {0};
        SeqStream<Integer> flattened = SeqStream.flatten(
                Stream.of(Stream.generate(() -> reads[0]++)
                        .onClose(() -> innerCloses[0]++))
                        .onClose(() -> outerCloses[0]++));
        Spliterator<Integer> cursor = flattened.spliterator();

        assertTrue(cursor.tryAdvance(i -> assertThat(i, equalTo(0))));
        assertTrue(cursor.tryAdvance(i -> assertThat(i, equalTo(1))));
        assertThat(reads[0], equalTo(2));
        assertThat(innerCloses[0], equalTo(0));

        flattened.close();
        assertThat(innerCloses[0], equalTo(1));
        assertThat(outerCloses[0], equalTo(1));
    }

    @Test
    public void testFlattenClosesCurrentInnerAfterTraversalFailureWhenClosed() {
        RuntimeException traversal = new RuntimeException("traversal");
        RuntimeException closing = new RuntimeException("closing");
        int[] outerCloses = {0};
        SeqStream<Integer> flattened = SeqStream.flatten(
                Stream.of(Stream.<Integer>generate(() -> {
                    throw traversal;
                }).onClose(() -> {
                    throw closing;
                }))
                        .onClose(() -> outerCloses[0]++));

        try {
            flattened.count();
            fail("expected traversal failure");
        } catch (RuntimeException expected) {
            assertThat(expected, sameInstance(traversal));
            assertArrayEquals(new Throwable[0], expected.getSuppressed());
        }
        try {
            flattened.close();
            fail("expected closing failure");
        } catch (RuntimeException expected) {
            assertThat(expected, sameInstance(closing));
        }
        assertThat(outerCloses[0], equalTo(1));
    }

    @Test
    public void testFlattenClosesInnerAfterObtainingItsSourceFails() {
        int[] innerCloses = {0};
        Stream<Integer> inner = Stream.of(0)
                .onClose(() -> innerCloses[0]++);
        inner.spliterator();
        SeqStream<Integer> flattened = SeqStream.flatten(Stream.of(inner));

        assertThrows(IllegalStateException.class, flattened::count);
        assertThat(innerCloses[0], equalTo(0));
        flattened.close();
        assertThat(innerCloses[0], equalTo(1));
    }

    @Test
    public void testFlatMapClosesMappedStreams() {
        List<Integer> closed = new ArrayList<>();
        assertThat(streamOf(0, 1, 2)
                .flatMap(i -> i == 1 ? null
                        : Stream.of(i)
                                .onClose(() -> closed.add(i)))
                .toSeq(),
                equalTo(Seq.of(0, 2)));
        assertThat(closed, equalTo(Arrays.asList(0, 2)));

        int[] innerCloses = {0};
        SeqStream<Integer> source = streamOf(0);
        SeqStream<Integer> flattened = source
                .flatMap(ignored -> Stream.generate(() -> 1)
                        .onClose(() -> innerCloses[0]++));
        assertTrue(flattened.spliterator().tryAdvance(i -> {}));
        assertThat(innerCloses[0], equalTo(0));
        source.close();
        assertThat(innerCloses[0], equalTo(1));
    }

    @Test
    public void testConcatOwnsItsInputs() {
        List<String> closed = new ArrayList<>();
        SeqStream<Integer> concatenated = SeqStream.concat(
                Stream.of(0).onClose(() -> closed.add("first")),
                Stream.of(1).onClose(() -> closed.add("second")));
        assertFalse(concatenated.isParallel());
        assertThat(concatenated.toSeq(), equalTo(Seq.of(0, 1)));
        assertTrue(closed.isEmpty());
        concatenated.close();
        assertThat(closed, equalTo(Arrays.asList("first", "second")));

        assertTrue(SeqStream
                .concat(Stream.of(0), Stream.of(1).parallel()).isParallel());
    }

    @Test
    public void testViewOfStreamOwnsItsSource() {
        int[] closes = {0};
        SeqStream<Integer> view = SeqStream.viewOf(
                Stream.of(0, 1).onClose(() -> closes[0]++));
        assertFalse(view.isParallel());
        assertThat(view.toSeq(), equalTo(Seq.of(0, 1)));
        assertThat(closes[0], equalTo(0));
        view.close();
        assertThat(closes[0], equalTo(1));

        assertTrue(SeqStream.viewOf(Stream.of(0).parallel()).isParallel());
    }

    @Test
    public void testToStream() {
        SeqStream<Integer> source = streamOf(0, 1);
        Stream<Integer> stream = source.toStream();
        assertFalse(stream.isParallel());
        assertThat(stream.collect(Collectors.toList()),
                equalTo(Arrays.asList(0, 1)));
        assertConsumed(source::toStream);

        // takes the current parallel mode
        assertTrue(streamOf(0).parallel().toStream().isParallel());

        // closing the result closes this pipeline
        int[] closes = {0};
        SeqStream<Integer> closed = streamOf(0).onClose(() -> closes[0]++);
        closed.toStream().close();
        assertThat(closes[0], equalTo(1));
    }

    @Test
    public void testCloses() {
        int[] closes = {0};
        Stream<Integer> owned = Stream.of(2, 3)
                .onClose(() -> closes[0]++);
        SeqStream<Integer> source = streamOf(0, 1);
        assertThat(source.closes(owned), sameInstance(source));
        assertThat(source.toSeq(), equalTo(Seq.of(0, 1)));
        assertThat(closes[0], equalTo(0));
        source.close();
        source.close();
        assertThat(closes[0], equalTo(1));

        SeqStream<Integer> nullArgument = streamOf(0);
        assertNullRejected(() -> nullArgument.closes(null));
        assertThat(nullArgument.toSeq(), equalTo(Seq.of(0)));

        SeqStream<Integer> consumed = streamOf(0);
        consumed.count();
        assertConsumed(() -> consumed.closes(Stream.empty()));
    }

    @Test
    public void testOperationsAdoptTheirStreamArguments() {
        List<BiConsumer<SeqStream<Integer>, Stream<Integer>>> operations = Arrays
                .asList(
                        (source, that) -> source.listEquals(that),
                        (source, that) -> source.setEquals(that),
                        (source, that) -> source.multisetEquals(that),
                        (source, that) -> source.zip(that, Integer::sum),
                        SeqStream::intersection,
                        SeqStream::difference,
                        SeqStream::symmetricDifference,
                        (source, that) -> source.disjoint(that),
                        SeqStream::union,
                        SeqStream::sum,
                        (source, that) -> source.containsMultiset(that),
                        (source, that) -> source.product(that, Integer::sum),
                        SeqStream::indexesOfSlice,
                        (source, that) -> source.indexOfSlice(that),
                        (source, that) -> source.lastIndexOfSlice(that),
                        (source, that) -> source.containsSlice(that),
                        (source, that) -> source.startsWith(that),
                        (source, that) -> source.endsWith(that),
                        (source, that) -> source.containsAll(that));

        for (BiConsumer<SeqStream<Integer>, Stream<Integer>> operation : operations) {
            int[] closes = {0};
            SeqStream<Integer> source = streamOf(0, 1);
            Stream<Integer> that = Stream.of(0, 1)
                    .onClose(() -> closes[0]++);
            operation.accept(source, that);
            assertThat(closes[0], equalTo(0));
            source.close();
            that.close();
            assertThat(closes[0], equalTo(1));
        }

        int[] invalidCloses = {0};
        SeqStream<Integer> invalid = streamOf(0);
        Stream<Integer> that = Stream.of(1)
                .onClose(() -> invalidCloses[0]++);
        assertNullRejected(() -> invalid.zip(that, null));
        assertThat(invalid.toSeq(), equalTo(Seq.of(0)));
        invalid.close();
        assertThat(invalidCloses[0], equalTo(0));
        that.close();
        assertThat(invalidCloses[0], equalTo(1));
    }

    @Test
    public void testOperationsDeferTheirStreamArguments() {
        List<BiFunction<SeqStream<Integer>, Stream<Integer>, SeqStream<?>>> operations = Arrays
                .asList(
                        (source, that) -> source.product(that, Integer::sum),
                        SeqStream::indexesOfSlice,
                        SeqStream::intersection,
                        SeqStream::difference,
                        SeqStream::symmetricDifference);
        for (BiFunction<SeqStream<Integer>, Stream<Integer>, SeqStream<?>> operation : operations) {
            for (Function<SeqStream<?>, BooleanSupplier> cursorFactory : deferredCursors()) {
                int[] reads = {0, 0};
                SeqStream<Integer> first = streamOf(0, 1, 2)
                        .peek(e -> reads[0]++);
                SeqStream<Integer> second = streamOf(1, 2)
                        .peek(e -> reads[1]++);
                SeqStream<?> result = operation.apply(first, second);
                assertArrayEquals(new int[]{0, 0}, reads);
                assertConsumed(() -> first.count());
                assertConsumed(() -> second.count());
                BooleanSupplier advance = cursorFactory.apply(result);
                assertArrayEquals(new int[]{0, 0}, reads);
                assertTrue(advance.getAsBoolean());
                assertThat(reads[1], equalTo(2));
            }
        }
    }

    @Test
    public void testPrimitiveStreamsCarryModeAndClose() {
        int[] closes = {0};
        IntStream ints = streamOf(0, 1)
                .onClose(() -> closes[0]++)
                .parallel()
                .mapToInt(i -> i);
        assertTrue(ints.isParallel());
        assertArrayEquals(new int[]{0, 1}, ints.toArray());
        assertThat(closes[0], equalTo(0));
        ints.close();
        assertThat(closes[0], equalTo(1));
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
        assertFalse(SeqStream.iterate(0, i -> i + 1).disjoint(streamOf(5)));
        assertFalse(streamOf(0).containsAll(SeqStream.iterate(0, i -> i + 1)));
        assertTrue(SeqStream.iterate(0, i -> i + 1).anyMatch(i -> i == 5));
        assertFalse(SeqStream.iterate(0, i -> i + 1).allMatch(i -> i < 5));
        assertFalse(SeqStream.iterate(0, i -> i + 1).noneMatch(i -> i == 5));
        assertTrue(SeqStream.iterate(0, i -> i + 1)
                .startsWith(streamOf(0, 1, 2)));
        assertTrue(SeqStream.iterate(0, i -> i + 1)
                .containsSlice(streamOf(4, 5, 6)));

        assertThat(SeqStream.iterate(0, i -> i + 1)
                .collectWhile(ArrayList<Integer>::new,
                        (acc, e) -> acc.add(e) && acc.size() < 2),
                equalTo(Arrays.asList(0, 1)));
    }

    @Test
    public void testIsSortedShortCircuitsAndConsumesStream() {
        List<Integer> visited = new ArrayList<>();
        SeqStream<Integer> stream = streamOf(1, 3, 2, 4).peek(visited::add);
        assertFalse(stream.isSorted());
        assertThat(visited, equalTo(Arrays.asList(1, 3, 2)));
        assertConsumed(stream::isSorted);

        visited.clear();
        SeqStream<Integer> custom = streamOf(3, 1, 2, 0).peek(visited::add);
        assertFalse(custom.isSorted(Comparator.reverseOrder()));
        assertThat(visited, equalTo(Arrays.asList(3, 1, 2)));
        assertConsumed(() -> custom.isSorted(Comparator.reverseOrder()));

        SeqStream<Integer> invalid = streamOf(1, 2);
        assertNullRejected(() -> invalid.isSorted(null));
        assertTrue(invalid.isSorted());
    }

    @Test
    public void testFrequency() {
        SeqStream<Integer> stream = streamOf(1, 2, 2, 3);
        assertThat(stream.frequency(2), equalTo(2));
        assertConsumed(() -> stream.frequency(2));
    }

    @Test(timeout = 5000)
    public void testIntermediateOperationsAreLazy() {
        int[] traversed = new int[1];
        SeqStream<Integer> result = SeqStream.iterate(0, i -> i + 1)
                .peek(i -> traversed[0]++)
                .filter(i -> i % 2 == 0)
                .map(i -> i + 1)
                .mapIndexed((index, i) -> index + i)
                .flatMap(i -> SeqStream.of(i, i))
                .distinct()
                .skip(1)
                .limit(3);

        assertThat(traversed[0], equalTo(0));
        assertThat(result.toSeq(), equalTo(Seq.of(4, 7, 10)));
        assertThat(traversed[0], equalTo(7));
    }

    @Test(timeout = 1000)
    public void testWindowAndScanLaziness() {
        for (boolean sliding : new boolean[]{false, true}) {
            int[] reads = {0};
            SeqStream<Integer> source = SeqStream.iterate(0, i -> i + 1)
                    .peek(i -> reads[0]++);
            SeqStream<Seq<Integer>> result = sliding
                    ? source.windowSliding(3)
                    : source.windowFixed(3);
            assertThrows(IllegalStateException.class, source::spliterator);
            Iterator<Seq<Integer>> iterator = result.iterator();
            assertThat(reads[0], equalTo(0));
            assertThat(iterator.next(), equalTo(Seq.of(0, 1, 2)));
            assertThat(reads[0], equalTo(3));
            assertThat(iterator.next(), equalTo(sliding
                    ? Seq.of(1, 2, 3)
                    : Seq.of(3, 4, 5)));
            assertThat(reads[0], equalTo(sliding ? 4 : 6));
        }
        int[] calls = {0, 0};
        SeqStream<Integer> source = SeqStream.iterate(1, i -> i + 1);
        SeqStream<Integer> result = source.scan(() -> {
            calls[0]++;
            return 0;
        }, (a, b) -> {
            calls[1]++;
            return a + b;
        });
        assertArrayEquals(new int[]{0, 0}, calls);
        assertThrows(IllegalStateException.class, source::spliterator);
        Spliterator<Integer> cursor = result.spliterator();
        assertArrayEquals(new int[]{0, 0}, calls);
        assertThat(SeqStream.viewOf(cursor).limit(3).toSeq(),
                equalTo(Seq.of(1, 3, 6)));
        assertArrayEquals(new int[]{1, 3}, calls);
    }

    @Test
    public void testDeferredSizedOperationsObserveMutationBeforeTerminals() {
        for (Function<SeqStream<Integer>, SeqStream<Integer>> operation : deferredSizedOperations()) {
            List<Integer> list = new ArrayList<>(Arrays.asList(2, 1));
            int[] reads = {0};
            SeqStream<Integer> counted = operation.apply(
                    Seq.viewOf(list).stream().peek(e -> reads[0]++));
            list.add(3);
            assertThat(counted.count(), equalTo(3L));
            assertThat(reads[0], equalTo(0));

            list = new ArrayList<>(Arrays.asList(2, 1));
            SeqStream<Integer> collected = operation
                    .apply(Seq.viewOf(list).stream());
            list.add(3);
            List<Integer> expected = operation.apply(SeqStream.of(2, 1, 3))
                    .toList();
            assertThat(collected.toList(), equalTo(expected));
        }
    }

    @Test
    public void testDeferredSizedOperationsDoNotBindOnCursorAcquisition() {
        for (Function<SeqStream<Integer>, SeqStream<Integer>> operation : deferredSizedOperations()) {
            for (boolean iteratorCursor : new boolean[]{false, true}) {
                List<Integer> list = new ArrayList<>(Arrays.asList(2, 1));
                SeqStream<Integer> stream = operation
                        .apply(Seq.viewOf(list).stream());
                List<Integer> actual = new ArrayList<>();
                if (iteratorCursor) {
                    Iterator<Integer> cursor = stream.iterator();
                    list.add(3);
                    cursor.forEachRemaining(actual::add);
                } else {
                    Spliterator<Integer> cursor = stream.spliterator();
                    assertTrue(cursor.hasCharacteristics(Spliterator.SIZED));
                    list.add(3);
                    assertThat(cursor.getExactSizeIfKnown(), equalTo(3L));
                    assertTrue(cursor.tryAdvance(actual::add));
                    assertThat(cursor.estimateSize(), equalTo(2L));
                    cursor.forEachRemaining(actual::add);
                    assertThat(cursor.estimateSize(), equalTo(0L));
                }
                assertThat(actual, equalTo(
                        operation.apply(SeqStream.of(2, 1, 3)).toList()));
            }
        }
    }

    private static List<Function<SeqStream<Integer>, SeqStream<Integer>>> deferredSizedOperations() {
        return Arrays.asList(
                SeqStream::sorted,
                stream -> stream.sorted(Comparator.reverseOrder()),
                SeqStream::reversed,
                stream -> stream.rotated(1),
                stream -> stream.shuffled(new Random(0)),
                stream -> stream.scan(() -> 0, Integer::sum));
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
    public void testBuilder() {
        SeqStream.Builder<Integer> emptyBuilder = SeqStream.builder();
        assertTrue(emptyBuilder.build().isEmpty());
        assertThrows(IllegalStateException.class, emptyBuilder::build);
        assertThrows(IllegalStateException.class, () -> emptyBuilder.accept(0));
        assertThrows(IllegalStateException.class, () -> emptyBuilder.add(0));

        SeqStream.Builder<Integer> builder = SeqStream.builder();
        builder.add(0).add(1).add(null).add(3).add(4);
        SeqStream<Integer> built = builder.build();
        assertTrue(built.listEquals(streamOf(0, 1, null, 3, 4)));
        assertThrows(IllegalStateException.class, builder::build);
        assertThrows(IllegalStateException.class, () -> builder.accept(5));
        assertThrows(IllegalStateException.class, () -> builder.add(5));
    }

    @Test
    public void testNextLength() {
        assertThat(ArrayBuilder.nextLength(Integer.MAX_VALUE / 4 * 3),
                equalTo(Integer.MAX_VALUE - 8));
        try {
            ArrayBuilder.nextLength(Integer.MAX_VALUE - 4);
            fail("expected OutOfMemoryError");
        } catch (OutOfMemoryError expected) {
            assertThat(expected.getMessage(),
                    equalTo("maximum array length exceeded"));
        }
    }

    @Test
    public void testRequireNonNegativeArgument() {
        Sources.requireNonNegativeArgument("size", 0);
        Sources.requireNonNegativeArgument("size", Long.MAX_VALUE);
        try {
            Sources.requireNonNegativeArgument("size", -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(),
                    equalTo("size -1 was negative"));
        }
        try {
            Sources.requireNonNegativeArgument("size", Long.MIN_VALUE);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(),
                    equalTo("size -9223372036854775808 was negative"));
        }
    }

    @Test
    public void testRequireNonNegativeIndex() {
        Sources.requireNonNegativeIndex("from", 0);
        Sources.requireNonNegativeIndex("from", Integer.MAX_VALUE);
        try {
            Sources.requireNonNegativeIndex("from", -1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            assertThat(expected.getMessage(), equalTo("from -1 was negative"));
        }
        try {
            Sources.requireNonNegativeIndex("from", Integer.MIN_VALUE);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            assertThat(expected.getMessage(),
                    equalTo("from -2147483648 was negative"));
        }
    }

    @Test
    public void testToStringRejectsNullArgumentsWithoutClaimingStream() {
        for (Seq<Integer> seq : Arrays.asList(Seq.<Integer>of(),
                Seq.of(1, 2))) {
            int[] reads = {0};
            SeqStream<Integer> stream = seq.stream().peek(e -> reads[0]++);
            assertNullRejected(() -> stream.toString(null, "[", "]"));
            assertNullRejected(() -> stream.toString(", ", null, "]"));
            assertNullRejected(() -> stream.toString(", ", "[", null));
            assertThat(reads[0], equalTo(0));
            assertThat(stream.toString(", ", "[", "]"),
                    equalTo(seq.toString()));
            assertThat(reads[0], equalTo(seq.size()));
            assertConsumed(stream::count);
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
    public void testNullSourceArgumentsAreRejectedByFactories() {
        assertNullRejected(() -> SeqStream.of((Object[]) null));
        assertNullRejected(() -> SeqStream.viewOf((Iterator<Object>) null));
        assertNullRejected(() -> SeqStream.viewOf((Spliterator<Object>) null));
        assertNullRejected(() -> SeqStream.viewOf(
                (Spliterator<Object>) null, new SeqStreamPipeline()));
        assertNullRejected(() -> SeqStream.viewOf(
                Stream.empty().spliterator(), (SeqStream.Pipeline) null));
        assertNullRejected(() -> SeqStream.viewOf((Stream<Object>) null));
    }

    /**
     * The {@code SeqStream} half of
     * {@link SeqTest#testNullFunctionalArgumentsAreRejectedOnAnEmptySeq}.
     */
    @Test
    public void testNullFunctionalArgumentsAreRejectedOnAnEmptyStream() {
        assertNullRejected(() -> emptyStream().filter(null));
        assertNullRejected(() -> emptyStream().map(null));
        assertNullRejected(() -> emptyStream().mapIndexed(null));
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
        assertNullRejected(() -> emptyStream().partitionBy(null));
        assertNullRejected(() -> emptyStream().partitionBy(null, Seq::size));
        assertNullRejected(() -> emptyStream().partitionBy(e -> true, null));
        assertNullRejected(() -> emptyStream().sorted(null));
        assertNullRejected(() -> emptyStream().isSorted(null));
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
        assertNullRejected(
                () -> emptyStream().collectWhile(ArrayList::new, null));
        assertNullRejected(
                () -> emptyStream().<List<Integer>>collectWhile(null,
                        List::add));
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
    public void testWindowAndScanValidationDoesNotClaimSource() {
        SeqStream<Integer> source = SeqStream.of(1, 2);
        assertThrows(IllegalArgumentException.class,
                () -> source.windowFixed(0));
        assertThrows(IllegalArgumentException.class,
                () -> source.windowSliding(-1));
        assertThrows(NullPointerException.class,
                () -> source.scan(null, Integer::sum));
        assertThrows(NullPointerException.class,
                () -> source.scan(() -> 0, null));
        assertThat(source.toSeq(), equalTo(Seq.of(1, 2)));
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
                        stream -> stream.symmetricDifference(null),
                        stream -> stream.disjoint(null),
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

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(
            Throwable throwable) throws T {
        throw (T) throwable;
    }

    @SafeVarargs
    private final <E> SeqStream<E> streamOf(E... elements) {
        return SeqStream.viewOf(Arrays.stream(elements));
    }
}
