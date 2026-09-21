package io.github.eagerseq;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class TraversalTest {

    @Test
    public void exactTerminalConsumption() {
        checkConsumption(1, 0, s -> s.getFirst());
        checkConsumption(4, 3, s -> s.get(3));
        checkConsumption(4, true, s -> s.anyMatch(i -> i == 3));
        checkConsumption(4, false, s -> s.allMatch(i -> i < 3));
        checkConsumption(4, false, s -> s.noneMatch(i -> i == 3));
        checkConsumption(4, 3, s -> s.indexOf(3));
        checkConsumption(4, true, s -> s.contains(3));
        checkConsumption(2, false, s -> s.findOnly().isPresent());
        checkConsumption(0, true, s -> s.startsWith(Stream.empty()));
        checkConsumption(3, true, s -> s.startsWith(Stream.of(0, 1, 2)));
        checkConsumption(2, false, s -> s.startsWith(Stream.of(0, 9, 2)));
        checkConsumption(4, 2, s -> s.indexOfSlice(Stream.of(2, 3)));
        checkConsumption(0, 0, s -> s.limit(0).toList().size());
        checkConsumption(5, Arrays.asList(2, 3, 4),
                s -> s.skip(2).limit(3).toList());
        checkConsumption(4, Arrays.asList(0, 1, 2),
                s -> s.takeWhile(i -> i < 3).toList());
    }

    private static void checkConsumption(int count, Object expected,
            Function<SeqStream<Integer>, Object> terminal) {
        int[] consumed = {0};
        SeqStream<Integer> stream = SeqStream.generate(() -> {
            if (consumed[0] >= count) throw new AssertionError("extra input");
            return consumed[0]++;
        });
        assertEquals(expected, terminal.apply(stream));
        assertEquals(count, consumed[0]);
        SeqTest.assertThrows(IllegalStateException.class, stream::count);
    }

    @Test
    public void mixedTraversalMatchesIndependentReferences() {
        for (int length = 0; length <= 12; length++) {
            List<Integer> input = new ArrayList<>();
            for (int i = 0; i < length; i++) input.add(i % 4);
            for (int size = 0; size <= 14; size++) {
                int n = size;
                List<Integer> expanded = new ArrayList<>();
                for (int value : input) {
                    for (int i = 0; i < value; i++)
                        expanded.add(value * 10 + i);
                }
                checkTraversal(input, s -> s.<Integer>mapMulti((v, out) -> {
                    for (int i = 0; i < v; i++) out.accept(v * 10 + i);
                }).skip(n).limit(5),
                        expanded.subList(Math.min(n, expanded.size()),
                                Math.min(n + 5, expanded.size())));
                checkTraversal(input, s -> s.skipLast(n),
                        input.subList(0, Math.max(0, length - n)));
                checkTraversal(input, s -> s.limitLast(n),
                        input.subList(Math.max(0, length - n), length));
                if (n == 0) continue;
                for (boolean sliding : new boolean[]{false, true}) {
                    List<List<Integer>> windows = new ArrayList<>();
                    for (int start = 0; start < length; start += sliding ? 1
                            : n) {
                        int end = Math.min(length, start + n);
                        if (sliding && start > 0 && end - start < n) break;
                        windows.add(new ArrayList<>(input.subList(start, end)));
                    }
                    checkTraversal(input,
                            s -> (sliding ? s.windowSliding(n)
                                    : s.windowFixed(n))
                                    .map(Seq::toList),
                            windows);
                }
            }
        }
    }

    @Test
    public void largeParallelBridgePreservesBufferedOutputOrder() {
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            expected.add(i);
            expected.add(-i);
        }
        assertEquals(expected, SeqStream.range(0, 2500)
                .<Integer>mapMulti((i, out) -> {
                    out.accept(i);
                    out.accept(-i);
                }).parallel().toStream()
                .collect(java.util.stream.Collectors.toList()));
    }

    @Test
    public void closedRangesDoNotWrapAtNumericMaximum() {
        assertEquals(Arrays.asList(Integer.MAX_VALUE - 1, Integer.MAX_VALUE),
                SeqStream.rangeClosed(Integer.MAX_VALUE - 1, Integer.MAX_VALUE)
                        .toList());
        assertEquals(Arrays.asList(Long.MAX_VALUE - 1, Long.MAX_VALUE),
                SeqStream.rangeClosed(Long.MAX_VALUE - 1, Long.MAX_VALUE)
                        .toList());
        assertEquals(Arrays.asList(Long.MIN_VALUE, Long.MIN_VALUE + 1),
                SeqStream.rangeClosed(Long.MIN_VALUE, Long.MAX_VALUE).limit(2)
                        .toList());
        assertEquals(Arrays.asList(Integer.MIN_VALUE, Integer.MIN_VALUE + 1),
                SeqStream.rangeClosed(Integer.MIN_VALUE, Integer.MAX_VALUE)
                        .limit(2).toList());
    }

    private static <T> void checkTraversal(List<Integer> input,
            Function<SeqStream<Integer>, SeqStream<T>> operation,
            List<T> expected) {
        for (int prefix = 0; prefix <= expected.size() + 1; prefix++) {
            Source<T> cursor = operation.apply(SeqStream.viewOf(input.stream()))
                    .spliterator();
            List<T> actual = new ArrayList<>();
            for (int i = 0; i < prefix; i++) cursor.tryAdvance(actual::add);
            cursor.forEachWhile(e -> {
                actual.add(e);
                return false;
            });
            cursor.forEachRemaining(actual::add);
            assertEquals(expected, actual);
            assertFalse(cursor.tryAdvance(
                    e -> fail("exhausted cursor delivered a value")));
        }
        Source<T> cursor = operation.apply(SeqStream.viewOf(input.stream()))
                .spliterator();
        List<T> splitValues = new ArrayList<>();
        Spliterator<T> split = cursor.trySplit();
        if (split != null) split.forEachRemaining(splitValues::add);
        cursor.forEachRemaining(splitValues::add);
        assertEquals(expected, splitValues);
    }
}
