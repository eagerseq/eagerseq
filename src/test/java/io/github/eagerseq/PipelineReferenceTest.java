package io.github.eagerseq;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Deterministic compositions compared against independent JDK and list references. */
public class PipelineReferenceTest {
    @Test
    public void testRandomCompositions() {
        Random random = new Random(194721);
        for (int trial = 0; trial < 3000; trial++) {
            List<Integer> input = new ArrayList<>();
            int length = random.nextInt(70);
            for (int i = 0; i < length; i++) input.add(random.nextInt(15) - 7);
            int[] operations = new int[1 + random.nextInt(8)];
            for (int i = 0; i < operations.length; i++)
                operations[i] = random.nextInt(22);
            Stream<Integer> reference = input.stream();
            for (int operation : operations)
                reference = jdk(reference, operation);
            List<Integer> expected = reference.collect(Collectors.toList());
            for (int mode = 0; mode < 4; mode++) {
                SeqStream<Integer> actual = mode == 0
                        ? SeqStream.viewOf(input.iterator())
                        : SeqStream.viewOf(input.stream());
                for (int operation : operations)
                    actual = seq(actual, operation);
                List<Integer> values = new ArrayList<>();
                if (mode == 0) values = actual.toList();
                if (mode == 1) values = actual.parallel().toStream()
                        .collect(Collectors.toList());
                if (mode == 2) {
                    Iterator<Integer> iterator = actual.iterator();
                    while (iterator.hasNext()) {
                        assertTrue(iterator.hasNext());
                        values.add(iterator.next());
                    }
                    assertFalse(iterator.hasNext());
                }
                if (mode == 3) {
                    Source<Integer> cursor = actual.spliterator();
                    for (int i = 0; i < 3; i++) cursor.tryAdvance(values::add);
                    Spliterator<Integer> prefix = cursor.trySplit();
                    if (prefix != null) prefix.forEachRemaining(values::add);
                    cursor.forEachRemaining(values::add);
                }
                assertEquals(
                        "trial=" + trial + " mode=" + mode + " ops="
                                + Arrays.toString(operations),
                        expected, values);
            }
        }
    }
    private static SeqStream<Integer> seq(SeqStream<Integer> s, int op) {
        switch (op) {
        case 0:
            return s.map(i -> i * 2 + 1);
        case 1:
            return s.filter(i -> i % 3 != 0);
        case 2:
            return s.skip(3);
        case 3:
            return s.limit(7);
        case 4:
            return s.distinct();
        case 5:
            return s.sorted();
        case 6:
            return s.flatMap(
                    i -> i % 2 == 0 ? Stream.empty() : Stream.of(i, -i));
        case 7:
            return s.mapMulti((i, out) -> {
                for (int n = 0; n < Math.abs(i % 4); n++) out.accept(i + n);
            });
        case 8:
            return s.takeWhile(i -> i < 5);
        case 9:
            return s.dropWhile(i -> i < 0);
        case 10:
            return s.sorted(Comparator.reverseOrder());
        case 11:
            return s.scan(() -> 0, Integer::sum);
        case 12:
            return s.mapIndexed((i, value) -> i + value);
        case 13:
            return s.reversed();
        case 14:
            return s.rotated(-3);
        case 15:
            return s.limitLast(5);
        case 16:
            return s.skipLast(2);
        case 17:
            return s.windowSliding(3).flatMap(Seq::stream);
        case 18:
            return s.zip(Stream.of(100, 101, 102), Integer::sum);
        case 19:
            return s.product(Stream.of(1, 2), (a, b) -> a * b);
        case 20:
            return s.indexesOfSlice(Stream.of(1, 1));
        default:
            return s.filter(i -> true);
        }
    }
    private static Stream<Integer> jdk(Stream<Integer> s, int op) {
        switch (op) {
        case 0:
            return s.map(i -> i * 2 + 1);
        case 1:
            return s.filter(i -> i % 3 != 0);
        case 2:
            return s.skip(3);
        case 3:
            return s.limit(7);
        case 4:
            return s.distinct();
        case 5:
            return s.sorted();
        case 6:
            return s.flatMap(
                    i -> i % 2 == 0 ? Stream.empty() : Stream.of(i, -i));
        case 7:
            return s.flatMap(i -> IntStream.range(0, Math.abs(i % 4))
                    .mapToObj(n -> i + n));
        case 8: {
            List<Integer> result = new ArrayList<>();
            Iterator<Integer> iterator = s.iterator();
            while (iterator.hasNext()) {
                int i = iterator.next();
                if (i >= 5) break;
                result.add(i);
            }
            return result.stream();
        }
        case 9: {
            List<Integer> result = new ArrayList<>();
            Iterator<Integer> iterator = s.iterator();
            boolean found = false;
            while (iterator.hasNext()) {
                int i = iterator.next();
                if (i >= 0) found = true;
                if (found) result.add(i);
            }
            return result.stream();
        }
        case 10:
            return s.sorted(Comparator.reverseOrder());
        case 11:
        case 12:
        case 13:
        case 14:
        case 15:
        case 16:
        case 17:
        case 18:
        case 19:
        case 20:
            return listReference(s.collect(Collectors.toList()), op).stream();
        default:
            return s.filter(i -> true);
        }
    }

    private static List<Integer> listReference(List<Integer> input, int op) {
        List<Integer> result = new ArrayList<>();
        int sum = 0;
        for (int i = 0; i < input.size(); i++) {
            int value = input.get(i);
            switch (op) {
            case 11:
                sum += value;
                result.add(sum);
                break;
            case 12:
                result.add(i + value);
                break;
            case 13:
                result.add(input.get(input.size() - 1 - i));
                break;
            case 14:
                result.add(input.get((i + 3) % input.size()));
                break;
            case 15:
                if (i >= input.size() - 5) result.add(value);
                break;
            case 16:
                if (i < input.size() - 2) result.add(value);
                break;
            case 17:
                if (i == 0 || i + 3 <= input.size()) {
                    for (int j = i; j < Math.min(i + 3, input.size()); j++)
                        result.add(input.get(j));
                }
                break;
            case 18:
                if (i < 3) result.add(value + 100 + i);
                break;
            case 19:
                result.add(value);
                result.add(value * 2);
                break;
            case 20:
                if (value == 1 && i + 1 < input.size() && input.get(i + 1) == 1)
                    result.add(i);
                break;
            default:
                throw new AssertionError(op);
            }
        }
        return result;
    }
}
