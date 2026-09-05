package io.github.eagerseq;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static io.github.eagerseq.SeqTest.assertThrows;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import static java.util.Spliterator.SUBSIZED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SpliteratorTest {

    @Test
    public void testArrayBackedSeqIsOrderedAndSized() {
        Spliterator<Integer> spliterator = Seq.of(0, 1).spliterator();
        assertTrue(spliterator.hasCharacteristics(ORDERED | SIZED | SUBSIZED));
    }

    @Test
    public void testDeferredSpliteratorComputesOnFirstAdvance() {
        int[] computations = new int[1];
        Spliterator<Integer> spliterator = Split.defer(() -> {
            computations[0]++;
            return ordered(0, 1);
        }, ORDERED | SIZED | SUBSIZED);

        assertThat(computations[0], equalTo(0));
        assertOnlyOrdered(spliterator);
        assertThat(spliterator.estimateSize(), equalTo(Long.MAX_VALUE));
        assertThrows(NullPointerException.class,
                () -> spliterator.tryAdvance((Consumer<Integer>) null));
        assertThat(computations[0], equalTo(0));

        List<Integer> elements = new ArrayList<>();
        assertTrue(spliterator.tryAdvance(elements::add));
        assertThat(computations[0], equalTo(1));
        spliterator.forEachRemaining(elements::add);
        assertThat(computations[0], equalTo(1));
        assertThat(elements, contains(0, 1));
    }

    @Test
    public void testDeferredSpliteratorDoesNotRetryFailedComputation() {
        int[] computations = new int[1];
        Spliterator<Integer> spliterator = Split.defer(() -> {
            computations[0]++;
            throw new ClassCastException("first failure");
        }, ORDERED);

        assertThrows(ClassCastException.class, "first failure",
                () -> spliterator.tryAdvance(element -> {}));
        assertThat(computations[0], equalTo(1));
        assertThrows(IllegalStateException.class,
                "deferred computation previously failed",
                () -> spliterator.tryAdvance(element -> {}));
        assertThat(computations[0], equalTo(1));
    }

    @Test
    public void testCollectionViewRequiresOrder() {
        assertThrows(IllegalArgumentException.class,
                "collection spliterator was not ORDERED",
                () -> Seq.viewOf(new HashSet<>(Arrays.asList(0, 1))));
        assertThat(Seq.viewOf(Arrays.asList(0, 1)), contains(0, 1));
    }

    @Test
    public void testOneSourceOperationsPropagateOrder() {
        for (Supplier<Spliterator<?>> operation : oneSourceOperations(true)) {
            assertOnlyOrdered(operation.get());
        }
        for (Supplier<Spliterator<?>> operation : oneSourceOperations(false)) {
            assertFalse(operation.get().hasCharacteristics(ORDERED));
        }
    }

    @Test
    public void testFlatMapUsesOuterOrder() {
        Spliterator<Integer> spliterator = Split.flatMap(
                ordered(0, 1), ignored -> unordered(2, 3));
        assertOnlyOrdered(spliterator);
    }

    @Test
    public void testEmptyLimitLastUsesSourceOrder() {
        assertTrue(Split.limitLast(ordered(0), 0)
                .hasCharacteristics(ORDERED));
        assertFalse(Split.limitLast(unordered(0), 0)
                .hasCharacteristics(ORDERED));
    }

    @Test
    public void testTwoSourceOperationsRequireBothSourcesToBeOrdered() {
        assertOnlyOrdered(Split.zip(
                ordered(0), ordered(1), Integer::sum));
        assertFalse(Split.zip(
                ordered(0), unordered(1), Integer::sum)
                .hasCharacteristics(ORDERED));

        assertOnlyOrdered(Split.union(ordered(0), ordered(1)));
        assertFalse(Split.union(ordered(0), unordered(1))
                .hasCharacteristics(ORDERED));

        assertOnlyOrdered(Split.concat(ordered(0), ordered(1)));
        assertFalse(Split.concat(ordered(0), unordered(1))
                .hasCharacteristics(ORDERED));

        assertFalse(Split.product(
                unordered(0), new Object[]{1}, Integer::sum)
                .hasCharacteristics(ORDERED));
    }

    @Test
    public void testOperationsThatEstablishOrder() {
        assertOnlyOrdered(Split.range(0, 2));
        assertOnlyOrdered(Split.range(0L, 2L));
        assertOnlyOrdered(Split.rangeClosed(0, 2));
        assertOnlyOrdered(Split.rangeClosed(0L, 2L));
        assertOnlyOrdered(Split.indexesOf(unordered(0, 0), 0));
        assertOnlyOrdered(Split.indexesOfSlice(
                unordered(0, 0), unordered(0)));
        assertOnlyOrdered(Split.permutations(new Object[]{0, 1}));
        assertOnlyOrdered(Split.permutations(new Object[]{0, 1}, 1));
        assertOnlyOrdered(Split.allPermutations(new Object[]{0, 1}));
        assertOnlyOrdered(Split.combinations(new Object[]{0, 1}, 1));
        assertOnlyOrdered(Split.allCombinations(new Object[]{0, 1}));
        assertOnlyOrdered(Split.power(new Object[]{0, 1}, 1));
        assertOnlyOrdered(Split.product(
                ordered(0, 1), new Object[]{2, 3}, Integer::sum));

        Spliterator<Integer> sorted = SeqStream.viewOf(unordered(1, 0))
                .sorted().spliterator();
        assertOnlyOrdered(sorted);
    }

    @Test
    public void testUnorderedRemainsUnorderedThroughOrdinaryOperations() {
        Spliterator<Integer> spliterator = SeqStream.of(0, 1)
                .unordered()
                .map(n -> n + 1)
                .filter(n -> n > 0)
                .spliterator();
        assertFalse(spliterator.hasCharacteristics(ORDERED));

        Spliterator<Integer> sortedSource = new TreeSet<>(
                Arrays.asList(0, 1)).spliterator();
        Spliterator<Integer> unordered = SeqStream.viewOf(sortedSource)
                .unordered().spliterator();
        assertFalse(unordered.hasCharacteristics(
                Spliterator.ORDERED | Spliterator.SORTED));
    }

    @Test
    public void testWindowAndScanSpliteratorContracts() {
        for (boolean ordered : new boolean[]{false, true}) {
            for (int operation = 0; operation < 3; operation++) {
                SeqStream<Integer> source = SeqStream.of(1, 2, 3);
                if (!ordered) source = source.unordered();
                Spliterator<?> cursor = (operation == 0 ? source.windowFixed(2)
                        : operation == 1 ? source.windowSliding(2)
                                : source.scan(() -> 0, Integer::sum))
                        .spliterator();
                assertThat(cursor.characteristics(),
                        equalTo(ordered ? Spliterator.ORDERED : 0));
                assertThrows(NullPointerException.class,
                        () -> cursor.tryAdvance(null));
                cursor.forEachRemaining(value -> {});
                assertFalse(cursor.tryAdvance(value -> fail("exhausted")));
                assertFalse(cursor.tryAdvance(value -> fail("exhausted")));
            }
        }
    }

    @Test
    public void testNullActionIsRejectedWhenEmptyOrExhausted() {
        Spliterator<Integer> empty = Split.filter(ordered(), n -> true);
        assertThrows(NullPointerException.class,
                () -> empty.tryAdvance((Consumer<Integer>) null));
        assertThrows(NullPointerException.class,
                () -> empty.forEachRemaining(null));

        Spliterator<Integer> exhausted = Split.map(ordered(0), n -> n);
        assertTrue(exhausted.tryAdvance(n -> {}));
        assertThrows(NullPointerException.class,
                () -> exhausted.tryAdvance((Consumer<Integer>) null));

        Spliterator.OfInt emptyInts = Split.range(0, 0);
        assertThrows(NullPointerException.class,
                () -> emptyInts.tryAdvance((IntConsumer) null));
    }

    @Test
    public void testOrderedSplitIsAPrefix() {
        Integer[] elements = IntStream.range(0, 2000).boxed()
                .toArray(Integer[]::new);
        Spliterator<Integer> remainder = Split.map(
                Arrays.spliterator(elements), n -> n);
        Spliterator<Integer> prefix = remainder.trySplit();
        if (prefix == null) fail("expected a prefix");

        List<Integer> actual = new ArrayList<>();
        prefix.forEachRemaining(actual::add);
        assertThat(actual.size(), equalTo(1024));
        remainder.forEachRemaining(actual::add);
        assertThat(actual, contains(elements));
    }

    @Test
    public void testReportedSizeIsNotTrustedForResults() {
        assertThat(Split.count(lyingSized()), equalTo(3L));
        assertThat(Seq.copyOf(lyingSized()), contains(0, 1, 2));
    }

    private static Spliterator<Integer> lyingSized() {
        return new Spliterators.AbstractSpliterator<Integer>(
                1, SIZED) {
            private int next;
            public boolean tryAdvance(Consumer<? super Integer> action) {
                if (next == 3) return false;
                action.accept(next++);
                return true;
            }
        };
    }

    private static Iterable<Supplier<Spliterator<?>>> oneSourceOperations(
            boolean ordered) {
        Supplier<Spliterator<Integer>> source = ordered
                ? () -> ordered(0, 1, 1)
                : () -> unordered(0, 1, 1);
        return Arrays.asList(
                () -> Split.filter(source.get(), n -> true),
                () -> Split.map(source.get(), n -> n),
                () -> Split.mapMulti(source.get(), (n, sink) -> sink.accept(n)),
                () -> Split.distinct(source.get()),
                () -> Split.peek(source.get(), n -> {}),
                () -> Split.takeWhile(source.get(), n -> true),
                () -> Split.dropWhile(source.get(), n -> false),
                () -> Split.limit(source.get(), 1),
                () -> Split.skip(source.get(), 1),
                () -> Split.slice(source.get(), 0, 1),
                () -> Split.limitLast(source.get(), 1),
                () -> Split.skipLast(source.get(), 1),
                () -> Split.intersection(source.get(), ordered(0)),
                () -> Split.difference(source.get(), ordered(2)),
                () -> Split.flatten(Split.map(
                        source.get(), SpliteratorTest::ordered)));
    }

    @SafeVarargs
    private static <E> Spliterator<E> ordered(E... elements) {
        return Spliterators.spliterator(elements, ORDERED);
    }

    @SafeVarargs
    private static <E> Spliterator<E> unordered(E... elements) {
        return Spliterators.spliterator(elements, 0);
    }

    private static void assertOnlyOrdered(Spliterator<?> spliterator) {
        assertThat(spliterator.characteristics(), equalTo(ORDERED));
    }
}
