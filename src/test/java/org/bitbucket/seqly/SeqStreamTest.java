package org.bitbucket.seqly;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.TreeSet;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.bitbucket.seqly.SeqTest.assertThrows;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SeqStreamTest {

    @Test
    public void testAllStreamReturningMethodsReturnSeqStream() {
        for (Method method : SeqStream.class.getMethods()) {
            if (Stream.class.isAssignableFrom(method.getReturnType())
                    && !method.isSynthetic()
                    && !Seq.of("takeWhile", "dropWhile", "mapMulti")
                    // compiling with -release 8, so these are declared by
                    // SeqStream without overriding the Stream method of the
                    // same name, which is therefore also reported here
                    .contains(method.getName())
                    // Gatherer does not exist before Java 24, so gather
                    // cannot be declared at all while targeting Java 8
                    && !method.getName().equals("gather")) {
                assertThat(method.getName(), method.getReturnType(),
                        equalTo(SeqStream.class));
            }
        }
    }

    /**
     * Detects methods added to {@code Stream} by later Java versions that
     * have not yet been considered for {@code Seq}. When this fails, either
     * add the method to {@code Seq} or add it below with a reason.
     */
    @Test
    public void testStreamMethodsAbsentFromSeqAreOnlyTheKnownExceptions() {
        Set<String> declaredBySeq = new HashSet<>();
        for (Method method : Seq.class.getDeclaredMethods()) {
            declaredBySeq.add(method.getName());
        }
        Set<String> absent = new TreeSet<>();
        for (Method method : Stream.class.getDeclaredMethods()) {
            if (!method.isSynthetic()
                    && !Modifier.isStatic(method.getModifiers())
                    && !declaredBySeq.contains(method.getName())) {
                absent.add(method.getName());
            }
        }
        absent.removeAll(Arrays.asList(
                // Seq is an object collection and defines no primitive
                // stream methods at all, including mapToInt and flatMapToInt
                "mapMultiToInt", "mapMultiToLong", "mapMultiToDouble",
                "mapToInt", "mapToLong", "mapToDouble",
                "flatMapToInt", "flatMapToLong", "flatMapToDouble",
                // Gatherer does not exist before Java 24
                "gather",
                // exists only to let a parallel pipeline ignore encounter
                // order; a Seq is eager and sequential, so it would be a
                // synonym for findFirst promising less
                "findAny"));
        assertThat("Stream methods absent from Seq without a stated reason",
                absent, empty());
    }

    /**
     * Detects methods added to {@code Seq} that have no {@code SeqStream}
     * equivalent. When this fails, either add the method to
     * {@code SeqStream} or add it below with a reason.
     */
    @Test
    public void testSeqMethodsAbsentFromSeqStreamAreOnlyTheKnownExceptions() {
        Set<String> declaredBySeqStream = new HashSet<>();
        for (Method method : SeqStream.class.getMethods()) {
            declaredBySeqStream.add(method.getName());
        }
        Set<String> absent = new TreeSet<>();
        for (Method method : Seq.class.getMethods()) {
            if (!method.isSynthetic()
                    && !Modifier.isStatic(method.getModifiers())
                    && !declaredBySeqStream.contains(method.getName())) {
                absent.add(method.getName());
            }
        }
        absent.removeAll(Arrays.asList(
                // Collection mutators, which Seq inherits only to throw
                "add", "addAll", "clear", "remove", "removeAll", "removeIf",
                "retainAll",
                // a SeqStream is consumed by any traversal, so it cannot
                // define equality by its elements
                "equals", "hashCode",
                // a SeqStream is already a stream
                "stream", "parallelStream"));
        assertThat("Seq methods absent from SeqStream without a stated reason",
                absent, empty());
    }

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
    }

    @Test(timeout = 5000)
    public void testIterate() {
        Spliterator<Integer> spliterator =
                SeqStream.iterate(0, n -> n + 1).spliterator();
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
        assertThrows(() -> SeqStream.concat(streamOf(), null).toSeq());
        assertThrows(() -> SeqStream.concat(null, streamOf()).toSeq());
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
        assertThrows(() -> SeqStream.flatten(streamOf(streamOf(), null)).toSeq());
        assertThrows(() -> SeqStream.flatten(streamOf(null, streamOf())).toSeq());
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
                streamOf(0, 1, 2).flatMapToDouble(DoubleStream::of).toArray(), 1e-12);
    }

    @Test(timeout = 5000)
    public void testGetRejectsNegativeIndexWithoutTraversing() {
        assertThrows(IndexOutOfBoundsException.class, () ->
                SeqStream.iterate(0, i -> i + 1).get(-1));
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
    public void testSeqIsReusableAfterItsStreamIsConsumed() {
        Seq<Integer> seq = Seq.of(0, 1, 2);
        assertThat(seq.stream().toSeq(), equalTo(seq));
        assertThat(seq.stream().toSeq(), equalTo(seq));
    }

    @Test
    public void testViewOfRejectsNullSpliterator() {
        assertThrows(() -> SeqStream.viewOf((Spliterator<Object>) null));
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
