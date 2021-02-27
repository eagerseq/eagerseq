package org.bitbucket.seqly;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.bitbucket.seqly.SeqTest.assertThrows;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SeqStreamTest {

    @Test
    public void testAllStreamReturningMethodsReturnSeqStream() {
        for (Method method : SeqStream.class.getMethods()) {
            if (Stream.class.isAssignableFrom(method.getReturnType())
                    && !method.isSynthetic()
                    && !Seq.of("collect", "stream",
                    "takeWhile", "dropWhile") // compiling with -release 8
                    // where these methods don't exist on stream
                    .contains(method.getName())) {
                assertThat(method.getName(), method.getReturnType(),
                        equalTo(SeqStream.class));
            }
        }
    }

    @Test
    public void testRange() {
        assertTrue(SeqStream.range(4, 7).listEquals(streamOf(4, 5, 6)));
        assertTrue(SeqStream.range(7, 4).isEmpty());
        assertTrue(SeqStream.range(-12, -10).listEquals(streamOf(-12, -11)));
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
        assertThrows(() -> SeqStream.concat(streamOf(), null).collect());
        assertThrows(() -> SeqStream.concat(null, streamOf()).collect());
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
        assertThrows(() -> SeqStream.flatten(streamOf(streamOf(), null)).collect());
        assertThrows(() -> SeqStream.flatten(streamOf(null, streamOf())).collect());
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
        SeqStream<Integer> stream = streamOf(0);
        assertThat(stream.unordered(), sameInstance(stream));
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
                streamOf(0, 1, 2).flatMapToDouble(DoubleStream::of).toArray(), 1e-12);
    }

    @Test
    public void testExpand() {
        assertThat(Split.expand(Integer.MAX_VALUE / 4 * 3),
                equalTo(Integer.MAX_VALUE - 8));
        try {
            Split.expand(Integer.MAX_VALUE - 4);
            fail("expected OutOfMemoryError");
        } catch (OutOfMemoryError expected) {
        }
    }

    @Test
    public void testToInt() {
        assertThrows(() -> Split.toInt(Integer.MAX_VALUE + 1L));
        assertThrows(() -> Split.toInt(Integer.MIN_VALUE - 1L));
    }

    @SafeVarargs
    private final <E> SeqStream<E> streamOf(E... elements) {
        return SeqStream.view(Arrays.stream(elements));
    }
}
