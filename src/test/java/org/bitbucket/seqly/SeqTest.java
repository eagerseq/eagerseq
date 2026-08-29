package org.bitbucket.seqly;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static java.util.Collections.emptySortedSet;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.bitbucket.seqly.Seq.toSeq;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class SeqTest {

    private final Factory factory;

    public SeqTest(String ignored, Factory factory) {
        this.factory = factory;
    }

    @Parameters(name = "{0}")
    public static Iterable<Object[]> parametersList() {
        return Seq.of(
                parameters("default", TestDelegatingSeq::new),
                parameters("Seq#of(E...)", Seq::of),
                parameters("Seq#copy(E[])", Seq::copy),
                parameters("Seq#view(E[])", Seq::view),
                parameters("Seq#copy(Iterable<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Arrays.asList(elements));
                    }
                }),
                parameters("Seq#view(Iterable<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        // ::iterator makes iterable have non-SIZED spliterator
                        return Seq.view(Arrays.asList(elements)::iterator);
                    }
                }),
                parameters("Seq#copy(Iterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Arrays.asList(elements).iterator());
                    }
                }),
                parameters("Seq#copy(Spliterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Arrays.asList(elements).spliterator());
                    }
                }),
                parameters("Seq#copy(Stream<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Arrays.stream(elements));
                    }
                }),
                parameters("SeqStream#of(E...)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.of(elements).toSeq();
                    }
                }),
                parameters("SeqStream#view(Iterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.view(Arrays.asList(elements)
                                .iterator()).toSeq();
                    }
                }),
                parameters("SeqStream#view(Spliterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.view(Arrays.asList(elements)
                                .spliterator()).toSeq();
                    }
                }),
                parameters("SeqStream#view(Stream<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.view(Arrays.stream(elements))
                                .toSeq();
                    }
                }));
    }

    public static void assertThrows(Runnable action) {
        try {
            action.run();
            fail("expected exception");
        } catch (RuntimeException ignored) {
        }
    }

    public static void assertThrows(
            Class<? extends RuntimeException> expected, Runnable action) {
        try {
            action.run();
            fail("expected " + expected.getSimpleName());
        } catch (RuntimeException e) {
            if (!expected.isInstance(e)) throw e;
        }
    }

    public static void assertThrows(
            Class<? extends RuntimeException> expected,
            String message,
            Runnable action) {
        try {
            action.run();
            fail("expected " + expected.getSimpleName());
        } catch (RuntimeException e) {
            if (!expected.isInstance(e)) throw e;
            assertThat(e.getMessage(), equalTo(message));
        }
    }

    private static Object[] parameters(String name, Factory factory) {
        return new Object[]{name, factory};
    }

    @Test
    public void testAllMethodsHaveTests() {
        Seq<String> testMethods = Seq.view(SeqTest.class.getDeclaredMethods())
                .filter(m -> m.getAnnotation(Test.class) != null)
                .map(m -> m.getName().toLowerCase().replaceFirst("test", ""))
                .distinct();
        Seq<String> implementedMethods = Seq.view(Seq.class.getMethods())
                .map(m -> m.getName().toLowerCase())
                .distinct();
        assertThat(implementedMethods.difference(testMethods), empty());
    }

    @Test
    public void testAllSeqReturningMethodsReturnViews() {
        for (Method method : Seq.class.getMethods()) {
            if (Seq.class.isAssignableFrom(method.getReturnType())
                    && !method.isSynthetic()
                    && !method.getName().equals("stream")
                    && !method.getName().equals("parallelStream")) {
                assertThat(method.getName(), method.getReturnType(),
                        equalTo(Seq.class));
            }
        }
    }

    @Test
    public void testOf() {
        assertThat(Seq.of(), empty());
        assertThat(Seq.of(0), contains(0));
        assertThat(Seq.of(0, 1, 2, null), contains(0, 1, 2, null));
        assertThat(Seq.of(0, 1, 2).getClass(), equalTo(ArraySeq.class));
        Integer[] elements = {0, 1};
        Seq<Integer> seq = Seq.of(elements);
        elements[0] = 2;
        assertThat(seq, contains(0, 1));
        assertThrows(() -> Seq.of((Object[]) null));
    }

    @Test
    public void testOfNullable() {
        assertThat(Seq.ofNullable(0), contains(0));
        assertThat(Seq.ofNullable(null), empty());
        assertThat(Seq.ofNullable(0).getClass(), equalTo(ArraySeq.class));
        assertThat(Seq.ofNullable(null).getClass(), equalTo(ArraySeq.class));
    }

    @Test
    public void testCopy() {
        Seq<String> collection = Seq.of("", null, ".");
        for (Seq<String> seq : Seq.of(
                Seq.copy(collection.toArray(new String[0])),
                Seq.copy(collection),
                Seq.copy(collection.iterator()),
                Seq.copy(collection.spliterator()),
                Seq.copy(collection.stream()))) {
            assertThat(seq, contains("", null, "."));
        }
        assertThat(Seq.copy(Optional.of("")), contains(""));
        assertThat(Seq.copy(Optional.empty()), empty());
    }

    @Test
    public void testView() {
        Seq<String> collection = Seq.of("", null, ".");
        for (Seq<String> seq : Seq.of(
                Seq.view(collection.toArray(new String[0])),
                Seq.view(collection))) {
            assertThat(seq, contains("", null, "."));
        }
        Integer[] elements = {0, 1};
        Seq<Integer> seq = Seq.view(elements);
        elements[0] = 2;
        assertThat(seq, contains(2, 1));
    }

    @Test
    public void testBuilder() {
        Seq.Builder<Integer> builder = Seq.builder();
        assertThat(builder.build(), empty());
        assertThat(builder.build(), empty());
        assertThat(builder.add(0).build(), contains(0));
        assertThat(builder.add(1).build(), contains(0, 1));
        assertThat(builder.add(null).build(), contains(0, 1, null));
        assertThat(builder.add(3).build(), contains(0, 1, null, 3));
        Seq<Integer> built = builder.build();
        Arrays.asList(4, 5, 6, 7).forEach(builder::add);
        assertThat(built, contains(0, 1, null, 3));
    }

    @Test
    public void testToSeq() {
        assertThat(Stream.empty().collect(toSeq()), empty());
        assertThat(
                Stream.of(0, 1, 2, null).collect(toSeq()),
                contains(0, 1, 2, null));
        assertThat(Stream.of("").collect(toSeq()), contains(""));
        assertThat(IntStream.range(0, 1000).boxed().parallel()
                .collect(toSeq()).size(), equalTo(1000));
    }

    @Test
    public void testRange() {
        assertThat(Seq.range(4, 7), contains(4, 5, 6));
        assertThat(Seq.range(7, 4), empty());
        assertThat(Seq.range(-12, -10), contains(-12, -11));
    }

    @Test
    public void testConcat() {
        assertThat(Seq.concat(), empty());
        assertThat(
                Seq.concat(seqOf(0, 1)),
                contains(0, 1));
        assertThat(
                Seq.concat(seqOf(0, 1), seqOf(2, 3)),
                contains(0, 1, 2, 3));
        assertThat(
                Seq.concat(seqOf(0, 1), seqOf(2, 3), seqOf(4, 5)),
                contains(0, 1, 2, 3, 4, 5));
        assertThrows(() -> Seq.concat(seqOf(), null));
        assertThrows(() -> Seq.concat(null, seqOf()));
    }

    @Test
    public void testFlatten() {
        assertThat(Seq.flatten(seqOf()), empty());
        assertThat(
                Seq.flatten(seqOf(seqOf(0, 1))),
                contains(0, 1));
        assertThat(
                Seq.flatten(seqOf(seqOf(0, 1), seqOf(2, 3))),
                contains(0, 1, 2, 3));
        assertThat(
                Seq.flatten(seqOf(seqOf(0, 1), seqOf(2, 3), seqOf(4, 5))),
                contains(0, 1, 2, 3, 4, 5));
        assertThrows(() -> Seq.flatten(seqOf(seqOf(), null)));
        assertThrows(() -> Seq.flatten(seqOf(null, seqOf())));
    }

    @Test
    public void testToList() {
        assertThat(seqOf(0, 1, null).toList(), contains(0, 1, null));
        assertThat(seqOf(0, 1, null).toList(), hasSize(3));
        assertThat(seqOf(0, 1, null).toList().get(1), equalTo(1));
        assertTrue(seqOf(0, 1, null).toList() instanceof RandomAccess);
        assertThat(seqOf(0, 1, null).toList(), not(equalTo(seqOf(0, 1, null))));
        assertThat(seqOf(0, 1, null).toList(),
                equalTo(Arrays.asList(0, 1, null)));
        assertThat(seqOf(0, 1, null).toList(),
                not(equalTo(Arrays.asList(null, 1, 0))));
        assertThat(Seq.copy(seqOf(0, 1, null).toList().spliterator()),
                contains(0, 1, null));
        List<Integer> elements = new ArrayList<>(Arrays.asList(0, 1));
        List<Integer> snapshot = Seq.view(elements).toList();
        elements.add(2);
        assertThat(snapshot, contains(0, 1));
        assertThrows(() -> snapshot.add(2));
    }

    @Test
    public void testToSet() {
        assertThat(seqOf(0, 1, null, 1).toSet(), contains(0, 1, null));
        assertThat(seqOf(0, 1, null, 1).toSet(), hasSize(3));
        assertThat(seqOf(0, 1, null).toSet().contains(1), equalTo(true));
        assertThat(seqOf(0, 1, null).toSet(), not(equalTo(seqOf(0, 1, null))));
        assertThat(seqOf(0, 1, null).toSet(),
                equalTo(Stream.of(0, 1, null).collect(toSet())));
        assertThat(seqOf(0, 1, null).toSet(),
                equalTo(Stream.of(null, 1, 0).collect(toSet())));
        assertThat(seqOf(0, 1, null).toSet(),
                not(equalTo(Stream.of(0, 1).collect(toSet()))));
        assertThat(Seq.copy(seqOf(0, 1, null).toSet().spliterator()),
                contains(0, 1, null));
        assertThrows(() -> seqOf(0, 1).toSet().add(2));
    }

    @Test
    public void testToMap() {
        assertThat(seqOf("zero").toMap().size(), equalTo(1));
        assertThat(
                seqOf("zero").toMap(),
                hasEntry("zero", "zero"));
        assertThat(
                seqOf("zero").toMap(s -> s.charAt(0)),
                hasEntry('z', "zero"));
        assertThat(
                seqOf("zero").toMap(s -> s.charAt(0), String::toUpperCase),
                hasEntry('z', "ZERO"));
        assertThat(
                seqOf("zero", null).toMap(), allOf(hasEntry("zero", "zero"),
                        hasEntry((Object) null, null)));
        assertThrows(() ->
                seqOf("zero", "zebra").toMap(s -> s.charAt(0)));
        assertThrows(() -> seqOf("zero").toMap().put("one", "one"));
        assertThat(
                seqOf("zero", "zebra").toMap(
                        s -> s.charAt(0), s -> s, (a, b) -> a + "/" + b),
                hasEntry('z', "zero/zebra"));
        assertThat(
                seqOf("zero", null).toMap(
                        s -> s, s -> s, (a, b) -> a),
                allOf(hasEntry("zero", "zero"), hasEntry((Object) null, null)));
        assertThat(
                Seq.copy(seqOf("zero", "one", "zebra").toMap(
                        s -> s.charAt(0), s -> s, (a, b) -> b).keySet()),
                contains('z', 'o'));
        assertThrows(() -> seqOf("zero")
                .toMap(s -> s, s -> s, (a, b) -> a).put("one", "one"));
    }

    @Test
    public void testListEquals() {
        assertTrue(seqOf().listEquals(seqOf()));
        assertFalse(seqOf().listEquals(seqOf(0)));
        assertFalse(seqOf(0).listEquals(seqOf()));
        assertTrue(seqOf(0).listEquals(seqOf(0)));
        assertTrue(seqOf(0, 3, 1, 4, 2).listEquals(seqOf(0, 3, 1, 4, 2)));
        assertTrue(seqOf(0, null, 1).listEquals(seqOf(0, null, 1)));
        assertFalse(seqOf(0, 1).listEquals(seqOf(0, null)));
        assertFalse(seqOf(0, 1).listEquals(seqOf(1, 0)));
    }

    @Test
    public void testSetEquals() {
        assertTrue(seqOf().setEquals(seqOf()));
        assertFalse(seqOf().setEquals(seqOf(0)));
        assertFalse(seqOf(0).setEquals(seqOf()));
        assertTrue(seqOf(0).setEquals(seqOf(0)));
        assertTrue(seqOf(0, 3, 1, 4, 2).setEquals(seqOf(0, 3, 1, 4, 2)));
        assertTrue(seqOf(0, null, 1).setEquals(seqOf(0, null, 1)));
        assertFalse(seqOf(0, 1).setEquals(seqOf(0, null)));
        assertTrue(seqOf(emptySet()).setEquals(seqOf(emptySortedSet())));
        assertTrue(seqOf(0, 1).setEquals(seqOf(1, 0)));
        assertTrue(seqOf(0, 0).setEquals(seqOf(0)));
        assertTrue(seqOf(0, 0, 1).setEquals(seqOf(0, 1, 1)));
        assertTrue(seqOf(0, 0, null).setEquals(seqOf(0, null, null)));
        assertTrue(seqOf(0, 0, 1).setEquals(seqOf(0, 0, 1)));
        assertTrue(seqOf(0, 0, null).setEquals(seqOf(0, 0, null)));
    }

    @Test
    public void testMultisetEquals() {
        assertTrue(seqOf().multisetEquals(seqOf()));
        assertFalse(seqOf().multisetEquals(seqOf(0)));
        assertFalse(seqOf(0).multisetEquals(seqOf()));
        assertTrue(seqOf(0).multisetEquals(seqOf(0)));
        assertTrue(seqOf(0, 3, 1, 4, 2).multisetEquals(seqOf(0, 3, 1, 4, 2)));
        assertTrue(seqOf(0, null, 1).multisetEquals(seqOf(0, null, 1)));
        assertFalse(seqOf(0, 1).multisetEquals(seqOf(0, null)));
        assertTrue(seqOf(emptySet()).multisetEquals(seqOf(emptySortedSet())));
        assertTrue(seqOf(0, 1).multisetEquals(seqOf(1, 0)));
        assertFalse(seqOf(0, 0).multisetEquals(seqOf(0)));
        assertFalse(seqOf(0, 0, 1).multisetEquals(seqOf(0, 1, 1)));
        assertFalse(seqOf(0, 0, null).multisetEquals(seqOf(0, null, null)));
        assertTrue(seqOf(0, 0, 1).multisetEquals(seqOf(0, 0, 1)));
        assertTrue(seqOf(0, 0, null).multisetEquals(seqOf(0, 0, null)));
    }

    @Test
    public void testReduce() {
        assertThat(seqOf().reduce((a, b) -> a), equalTo(Optional.empty()));
        assertThat(
                seqOf(0, 1, 2).reduce(Integer::sum),
                equalTo(Optional.of(3)));
        assertThat(seqOf().reduce(0, (a, b) -> a), equalTo(0));
        assertThat(seqOf(0, 1, 2).reduce(0, Integer::sum), equalTo(3));
        assertThat(
                seqOf("zero", "one").reduce(0, (a, b) -> a + b.length()),
                equalTo(7));
    }

    @Test
    public void testCollect() {
        assertThat(seqOf(0, 1, 2).collect(toList()), contains(0, 1, 2));
        assertThat(
                seqOf(0, 1, 2).collect(ArrayList::new, ArrayList::add),
                contains(0, 1, 2));
    }

    @Test
    public void testZip() {
        assertThat(
                seqOf(0, 1, 2, 3).zip(seqOf(1, 1, 1, 1), Math::min),
                contains(0, 1, 1, 1));
        assertThat(seqOf(0, 7).zip(seqOf(2, 3, 4), Math::min), contains(0, 3));
        assertThat(seqOf(0, 7, 2).zip(seqOf(3, 4), Math::min), contains(0, 4));
        assertThat(
                seqOf(0, 1, null, null).zip(seqOf(0, null, 2, null), (a, b) ->
                        a == null & b == null ? "both" : a == null ? "a"
                                : b == null ? "b" : "neither"),
                contains("neither", "b", "a", "both"));
    }

    @Test
    public void testIndexes() {
        assertThat(
                seqOf("zero", "one", "two").indexes(),
                contains(0, 1, 2));
    }

    @Test
    public void testIntersection() {
        assertThat(
                seqOf(0, 1).intersection(seqOf(1, 2)),
                contains(1));
        assertThat(
                seqOf(0, 0, 0, 0, 0).intersection(seqOf(0, 0)),
                contains(0, 0));
        assertThat(
                seqOf(null, null, null).intersection(seqOf(null, null)),
                contains((Integer) null, null));
    }

    @Test
    public void testDifference() {
        assertThat(
                seqOf(0, 1).difference(seqOf(1, 2)),
                contains(0));
        assertThat(
                seqOf(0, 0, 0, 0, 0).difference(seqOf(0, 0)),
                contains(0, 0, 0));
        assertThat(
                seqOf(null, null, null).difference(seqOf(null, null)),
                contains((Integer) null));
    }

    @Test
    public void testUnion() {
        assertThat(
                seqOf(0, 1).union(seqOf(1, 2)),
                contains(0, 1, 2));
        assertThat(
                seqOf(0, 0, 0, 0, 0).union(seqOf(0, 0)),
                contains(0, 0, 0, 0, 0));
        assertThat(
                seqOf(null, null, null).union(seqOf(null, null)),
                contains((Integer) null, null, null));
    }

    @Test
    public void testSum() {
        assertThat(
                seqOf(0, 1).sum(seqOf(1, 2)),
                contains(0, 1, 1, 2));
        assertThat(
                seqOf(0, 0, 0, 0, 0).sum(seqOf(0, 0)),
                contains(0, 0, 0, 0, 0, 0, 0));
        assertThat(
                seqOf(null, null, null).sum(seqOf(null, null)),
                contains((Integer) null, null, null, null, null));
    }

    @Test
    public void testContainsMultiset() {
        assertTrue(seqOf(1).containsMultiset(seqOf()));
        assertFalse(seqOf().containsMultiset(seqOf(1)));
        assertTrue(seqOf(1, 2, 3).containsMultiset(seqOf(2, 3)));
        assertFalse(seqOf(0, 1).containsMultiset(seqOf(1, 2)));
        assertTrue(seqOf(0, 0, 0).containsMultiset(seqOf(0, 0)));
        assertFalse(seqOf(0, 0).containsMultiset(seqOf(0, 0, 0)));
        assertTrue(seqOf(null, null, null).containsMultiset(seqOf(null, null)));
    }

    @Test
    public void testPermutations() {
        assertThat(seqOf().permutations(), contains(seqOf()));
        assertThat(seqOf(4).permutations(), contains(seqOf(4)));
        assertThat(seqOf(4, 2).permutations(),
                contains(seqOf(4, 2), seqOf(2, 4)));
        assertThat(seqOf(0, 1, 2, 3, 4).permutations().size(), equalTo(120));
        seqOf(0, 1, 2, 3, 4).permutations().forEach(p ->
                assertThat(p, containsInAnyOrder(0, 1, 2, 3, 4)));
        assertTrue(seqOf(0, 1, 2, 3, 4).permutations()
                .zip(
                        seqOf(0, 1, 2, 3, 4).permutations().skip(1),
                        this::isLexicalOrder)
                .allMatch(b -> b));
        assertThat(seqOf(null, null).permutations(),
                contains(seqOf(null, null), seqOf(null, null)));
    }

    @Test
    public void testCombinations() {
        assertThat(seqOf().combinations(0), contains(seqOf()));
        assertThat(seqOf(4).combinations(0), contains(seqOf()));
        assertThat(seqOf(4).combinations(1), contains(seqOf(4)));
        assertThat(seqOf(4, 2).combinations(0), contains(seqOf()));
        assertThat(seqOf(4, 2).combinations(1), contains(seqOf(4), seqOf(2)));
        assertThat(seqOf(4, 2).combinations(2), contains(seqOf(4, 2)));
        assertThrows(IllegalArgumentException.class,
                "size -1 was negative",
                () -> seqOf(4, 2).combinations(-1));
        assertThrows(IllegalArgumentException.class,
                "size 3 was greater than length 2",
                () -> seqOf(4, 2).combinations(3));
        assertThat(seqOf(0, 1, 2, 3, 4).combinations(2).size(), equalTo(10));
        seqOf(0, 1, 2, 3, 4).combinations(3).forEach(p ->
                assertThat(p, hasSize(3)));
        assertTrue(seqOf(0, 1, 2, 3, 4, 5, 6).combinations(4)
                .zip(
                        seqOf(0, 1, 2, 3, 4, 5, 6).combinations(4).skip(1),
                        this::isLexicalOrder)
                .allMatch(b -> b));
        assertThat(seqOf(null, null).combinations(1),
                contains(seqOf((Object) null), seqOf((Object) null)));
    }

    @Test
    public void testPowerSet() {
        assertThat(seqOf().powerSet(), contains(seqOf()));
        assertThat(seqOf(4).powerSet(), contains(seqOf(), seqOf(4)));
        assertThat(seqOf(4, 2).powerSet(),
                contains(seqOf(), seqOf(4), seqOf(2), seqOf(4, 2)));
        assertThat(seqOf(0, 1, 2, 3, 4).powerSet().size(), equalTo(32));
        seqOf(0, 1, 2, 3, 4).powerSet().forEach(p ->
                assertTrue(seqOf(0, 1, 2, 3, 4).containsMultiset(p)));
        assertTrue(seqOf(0, 1, 2, 3, 4, 5, 6).powerSet()
                .zip(
                        seqOf(0, 1, 2, 3, 4, 5, 6).powerSet().skip(1),
                        (s, t) -> s.size() < t.size() || isLexicalOrder(s, t))
                .allMatch(b -> b));
        assertThat(seqOf(null, null).powerSet(),
                contains(seqOf(), seqOf((Object) null), seqOf((Object) null),
                        seqOf(null, null)));
    }

    @Test
    public void testSlice() {
        assertThat(seqOf(0, 1, null, 3).slice(1, 3), contains(1, null));
        assertThat(seqOf(0, 1, null, 3).slice(3, 1), empty());
        assertThat(seqOf(0, 1, null, 3).slice(1, 5), contains(1, null, 3));
        assertThrows(() -> seqOf(0, 1, null, 3).slice(-1, 3));
        assertThrows(() -> seqOf(0, 1, null, 3).slice(1, -1));
        Integer[] array = {0, 1};
        Seq<Integer> slice = seqOf(array).slice(0, 2);
        array[0] = 4;
        assertThat(slice, contains(0, 1));
    }

    @Test
    public void testIndexesOfSlice() {
        Function<String, Seq<Integer>> toSeq =
                s -> seqOf(s.chars().boxed().toArray(Integer[]::new));
        Seq.of(
                Seq.of("abacabadac", "",
                        Seq.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
                Seq.of("abacabadac", "a", Seq.of(0, 2, 4, 6, 8)),
                Seq.of("abacabadac", "aba", Seq.of(0, 4)),
                Seq.of("abacabadac", "bac", Seq.of(1)),
                Seq.of("abacabadac", "loooooooooooong", Seq.of()),
                Seq.of("abacabadac", "abacabadac", Seq.of(0)),
                Seq.of("abacabadac", "abacabadacx", Seq.of()),
                Seq.of("abacabadac", "xabacabadac", Seq.of()),
                Seq.of("aaa", "", Seq.of(0, 1, 2, 3)),
                Seq.of("aaa", "a", Seq.of(0, 1, 2)),
                Seq.of("aaa", "aa", Seq.of(0, 1)),
                Seq.of("aaa", "aaa", Seq.of(0)),
                Seq.of("aaa", "aaaa", Seq.of()),
                Seq.of("", "def", Seq.of()),
                Seq.of("", "", Seq.of(0))).forEach(args -> {
            Seq<Integer> seq = toSeq.apply((String) args.get(0));
            Seq<Integer> slice = toSeq.apply((String) args.get(1));
            @SuppressWarnings("unchecked")
            Seq<Integer> indexes = (Seq<Integer>) args.get(2);
            assertThat(seq.indexesOfSlice(slice),
                    equalTo(indexes));
            assertThat(seq.indexOfSlice(slice),
                    equalTo(indexes.findFirst().orElse(-1)));
            assertThat(seq.lastIndexOfSlice(slice),
                    equalTo(indexes.findLast().orElse(-1)));
            assertThat(seq.containsSlice(slice),
                    equalTo(!indexes.isEmpty()));
            assertThat(seq.startsWith(slice),
                    equalTo(indexes.findFirst()
                            .map(i -> i == 0).orElse(false)));
            assertThat(seq.endsWith(slice),
                    equalTo(indexes.findLast()
                            .map(i -> i == seq.size() - slice.size())
                            .orElse(false)));
        });
        assertThat(seqOf(3, null, 4).indexesOfSlice(seqOf(null, 4)),
                equalTo(Seq.of(1)));
    }

    @Test
    public void testIndexOfSlice() {
        // see testIndexesOfSlice()
    }

    @Test
    public void testLastIndexOfSlice() {
        // see testIndexesOfSlice()
    }

    @Test
    public void testContainsSlice() {
        // see testIndexesOfSlice()
    }

    @Test
    public void testStartsWith() {
        // see testIndexesOfSlice()
    }

    @Test
    public void testEndsWith() {
        // see testIndexesOfSlice()
    }

    @Test
    public void testGet() {
        assertThat(seqOf(0, 1, 2).get(2), equalTo(2));
        assertThat(seqOf(0, null, 2).get(1), equalTo(null));
        assertThrows(IndexOutOfBoundsException.class,
                () -> seqOf(0, 1, 2).get(-1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> seqOf(0, 1, 2).get(3));
    }

    @Test
    public void testIndexOf() {
        Seq<String> words = seqOf(
                "the quick brown fox jumps over the lazy dog".split(" "));
        assertThat(words.indexOf("the"), equalTo(0));
        assertThat(words.indexOf("on"), equalTo(-1));
        assertThat(seqOf(0, null, null, 3).indexOf(null), equalTo(1));
    }

    @Test
    public void testLastIndexOf() {
        Seq<String> words = seqOf(
                "the quick brown fox jumps over the lazy dog".split(" "));
        assertThat(words.lastIndexOf("the"), equalTo(6));
        assertThat(words.lastIndexOf("on"), equalTo(-1));
        assertThat(seqOf(0, null, null, 3).lastIndexOf(null), equalTo(2));
    }

    @Test
    public void testIndexesOf() {
        Seq<String> words = seqOf(
                "the quick brown fox jumps over the lazy dog".split(" "));
        assertThat(words.indexesOf("the"), contains(0, 6));
        assertThat(words.indexesOf("on"), empty());
        assertThat(seqOf(0, null, null, 3).indexesOf(null), contains(1, 2));
    }

    @Test
    public void testFindOnly() {
        assertThat(seqOf().findOnly(), equalTo(Optional.empty()));
        assertThat(seqOf(0).findOnly(), equalTo(Optional.of(0)));
        assertThat(seqOf(0, 1).findOnly(), equalTo(Optional.empty()));
    }

    @Test
    public void testFindLast() {
        assertThat(seqOf().findLast(), equalTo(Optional.empty()));
        assertThat(seqOf(0).findLast(), equalTo(Optional.of(0)));
        assertThat(seqOf(0, 1).findLast(), equalTo(Optional.of(1)));
    }

    @Test
    public void testReversed() {
        assertThat(seqOf().reversed(), empty());
        assertThat(seqOf(0, null, 1).reversed(), contains(1, null, 0));
    }

    @Test
    public void testRotated() {
        Seq<Integer> nums = seqOf(0, 1, 2, null, 4);
        assertThat(nums.rotated(1), contains(4, 0, 1, 2, null));
        assertThat(nums.rotated(-2), contains(2, null, 4, 0, 1));
    }

    @Test
    public void testShuffled() {
        Seq<Integer> nums = seqOf(0, null, 2, 3, 4);
        List<Integer> copy = new ArrayList<>(nums);
        Collections.shuffle(copy, new Random(0));
        assertThat(
                nums.shuffled(new Random(0)).toList(),
                equalTo(copy));
    }

    @Test
    public void testLimitLast() {
        assertThat(seqOf().limitLast(2), empty());
        assertThat(seqOf(0, null, 2).limitLast(0), empty());
        assertThat(seqOf(0, null, 2).limitLast(3), contains(0, null, 2));
        assertThat(seqOf(0, null, 2).limitLast(5), contains(0, null, 2));
        assertThat(seqOf(0, null, 2, 3, 4).limitLast(2), contains(3, 4));
        assertThat(seqOf(0, null, 2, 3, 4, 5).limitLast(3), contains(3, 4, 5));
        assertThrows(IllegalArgumentException.class,
                () -> seqOf(0, null, 2).limitLast(-2));
        // the queue is sized from the data, not from the argument
        assertThat(seqOf(0, null, 2).limitLast(200_000_000),
                contains(0, null, 2));
        assertThat(seqOf(0, null, 2).limitLast(Long.MAX_VALUE),
                contains(0, null, 2));
        // more elements than the queue holds at first, but fewer than size,
        // so it must still be growing rather than overwriting
        assertThat(seqOf(0, 1, 2, 3, 4, 5).limitLast(1000),
                contains(0, 1, 2, 3, 4, 5));
        assertThat(seqOf(0, 1, 2, 3, 4, 5).limitLast(5),
                contains(1, 2, 3, 4, 5));
    }

    @Test
    public void testSkipLast() {
        assertThat(seqOf().skipLast(2), empty());
        assertThat(seqOf(0, null, 2).skipLast(0), contains(0, null, 2));
        assertThat(seqOf(0, null, 2).skipLast(3), empty());
        assertThat(seqOf(0, null, 2).skipLast(5), empty());
        assertThat(seqOf(0, null, 2, 3, 4).skipLast(2), contains(0, null, 2));
        assertThat(seqOf(0, null, 2, 3, 4, 5).skipLast(3),
                contains(0, null, 2));
        assertThrows(IllegalArgumentException.class,
                () -> seqOf(0, null, 2).skipLast(-2));
        assertThat(seqOf(0, null, 2).skipLast(200_000_000), empty());
        assertThat(seqOf(0, null, 2).skipLast(Long.MAX_VALUE), empty());
        assertThat(seqOf(0, 1, 2, 3, 4, 5).skipLast(5), contains(0));
    }

    @Test
    public void testTakeWhile() {
        assertThat(seqOf().takeWhile(n -> false), empty());
        assertThat(seqOf().takeWhile(n -> true), empty());
        Seq<Integer> numbers = seqOf(0, null, 2, 1, 0);
        assertThat(numbers.takeWhile(n -> false), empty());
        assertThat(numbers.takeWhile(n -> true), contains(0, null, 2, 1, 0));
        assertThat(numbers.takeWhile(n -> n == null || n < 2),
                contains(0, null));
        Spliterator<Integer> s = numbers.stream().takeWhile(
                n -> n == null || n < 2).spliterator();
        assertTrue(s.tryAdvance(e -> {}));
        assertTrue(s.tryAdvance(e -> {}));
        assertFalse(s.tryAdvance(e -> {}));
        assertFalse(s.tryAdvance(e -> {})); // code coverage
    }

    @Test
    public void testDropWhile() {
        assertThat(seqOf().dropWhile(n -> false), empty());
        assertThat(seqOf().dropWhile(n -> true), empty());
        Seq<Integer> numbers = seqOf(0, null, 2, 1, 0);
        assertThat(numbers.dropWhile(n -> false), contains(0, null, 2, 1, 0));
        assertThat(numbers.dropWhile(n -> true), empty());
        assertThat(numbers.dropWhile(n -> n == null || n < 2),
                contains(2, 1, 0));
    }

    @Test
    public void testFilter() {
        assertThat(seqOf(0, 1, 2, 3).filter(n -> n % 3 == 0), contains(0, 3));
        assertThat(seqOf(0, null, 2).filter(Objects::nonNull), contains(0, 2));
    }

    @Test
    public void testMap() {
        assertThat(seqOf().map(identity()), empty());
        assertThat(seqOf(0, 1, 2).map(n -> n * 2), contains(0, 2, 4));
        assertThat(seqOf(0, null).map(Objects::isNull), contains(false, true));
        assertThat(
                seqOf("", null).map(s -> s == null ? "" : null),
                contains(null, ""));
    }

    @Test
    public void testFlatMap() {
        assertThat(
                seqOf(seqOf(0, 1, 2), seqOf(null, 4))
                        .flatMap(identity()),
                contains(0, 1, 2, null, 4));
        assertThat(
                seqOf(seqOf(0, 1, 2), null)
                        .flatMap(identity()),
                contains(0, 1, 2));
    }

    @Test
    public void testMapMulti() {
        assertThat(
                seqOf(0, 1, 2).<Integer>mapMulti((n, sink) -> {
                    sink.accept(n);
                    sink.accept(-n);
                }),
                contains(0, 0, 1, -1, 2, -2));
        assertThat(
                seqOf(0, 1, 2).<Integer>mapMulti((n, sink) -> {}),
                empty());
        assertThat(
                seqOf(0, null).<Integer>mapMulti((n, sink) -> sink.accept(n)),
                contains(0, null));
        assertThat(
                seqOf().<Integer>mapMulti((n, sink) -> sink.accept(0)),
                empty());
        assertThrows(() -> seqOf(0).mapMulti(null));
    }

    @Test
    public void testDistinct() {
        assertThat(seqOf(3, 2, null, 2, 3, 1, 1).distinct(),
                contains(3, 2, null, 1));
    }

    @Test
    public void testSorted() {
        assertThat(seqOf(2, 3, 1, 0, 4).sorted(),
                contains(0, 1, 2, 3, 4));
        assertThat(seqOf("two", "three", "one", "zero", "four")
                        .sorted(comparing(String::length)),
                contains("two", "one", "zero", "four", "three"));
    }

    @Test
    public void testLimit() {
        assertThat(seqOf(0, null, 2).limit(2), contains(0, null));
        assertThat(seqOf(0, null, 2).limit(5), contains(0, null, 2));
        assertThrows(IllegalArgumentException.class,
                () -> seqOf(0, null, 2).limit(-2));
        // Stream.limit accepts any non-negative long, so Seq must too
        assertThat(seqOf(0, null, 2).limit(Long.MAX_VALUE),
                contains(0, null, 2));
    }

    @Test
    public void testSkip() {
        assertThat(seqOf(0, null, 2).skip(2), contains(2));
        assertThat(seqOf(0, null, 2).skip(5), empty());
        assertThrows(IllegalArgumentException.class,
                () -> seqOf(0, null, 2).skip(-2));
        assertThat(seqOf(0, null, 2).skip(Long.MAX_VALUE), empty());
    }

    @Test
    public void testForEach() {
        int[] total = new int[1];
        seqOf(0, 1, 2).forEach(n -> total[0] += n);
        assertThat(total[0], equalTo(3));
    }

    @Test
    public void testForEachOrdered() {
        int[] total = new int[1];
        seqOf(0, 1, 2).forEachOrdered(n -> total[0] += n);
        assertThat(total[0], equalTo(3));
    }

    @Test
    public void testToArray() {
        Seq<Integer> seq = seqOf(0, 1, 2);
        assertThat(seq.toArray(), arrayContaining(0, 1, 2));
        assertThat(seq.toArray().getClass().getComponentType(),
                equalTo(Object.class));
        assertThat(seq.toArray(Integer[]::new), arrayContaining(0, 1, 2));
        assertThat(seq.toArray(Integer[]::new).getClass().getComponentType(),
                equalTo(Integer.class));
        assertThat(seq.toArray(new Integer[0]), arrayContaining(0, 1, 2));
        assertThat(seq.toArray(new Integer[0]).getClass().getComponentType(),
                equalTo(Integer.class));
        assertThat(seq.toArray(new Integer[5]), arrayWithSize(5));
        assertThat(seq.toArray(new Integer[5])[2], equalTo(2));
        assertThat(seq.toArray(new Integer[5])[3], equalTo(null));
        Integer[] target = new Integer[seq.size()];
        assertThat(seq.toArray(target), arrayContaining(0, 1, 2));
        assertThat(seq.toArray(target), sameInstance(target));
        assertThat(seq.limit(1).toArray(target), arrayContaining(0, null, 2));
        assertThat(seq.limit(1).toArray(target), sameInstance(target));
        assertThat(seqOf(null, 1).toArray(), arrayContaining(null, 1));
    }

    @Test
    public void testMin() {
        assertThat(
                this.<Integer>seqOf().min(naturalOrder()),
                equalTo(Optional.empty()));
        assertThat(
                seqOf(2, 3, 1, 4, 9, 7).min(naturalOrder()),
                equalTo(Optional.of(1)));
        assertThat(
                seqOf("quick", "brown", "fox", "jumped")
                        .min(comparing(String::length)),
                equalTo(Optional.of("fox")));
    }

    @Test
    public void testMax() {
        assertThat(
                this.<Integer>seqOf().max(naturalOrder()),
                equalTo(Optional.empty()));
        assertThat(
                seqOf(2, 3, 1, 4, 9, 7).max(naturalOrder()),
                equalTo(Optional.of(9)));
        assertThat(
                seqOf("quick", "brown", "fox", "jumped")
                        .max(comparing(String::length)),
                equalTo(Optional.of("jumped")));
    }

    @Test
    public void testCount() {
        assertThat(seqOf(0, null, 2).count(), equalTo(3L));
    }

    @Test
    public void testAnyMatch() {
        assertFalse(seqOf().anyMatch(n -> true));
        assertTrue(seqOf(0, 1, 2).anyMatch(n -> n == 0));
        assertFalse(seqOf(0, 1, 2).anyMatch(n -> n == 3));
    }

    @Test
    public void testAllMatch() {
        assertTrue(seqOf().allMatch(n -> false));
        assertTrue(seqOf(0, 1, 2).allMatch(n -> n < 3));
        assertFalse(seqOf(0, 1, 2).allMatch(n -> n == 0));
    }

    @Test
    public void testNoneMatch() {
        assertTrue(seqOf().noneMatch(n -> true));
        assertTrue(seqOf(0, 1, 2).noneMatch(n -> n == 3));
        assertFalse(seqOf(0, 1, 2).noneMatch(n -> n == 0));
    }

    @Test
    public void testFindFirst() {
        assertThat(seqOf().findFirst(), equalTo(Optional.empty()));
        assertThat(seqOf(0).findFirst(), equalTo(Optional.of(0)));
        assertThat(seqOf(0, 1).findFirst(), equalTo(Optional.of(0)));
    }

    @Test
    public void testFindAny() {
        assertThat(seqOf().findAny(), equalTo(Optional.empty()));
        assertThat(seqOf(0).findAny(), equalTo(Optional.of(0)));
        assertThat(seqOf(0, 1).findAny(),
                isIn(seqOf(Optional.of(0), Optional.of(1))));
    }

    @Test
    public void testPeek() {
        int[] total = new int[1];
        // eager, so each peek runs over every element before the next peek
        // starts; lazily (ie stream().peek(..).peek(..).toSeq()) the peeks
        // would interleave per element and the result would be 27
        seqOf(1, 2, 3)
                .peek(n -> total[0] += n)
                .peek(n -> total[0] *= n);
        assertThat(total[0], allOf(equalTo(36), not(equalTo(27))));
    }

    @Test
    public void testSize() {
        assertThat(seqOf().size(), equalTo(0));
        assertThat(seqOf(0, 1, 2).size(), equalTo(3));
        assertThat(seqOf(0, null).size(), equalTo(2));
    }

    @Test
    public void testIsEmpty() {
        assertFalse(seqOf(1, null, 3).isEmpty());
        assertTrue(seqOf().isEmpty());
    }

    @Test
    public void testContains() {
        Seq<String> words = seqOf(
                "the quick brown fox jumps over the lazy dog".split(" "));
        assertTrue(words.contains("the"));
        assertFalse(words.contains("on"));
        assertTrue(seqOf(0, null, null, 3).contains(null));
    }

    @Test
    public void testAdd() {
        assertThrows(() -> seqOf(0).add(1));
    }

    @Test
    public void testRemove() {
        assertThrows(() -> seqOf(0).remove(0));
    }

    @Test
    public void testContainsAll() {
        assertTrue(seqOf(0, 1, 2).containsAll(Seq.of(0, 1)));
        assertFalse(seqOf(0, 1, 2).containsAll(Seq.of(2, 3)));
        assertTrue(seqOf(0).containsAll(Seq.of(0, 0)));
        assertTrue(seqOf(0, 0).containsAll(Seq.of(0)));
        assertTrue(seqOf(0, null).containsAll(Seq.of(0)));
        assertTrue(seqOf(0, null).containsAll(Seq.of((Integer) null)));
        assertTrue(seqOf(0, null).containsAll(Seq.of(0, null)));
        assertFalse(seqOf(0).containsAll(Seq.of(0, null)));
        assertFalse(seqOf((Integer) null).containsAll(Seq.of(0, null)));
    }

    @Test
    public void testAddAll() {
        assertThrows(() -> seqOf(0).addAll(Seq.of(1, 2)));
    }

    @Test
    public void testRemoveAll() {
        assertThrows(() -> seqOf(0).removeAll(Seq.of(0)));
    }

    @Test
    public void testRemoveIf() {
        assertThrows(() -> seqOf(0).removeIf(n -> n == 0));
    }

    @Test
    public void testRetainAll() {
        assertThrows(() -> seqOf(0).retainAll(Seq.of(0)));
    }

    @Test
    public void testClear() {
        assertThrows(() -> seqOf(0).clear());
    }

    @Test
    public void testSpliterator() {
        Spliterator<String> spliterator = seqOf("the", "fox").spliterator();
        assertTrue(spliterator.tryAdvance(s -> assertThat(s, equalTo("the"))));
        assertTrue(spliterator.tryAdvance(s -> assertThat(s, equalTo("fox"))));
        assertFalse(spliterator.tryAdvance(s -> {}));
    }

    @Test
    public void testStream() {
        assertThat(seqOf(0, 1, 2).stream().toSeq(), contains(0, 1, 2));
    }

    @Test
    public void testParallelStream() {
        assertThat(seqOf(0, 1).parallelStream().toSeq(), contains(0, 1));
    }

    @Test
    public void testIterator() {
        Iterator<String> iterator = seqOf("the", "fox").iterator();
        assertTrue(iterator.hasNext());
        assertThat(iterator.next(), equalTo("the"));
        assertTrue(iterator.hasNext());
        assertThat(iterator.next(), equalTo("fox"));
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testHashCode() {
        for (Seq<?> seq : seqOf(
                seqOf(),
                seqOf((Integer) null),
                seqOf(10),
                seqOf(3, 7),
                seqOf(7, 3),
                seqOf("the", "quick", "brown"))) {
            assertThat(seq.hashCode(),
                    equalTo(new ArrayList<>(seq).hashCode()));
        }
    }

    @Test
    public void testEquals() {
        Seq<Object> instance = seqOf();
        assertTrue(instance.equals(instance));
        assertFalse(seqOf().equals(null));
        assertFalse(seqOf().equals(Arrays.asList()));
        assertTrue(seqOf().equals(seqOf()));
        assertFalse(seqOf().equals(seqOf(0)));
        assertFalse(seqOf(0).equals(seqOf()));
        assertTrue(seqOf(0).equals(seqOf(0)));
        assertTrue(seqOf(0, 3, 1, 4, 2).equals(seqOf(0, 3, 1, 4, 2)));
        assertTrue(seqOf(0, null, 1).equals(seqOf(0, null, 1)));
        assertFalse(seqOf(0, 1).equals(seqOf(0, null)));
        assertTrue(seqOf(emptySet()).equals(seqOf(emptySortedSet())));
    }

    @Test
    public void testToString() {
        assertThat(seqOf().toString(), equalTo("[]"));
        assertThat(seqOf(0).toString(), equalTo("[0]"));
        assertThat(seqOf(0, 1).toString(), equalTo("[0, 1]"));
        assertThat(seqOf(true, false).toString("|", "<", ">"),
                equalTo("<true|false>"));
        assertThat(seqOf("fox", null).toString(), equalTo("[fox, null]"));
    }

    @SafeVarargs
    private final <E> Seq<E> seqOf(E... elements) {
        return factory.create(elements);
    }

    private boolean isLexicalOrder(Seq<Integer> s, Seq<Integer> t) {
        return s.zip(t, (i, j) -> i - j)
                .filter(n -> n != 0).findFirst()
                .map(n -> n < 0).orElse(true);
    }

    private interface Factory {
        <E> Seq<E> create(E[] elements);
    }

    private static class TestDelegatingSeq<E>
            extends AbstractSeq<E> implements DelegatingSeq<E> {

        private final E[] elements;

        @SafeVarargs
        public TestDelegatingSeq(E... elements) {
            this.elements = elements;
        }

        public Spliterator<E> spliterator() {
            return Arrays.spliterator(elements);
        }
    }
}
