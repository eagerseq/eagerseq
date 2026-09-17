package io.github.eagerseq;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.eagerseq.SeqTest.assertThrows;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import static java.util.Spliterator.SUBSIZED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SourcesTest {

    @Test
    public void testArrayBackedSeqIsOrderedAndSized() {
        Spliterator<Integer> spliterator = Seq.of(0, 1).spliterator();
        assertTrue(spliterator.hasCharacteristics(ORDERED | SIZED | SUBSIZED));
    }

    @Test
    public void testDeferredSourceComputesOnFirstAdvance() {
        int[] computations = new int[1];
        Source<Integer> source = Sources.defer(() -> {
            computations[0]++;
            return ordered(0, 1);
        }, ORDERED | SIZED | SUBSIZED);

        assertThat(computations[0], equalTo(0));
        assertOnlyOrdered(source);
        assertThat(source.estimateSize(), equalTo(Long.MAX_VALUE));
        assertThrows(NullPointerException.class,
                () -> source.tryAdvance((Consumer<Integer>) null));
        assertThat(computations[0], equalTo(0));

        List<Integer> elements = new ArrayList<>();
        assertTrue(source.tryAdvance(elements::add));
        assertThat(computations[0], equalTo(1));
        source.forEachRemaining(elements::add);
        assertThat(computations[0], equalTo(1));
        assertThat(elements, contains(0, 1));
    }

    @Test
    public void testDeferredSourceDoesNotRetryFailedComputation() {
        int[] computations = new int[1];
        Source<Integer> source = Sources.defer(() -> {
            computations[0]++;
            throw new ClassCastException("first failure");
        }, ORDERED);

        assertThrows(ClassCastException.class, "first failure",
                () -> source.tryAdvance(element -> {}));
        assertThat(computations[0], equalTo(1));
        assertThrows(IllegalStateException.class,
                "deferred computation previously failed",
                () -> source.tryAdvance(element -> {}));
        assertThat(computations[0], equalTo(1));
    }

    @Test
    public void testScanDoesNotRetryFailedInitialization() {
        int[] initializations = new int[1];
        Source<Integer> source = SeqStream.of(0).scan(() -> {
            initializations[0]++;
            throw new ClassCastException("first failure");
        }, Integer::sum).spliterator();
        assertThat(initializations[0], equalTo(0));

        assertThrows(ClassCastException.class, "first failure",
                () -> source.tryAdvance(element -> {}));
        assertThat(initializations[0], equalTo(1));
        assertThrows(IllegalStateException.class,
                "deferred computation previously failed",
                () -> source.tryAdvance(element -> {}));
        assertThat(initializations[0], equalTo(1));
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
        Source<Integer> source = Sources.flatMap(
                ordered(0, 1), ignored -> unordered(2, 3), value -> value);
        assertOnlyOrdered(source);
    }

    @Test
    public void testEmptyLimitLastUsesSourceOrder() {
        assertTrue(Sources.limitLast(ordered(0), 0)
                .hasCharacteristics(ORDERED));
        assertFalse(Sources.limitLast(unordered(0), 0)
                .hasCharacteristics(ORDERED));
    }

    @Test
    public void testTwoSourceOperationsRequireBothSourcesToBeOrdered() {
        assertOnlyOrdered(Sources.zip(
                ordered(0), ordered(1), Integer::sum));
        assertFalse(Sources.zip(
                ordered(0), unordered(1), Integer::sum)
                .hasCharacteristics(ORDERED));

        assertOnlyOrdered(Sources.union(ordered(0), ordered(1)));
        assertFalse(Sources.union(ordered(0), unordered(1))
                .hasCharacteristics(ORDERED));

        assertOnlyOrdered(Sources.concat(value -> value,
                ordered(0), ordered(1)));
        assertFalse(Sources.concat(value -> value,
                ordered(0), unordered(1))
                .hasCharacteristics(ORDERED));

        assertFalse(Sources.product(
                unordered(0), new Object[]{1}, Integer::sum)
                .hasCharacteristics(ORDERED));
    }

    @Test
    public void testOperationsThatEstablishOrder() {
        assertOnlyOrdered(Sources.range(0, 2));
        assertOnlyOrdered(Sources.range(0L, 2L));
        assertOnlyOrdered(Sources.rangeClosed(0, 2));
        assertOnlyOrdered(Sources.rangeClosed(0L, 2L));
        assertOnlyOrdered(Sources.indexesOf(unordered(0, 0), 0));
        assertOnlyOrdered(Sources.indexesOfSlice(
                unordered(0, 0), unordered(0)));
        assertOnlyOrdered(Sources.permutations(new Object[]{0, 1}));
        assertOnlyOrdered(Sources.permutations(new Object[]{0, 1}, 1));
        assertOnlyOrdered(Sources.allPermutations(new Object[]{0, 1}));
        assertOnlyOrdered(Sources.combinations(new Object[]{0, 1}, 1));
        assertOnlyOrdered(Sources.allCombinations(new Object[]{0, 1}));
        assertOnlyOrdered(Sources.power(new Object[]{0, 1}, 1));
        assertOnlyOrdered(Sources.product(
                ordered(0, 1), new Object[]{2, 3}, Integer::sum));

        Spliterator<Integer> sorted = SeqStream.viewOf(unordered(1, 0))
                .sorted().spliterator();
        assertOnlyOrdered(sorted);
    }

    @Test
    public void testUnorderedRemainsUnorderedThroughOrdinaryOperations() {
        Source<Integer> source = SeqStream.of(0, 1)
                .unordered()
                .map(n -> n + 1)
                .filter(n -> n > 0)
                .spliterator();
        assertFalse(source.hasCharacteristics(ORDERED));

        Spliterator<Integer> sortedSource = new TreeSet<>(
                Arrays.asList(0, 1)).spliterator();
        Spliterator<Integer> unordered = SeqStream.viewOf(sortedSource)
                .unordered().spliterator();
        assertFalse(unordered.hasCharacteristics(
                Spliterator.ORDERED | Spliterator.SORTED));
    }

    @Test
    public void testWindowAndScanSourceContracts() {
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
        Spliterator<Integer> empty = Sources.filter(ordered(), n -> true);
        assertThrows(NullPointerException.class,
                () -> empty.tryAdvance((Consumer<Integer>) null));
        assertThrows(NullPointerException.class,
                () -> empty.forEachRemaining(null));

        Spliterator<Integer> exhausted = Sources.map(ordered(0), n -> n);
        assertTrue(exhausted.tryAdvance(n -> {}));
        assertThrows(NullPointerException.class,
                () -> exhausted.tryAdvance((Consumer<Integer>) null));

        Spliterator<Integer> emptyInts = Sources.range(0, 0);
        assertThrows(NullPointerException.class,
                () -> emptyInts.tryAdvance((Consumer<Integer>) null));
    }

    @Test
    public void testOrderedSplitIsAPrefix() {
        Integer[] elements = IntStream.range(0, 2000).boxed()
                .toArray(Integer[]::new);
        Spliterator<Integer> remainder = Sources.map(
                Source.viewOf(Arrays.spliterator(elements)), n -> n);
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
        assertThat(Sources.count(Source.viewOf(lyingSized())),
                equalTo(3L));
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
        Supplier<Source<Integer>> source = ordered
                ? () -> ordered(0, 1, 1)
                : () -> unordered(0, 1, 1);
        return Arrays.asList(
                () -> Sources.filter(source.get(), n -> true),
                () -> Sources.map(source.get(), n -> n),
                () -> Sources.mapIndexed(source.get(), (i, n) -> n),
                () -> Sources.mapMulti(source.get(),
                        (n, sink) -> sink.accept(n)),
                () -> Sources.distinct(source.get()),
                () -> Sources.peek(source.get(), n -> {}),
                () -> Sources.takeWhile(source.get(), n -> true),
                () -> Sources.dropWhile(source.get(), n -> false),
                () -> Sources.limit(source.get(), 1),
                () -> Sources.skip(source.get(), 1),
                () -> Sources.slice(source.get(), 0, 1),
                () -> Sources.limitLast(source.get(), 1),
                () -> Sources.skipLast(source.get(), 1),
                () -> Sources.intersection(source.get(), ordered(0)),
                () -> Sources.difference(source.get(), ordered(2)),
                () -> Sources.flatten(
                        source.get(), SourcesTest::ordered));
    }

    @SafeVarargs
    private static <E> Source<E> ordered(E... elements) {
        return Source
                .viewOf(Spliterators.spliterator(elements, ORDERED));
    }

    @SafeVarargs
    private static <E> Source<E> unordered(E... elements) {
        return Source.viewOf(Spliterators.spliterator(elements, 0));
    }

    private static void assertOnlyOrdered(Spliterator<?> spliterator) {
        assertThat(spliterator.characteristics(), equalTo(ORDERED));
    }

    private static final Integer[][] INNER = {{}, {1}, {2, 3}, {4, 5, 6}};
    private static final Integer[] OTHER = {3, 5, 5, 9, 100, 0};
    private static final Integer[] SLICE = {2, 3};

    // traversal consistency: every way of consuming a source must
    // yield the same elements, whatever state a stage keeps between calls

    private static Integer[] data(int n) {
        Integer[] a = new Integer[n];
        for (int i = 0; i < n; i++) a[i] = i < 20 ? i : i % 7;
        return a;
    }

    private static <T> Source<Integer> hashes(
            Source<T[]> source) {
        // array-valued results compare as elements by content
        return Sources.map(source, Arrays::hashCode);
    }

    private static final Map<String, Function<Source<Integer>, Source<Integer>>> PIPES = new LinkedHashMap<>();
    static {
        PIPES.put("identity", s -> s);
        PIPES.put("defer",
                s -> Sources.defer(() -> Sources.map(s, x -> x + 1), 0));
        PIPES.put("map", s -> Sources.map(s, x -> x + 1));
        PIPES.put("mapIndexed",
                s -> Sources.mapIndexed(s, (i, x) -> i * 100 + x));
        PIPES.put("filter", s -> Sources.filter(s, x -> x % 3 == 0));
        PIPES.put("map.filter.map", s -> Sources.map(Sources.filter(
                Sources.map(s, x -> x + 1), x -> (x & 1) == 0), x -> x * 3));
        PIPES.put("flatMap", s -> Sources.flatMap(
                s, x -> Sources.toSource(INNER[x & 3])));
        PIPES.put("flatten", s -> Sources.flatten(
                s, x -> Sources.<Integer>toSource(INNER[x & 3])));
        PIPES.put("concat", s -> Sources.concat(Function.identity(),
                s, Sources.<Integer>toSource(OTHER), s));
        PIPES.put("mapMulti",
                s -> Sources.<Integer, Integer>mapMulti(s, (x, sink) -> {
                    for (int i = 0; i < (x & 3); i++) sink.accept(x * 10 + i);
                }));
        PIPES.put("distinct", Sources::distinct);
        PIPES.put("distinctBy", s -> Sources.distinctBy(s, x -> x & 3));
        PIPES.put("limit0", s -> Sources.limit(s, 0));
        PIPES.put("limit5", s -> Sources.limit(s, 5));
        PIPES.put("filter.limit.map", s -> Sources.map(
                Sources.limit(Sources.filter(s, x -> x % 2 == 0), 4), x -> -x));
        PIPES.put("skip", s -> Sources.skip(s, 3));
        PIPES.put("slice", s -> Sources.slice(s, 2, 6));
        PIPES.put("takeWhile", s -> Sources.takeWhile(s, x -> x < 7));
        PIPES.put("takeWhile.filter", s -> Sources.filter(
                Sources.takeWhile(s, x -> x < 7), x -> x > 2));
        PIPES.put("dropWhile", s -> Sources.dropWhile(s, x -> x < 7));
        for (int n : new int[]{0, 3, 50}) {
            PIPES.put("limitLast" + n, s -> Sources.limitLast(s, n));
            PIPES.put("skipLast" + n, s -> Sources.skipLast(s, n));
        }
        PIPES.put("zip",
                s -> Sources.zip(s, Sources.range(10, 14), (x, y) -> x * y));
        PIPES.put("zipOther",
                s -> Sources.zip(Sources.range(10, 14), s, (x, y) -> x * y));
        PIPES.put("intersection",
                s -> Sources.intersection(s, Sources.toSource(OTHER)));
        PIPES.put("difference",
                s -> Sources.difference(s, Sources.toSource(OTHER)));
        PIPES.put("union",
                s -> Sources.union(s, Sources.<Integer>toSource(OTHER)));
        PIPES.put("indexes", Sources::indexes);
        PIPES.put("indexesOf", s -> Sources.indexesOf(s, 5));
        PIPES.put("indexesOfSlice",
                s -> Sources.indexesOfSlice(s, Sources.toSource(SLICE)));
        int[][] windows = {{3, 1}, {3, 3}, {2, 5}, {5, 2}, {1, 1}};
        for (int[] w : windows) {
            PIPES.put("window" + w[0] + "x" + w[1],
                    s -> hashes(Sources.window(s, w[0], w[1])));
        }
        PIPES.put("windowFixed", s -> hashes(Sources.windowFixed(s, 3)));
        PIPES.put("windowSliding", s -> hashes(Sources.windowSliding(s, 3)));
        PIPES.put("scan", s -> Sources.scan(s, () -> 0, (acc, x) -> acc + x));
        PIPES.put("peek", s -> Sources.peek(s, x -> {}));
        PIPES.put("unordered", Sources::unordered);
        PIPES.put("permutations", s -> hashes(Sources.defer(
                () -> Sources.permutations(Sources.toArray(Sources.limit(s, 4)),
                        2),
                0)));
        PIPES.put("allPermutations", s -> hashes(Sources.defer(
                () -> Sources
                        .allPermutations(Sources.toArray(Sources.limit(s, 3))),
                0)));
        PIPES.put("combinations", s -> hashes(Sources.defer(
                () -> Sources.combinations(Sources.toArray(Sources.limit(s, 5)),
                        3),
                0)));
        PIPES.put("allCombinations", s -> hashes(Sources.defer(
                () -> Sources
                        .allCombinations(Sources.toArray(Sources.limit(s, 4))),
                0)));
        PIPES.put("power", s -> hashes(Sources.defer(
                () -> Sources.power(Sources.toArray(Sources.limit(s, 3)), 2),
                0)));
        PIPES.put("product", s -> Sources.<Integer, Integer, Integer>product(
                s, OTHER, (x, y) -> x * 1000 + y));
        PIPES.put("repeat", s -> Sources.repeat(7, 4));
        PIPES.put("generate", s -> Sources.generate(() -> 7, 4));
        PIPES.put("iterate", s -> Sources.iterate(1, x -> x < 100, x -> x * 3));
        PIPES.put("iterate.limit",
                s -> Sources.limit(Sources.iterate(1, x -> x * 3), 6));
        PIPES.put("range", s -> Sources.range(3, 8));
        PIPES.put("rangeClosed", s -> Sources.rangeClosed(3, 8));
        PIPES.put("rangeClosedOne", s -> Sources.rangeClosed(3, 3));
        PIPES.put("rangeEmpty", s -> Sources.rangeClosed(8, 3));
        PIPES.put("rangeLong",
                s -> Sources.map(Sources.rangeClosed(3L, 8L), Long::intValue));
    }

    private static final Map<String, Function<Integer[], Source<Integer>>> SOURCES = new LinkedHashMap<>();
    static {
        SOURCES.put("toSource.array", Sources::toSource);
        SOURCES.put("toSource.iterator",
                a -> Sources.toSource(Arrays.asList(a).iterator()));
        SOURCES.put("toSource.iterable",
                a -> Sources.toSource(Arrays.asList(a)));
        SOURCES.put("toSource.stream",
                a -> Sources.toSource(Stream.of(a)));
        SOURCES.put("toSource.spliterator",
                a -> Sources.toSource(Arrays.asList(a).spliterator()));
    }

    /**
     * The tables above are only as good as their coverage: every factory in
     * {@code Sources} that returns a {@code Source} must appear in one of
     * them, named by a dotted segment of the key with any trailing size
     * suffix removed, eg {@code limitLast3} or {@code window3x1}.
     */
    @Test
    public void everySourceFactoryIsExercisedByTheTables() {
        Set<String> covered = new HashSet<>();
        for (String key : PIPES.keySet()) {
            for (String segment : key.split("\\.")) {
                covered.add(segment.replaceFirst("[0-9x]+$", ""));
            }
        }
        for (String key : SOURCES.keySet()) {
            covered.add(key.split("\\.")[0]);
        }
        Set<String> uncovered = new TreeSet<>();
        for (Method method : Sources.class.getDeclaredMethods()) {
            if (Modifier.isPrivate(method.getModifiers())
                    || method.isSynthetic()
                    || !Source.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (!covered.contains(method.getName())) {
                uncovered.add(method.getName());
            }
        }
        assertThat("Source factories missing from PIPES or SOURCES",
                uncovered, empty());
    }

    /**
     * A source must reject a null sink before it is first traversed, after
     * a sink has stopped it part way, and after it is exhausted: an early
     * return for an exhausted or closed source must not skip the check.
     */
    @Test
    public void nullSinkIsRejectedInEveryState() {
        for (int n : new int[]{0, 3, 9}) {
            Integer[] a = data(n);
            for (Map.Entry<String, Function<Integer[], Source<Integer>>> source : SOURCES
                    .entrySet()) {
                for (Map.Entry<String, Function<Source<Integer>, Source<Integer>>> pipe : PIPES
                        .entrySet()) {
                    Source<Integer> s = source.getValue()
                            .andThen(pipe.getValue()).apply(a);
                    String at = pipe.getKey() + " over " + source.getKey()
                            + " n=" + n;
                    assertNullSinkRejected(at + " fresh", s);
                    s.forEachWhile(e -> false);
                    assertNullSinkRejected(at + " stopped", s);
                    s.forEachWhile(e -> true);
                    assertNullSinkRejected(at + " exhausted", s);
                }
            }
        }
    }

    @Test
    public void closedFlattenRejectsNullSink() {
        List<Runnable> onClose = new ArrayList<>();
        Source<Integer> flattened = Sources.flatten(
                Sources.<Integer>toSource(data(3)),
                x -> Sources.<Integer>toSource(INNER[x & 3]),
                ignored -> {}, onClose::add);
        onClose.forEach(Runnable::run);
        assertNullSinkRejected("closed flatten", flattened);
    }

    private static void assertNullSinkRejected(String at, Source<?> s) {
        try {
            s.forEachWhile(null);
            fail(at + ": forEachWhile(null) was accepted");
        } catch (NullPointerException expected) {
        }
        try {
            s.tryAdvance(null);
            fail(at + ": tryAdvance(null) was accepted");
        } catch (NullPointerException expected) {
        }
        try {
            s.forEachRemaining(null);
            fail(at + ": forEachRemaining(null) was accepted");
        } catch (NullPointerException expected) {
        }
    }

    private static List<Integer> push(Spliterator<Integer> s) {
        List<Integer> out = new ArrayList<>();
        s.forEachRemaining(out::add);
        return out;
    }

    private static List<Integer> pull(Spliterator<Integer> s) {
        List<Integer> out = new ArrayList<>();
        while (s.tryAdvance(out::add)) {
        }
        return out;
    }

    private static List<Integer> mixed(Spliterator<Integer> s, int k) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < k && s.tryAdvance(out::add); i++) {
        }
        s.forEachRemaining(out::add);
        return out;
    }

    private static List<Integer> stopping(Source<Integer> s, int k) {
        List<Integer> out = new ArrayList<>();
        int[] i = new int[1];
        while (!s.forEachWhile(e -> {
            out.add(e);
            return ++i[0] % k != 0;
        })) {
        }
        return out;
    }

    @Test
    public void everyConsumptionPatternYieldsTheSameElements() {
        for (int n : new int[]{0, 1, 2, 5, 13, 40}) {
            Integer[] a = data(n);
            for (Map.Entry<String, Function<Integer[], Source<Integer>>> source : SOURCES
                    .entrySet()) {
                for (Map.Entry<String, Function<Source<Integer>, Source<Integer>>> pipe : PIPES
                        .entrySet()) {
                    Function<Integer[], Source<Integer>> make = source
                            .getValue().andThen(pipe.getValue());
                    List<Integer> expected = push(make.apply(a));
                    String at = pipe.getKey() + " over " + source.getKey()
                            + " n=" + n;
                    assertEquals(at + " pull", expected, pull(make.apply(a)));
                    for (int k = 1; k <= 4; k++) {
                        assertEquals(at + " mixed " + k, expected,
                                mixed(make.apply(a), k));
                        assertEquals(at + " stopping " + k, expected,
                                stopping(make.apply(a), k));
                    }
                }
            }
        }
    }

    @Test
    public void flattenClosesEachSourceOnceWhateverTheConsumption() {
        for (int n : new int[]{0, 3, 9}) {
            Integer[] a = data(n);
            List<Integer> expectedClosed = Arrays.asList(a);
            for (int stopAt : new int[]{0, 1, 4, 100}) {
                List<Integer> closed = new ArrayList<>();
                List<Runnable> onClose = new ArrayList<>();
                Source<Integer> flattened = Sources.flatten(
                        Sources.<Integer>toSource(a),
                        x -> Sources.<Integer>toSource(INNER[x & 3]),
                        closed::add, onClose::add);
                List<Integer> out = mixed(flattened, stopAt);
                assertEquals("elements n=" + n, push(Sources.flatMap(
                        Sources.<Integer>toSource(a),
                        x -> Sources.<Integer>toSource(INNER[x & 3]))), out);
                assertEquals("closed n=" + n + " stopAt=" + stopAt,
                        expectedClosed, closed);
                onClose.forEach(Runnable::run);
                assertEquals("closed again", expectedClosed, closed);
                assertEquals("after close", Arrays.asList(), pull(flattened));
            }
        }
    }

    @Test
    public void sourceDefaultsAdvanceOnceAndPromiseNothing() {
        int[] next = {0};
        Source<Integer> source = sink -> {
            while (next[0] < 3) {
                if (!sink.push(next[0]++)) return false;
            }
            return true;
        };
        assertEquals(null, source.trySplit());
        assertEquals(Long.MAX_VALUE, source.estimateSize());
        assertEquals(0, source.characteristics());
        assertThrows(NullPointerException.class,
                () -> source.tryAdvance((Consumer<Integer>) null));

        List<Integer> out = new ArrayList<>();
        assertTrue(source.tryAdvance(out::add));
        assertEquals(Arrays.asList(0), out);
        assertEquals(Arrays.asList(1, 2), pull(source));
        assertFalse(source.tryAdvance(out::add));
        assertEquals(Arrays.asList(0), out);

        int[] from = {0};
        Source<Integer> unconsumed = sink -> {
            while (from[0] < 3) {
                if (!sink.push(from[0]++)) return false;
            }
            return true;
        };
        assertEquals(Arrays.asList(0, 1, 2), push(unconsumed));
    }

    @Test
    public void arraySourceSplitsIntoAPrefix() {
        Source<Integer> s = Sources.toSource(data(10));
        Spliterator<Integer> prefix = s.trySplit();
        assertEquals(5, prefix.estimateSize());
        assertEquals(5, s.estimateSize());
        assertEquals(Arrays.asList(0, 1, 2, 3, 4), pull(prefix));
        assertEquals(Arrays.asList(5, 6, 7, 8, 9), pull(s));
    }
}
