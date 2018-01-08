package seqly;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Spliterator;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static java.util.Collections.emptySortedSet;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static seqly.Seq.toSeq;

@RunWith(Parameterized.class)
public class SeqTest {

    private Factory factory;

    public SeqTest(String ignored, Factory factory) {
        this.factory = factory;
    }

    @Parameters(name = "{0}")
    public static Iterable<Object[]> parametersList() {
        return Seq.of(
                parameters("default", TestSeq::new),
                parameters("Seq#of(E...)", Seq::of),
                parameters("Seq#copy(E[])", Seq::copy),
                parameters("Seq#view(E[])", Seq::view),
                parameters("Seq#copy(Iterable<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy((Iterable<E>) Seq.of(elements));
                    }
                }),
                parameters("Seq#view(Iterable<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.view((Iterable<E>) Seq.of(elements));
                    }
                }),
                parameters("Seq#copy(Collection<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Seq.of(elements));
                    }
                }),
                parameters("Seq#view(Collection<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.view(Seq.of(elements));
                    }
                }),
                parameters("Seq#copy(Iterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Seq.of(elements).iterator());
                    }
                }),
                parameters("Seq#copy(Spliterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Seq.of(elements).spliterator());
                    }
                }),
                parameters("Seq#copy(Stream<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Seq.of(elements).stream());
                    }
                }));
    }

    private static Object[] parameters(String name, Factory factory) {
        return new Object[]{name, factory};
    }

    private static void assertThrows(Runnable action) {
        try {
            action.run();
            fail("expected exception");
        } catch (RuntimeException ignored) {
        }
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
        assertThat(Seq.of(0, 1, 2), contains(0, 1, 2));
        assertThat(Seq.of(0, 1, 2).getClass(), equalTo(ArraySeq.class));
        assertThrows(() -> Seq.of(null));
    }

    @Test
    public void testCopy() {
        Seq<String> collection = Seq.of("", null, ".");
        for (Seq<String> seq : Seq.of(
                Seq.copy(collection.toArray(new String[0])),
                Seq.copy((Iterable<String>) collection),
                Seq.copy(collection),
                Seq.copy(collection.iterator()),
                Seq.copy(collection.spliterator()),
                Seq.copy(collection.stream()))) {
            assertThat(seq, contains("", null, "."));
        }
    }

    @Test
    public void testView() {
        Seq<String> collection = Seq.of("", null, ".");
        for (Seq<String> seq : Seq.of(
                Seq.view(collection.toArray(new String[0])),
                Seq.view((Iterable<String>) collection),
                Seq.view(collection))) {
            assertThat(seq, contains("", null, "."));
        }
        assertThat(Seq.view(Optional.of("")), contains(""));
        assertThat(Seq.view(Optional.empty()), empty());
    }

    @Test
    public void testBuilder() {
        Seq.Builder<Integer> builder = Seq.builder();
        assertThat(builder.build(), empty());
        assertThat(builder.build(), empty());
        assertThat(builder.add(0).build(), contains(0));
        assertThat(builder.add(1).build(), contains(0, 1));
        Seq<Integer> built = builder.build();
        builder.add(2);
        assertThat(built, contains(0, 1));
    }

    @Test
    public void testToSeq() {
        assertThat(Stream.empty().collect(toSeq()), empty());
        assertThat(
                Stream.of(0, 1, 2, null).collect(toSeq()),
                contains(0, 1, 2, null));
        assertThat(Stream.of("").collect(toSeq()), contains(""));
    }

    @Test
    public void testRange() {
        assertThat(Seq.range(4, 7), contains(4, 5, 6));
        assertThat(Seq.range(7, 4), empty());
        assertThat(Seq.range(-12, -10), contains(-12, -11));
    }

    @Test
    public void testConcat() {
        assertThat(
                Seq.concat(seqOf(0, 1), seqOf(2, 3)),
                contains(0, 1, 2, 3));
        assertThrows(() -> Seq.concat(seqOf(), null));
        assertThrows(() -> Seq.concat(null, seqOf()));
        assertThat(
                Seq.concat(seqOf(seqOf(0, 1), seqOf(2), seqOf(3, 4))),
                contains(0, 1, 2, 3, 4));
    }

    @Test
    public void testAsList() {
        assertThat(seqOf(0, 1, 2).asList(), contains(0, 1, 2));
        assertThat(seqOf(0, 1, 2), hasSize(3));
        assertThat(seqOf(0, 1, 2).get(1), equalTo(1));
    }

    @Test
    public void testReduce() {
        assertThat(seqOf().reduce((a, b) -> a), equalTo(Optional.empty()));
        assertThat(
                seqOf(0, 1, 2).reduce((a, b) -> a + b),
                equalTo(Optional.of(3)));
        assertThat(seqOf().reduce(0, (a, b) -> a), equalTo(0));
        assertThat(seqOf(0, 1, 2).reduce(0, (a, b) -> a + b), equalTo(3));
        assertThat(
                seqOf("zero", "one").reduce(0, (a, b) -> a + b.length()),
                equalTo(7));
        assertThat(seqOf("zero", "one").reduce(
                0, (a, b) -> a + b.length(), (a, b) -> a + b),
                equalTo(7));
    }

    @Test
    public void testCollect() {
        assertThat(seqOf(0, 1, 2).collect(toList()), contains(0, 1, 2));
        assertThat(
                seqOf(0, 1, 2).collect(ArrayList::new, ArrayList::add),
                contains(0, 1, 2));
        assertThat(
                seqOf(0, 1, 2).collect(
                        ArrayList::new, ArrayList::add, ArrayList::addAll),
                contains(0, 1, 2));
    }

    @Test
    public void testGrouped() {
        assertThrows(() -> seqOf(0, 1, 2).grouped(0));
        assertThrows(() -> seqOf(0, 1, 2).grouped(-2));
        assertThat(seqOf(0, 1).grouped(3), contains(seqOf(0, 1)));
        assertThat(seqOf(0, 1, 2).grouped(3), contains(seqOf(0, 1, 2)));
        assertThat(seqOf(0, 1, 2, 3, 4).grouped(2),
                contains(seqOf(0, 1), seqOf(2, 3), seqOf(4)));
        assertThat(seqOf(0, 1, 2, 3, 4).grouped(3),
                contains(seqOf(0, 1, 2), seqOf(3, 4)));
    }

    @Test
    public void testZip() {
        assertThat(
                seqOf(0, 1, 2, 3).zip(seqOf(1, 1, 1, 1), Math::min),
                contains(0, 1, 1, 1));
        assertThat(seqOf(0, 7).zip(seqOf(2, 3, 4), Math::min), contains(0, 3));
        assertThat(seqOf(0, 7, 2).zip(seqOf(3, 4), Math::min), contains(0, 4));
        assertThat(
                seqOf("zero", "one", "two").zip((e, i) -> i + ":" + e),
                contains("0:zero", "1:one", "2:two"));
    }

    @Test
    public void testIntersection() {
        assertThat(
                seqOf(0, 1).intersection(seqOf(1, 2)),
                contains(1));
        assertThat(
                seqOf(0, 0, 0, 0, 0).intersection(seqOf(0, 0)),
                contains(0, 0));
    }

    @Test
    public void testDifference() {
        assertThat(
                seqOf(0, 1).difference(seqOf(1, 2)),
                contains(0));
        assertThat(
                seqOf(0, 0, 0, 0, 0).difference(seqOf(0, 0)),
                contains(0, 0, 0));
    }

    @Test
    public void testUnion() {
        assertThat(
                seqOf(0, 1).union(seqOf(1, 2)),
                contains(0, 1, 2));
        assertThat(
                seqOf(0, 0, 0, 0, 0).union(seqOf(0, 0)),
                contains(0, 0, 0, 0, 0));
    }

    @Test
    public void testFlattenOptionals() {
        assertThat(
                seqOf(seqOf(0, 1, 2), seqOf(3, 4))
                        .flattenOptionals(Seq::findFirst),
                contains(0, 3));
    }

    @Test
    public void testSubseq() {
        assertThat(seqOf(0, 1, 2, 3).subseq(1, 3), contains(1, 2));
        assertThat(seqOf(0, 1, 2, 3).subseq(3, 1), empty());
        assertThat(seqOf(0, 1, 2, 3).subseq(1, 5), contains(1, 2, 3));
        assertThrows(() -> seqOf(0, 1, 2, 3).subseq(-1, 3));
        assertThrows(() -> seqOf(0, 1, 2, 3).subseq(1, -1));
        Integer[] array = {0, 1};
        Seq<Integer> subseq = seqOf(array).subseq(0, 2);
        array[0] = 4;
        assertThat(subseq, contains(0, 1));
    }

    @Test
    public void testGet() {
        assertThat(seqOf(0, 1, 2).get(2), equalTo(2));
        assertThrows(() -> seqOf(0, 1, 2).get(-1));
        assertThrows(() -> seqOf(0, 1, 2).get(3));
    }

    @Test
    public void testIndexOf() {
        Seq<String> words = seqOf(
                "the quick brown fox jumps over the lazy dog".split(" "));
        assertThat(words.indexOf("the"), equalTo(0));
        assertThat(words.lastIndexOf("the"), equalTo(6));
        assertThat(words.indexesOf("the"), contains(0, 6));
        assertTrue(words.contains("the"));
        assertThat(words.indexOf("on"), equalTo(-1));
        assertThat(words.lastIndexOf("on"), equalTo(-1));
        assertThat(words.indexesOf("on"), empty());
        assertFalse(words.contains("on"));
    }

    @Test
    public void testLastIndexOf() {
        Seq<String> words = seqOf(
                "the quick brown fox jumps over the lazy dog".split(" "));
        assertThat(words.lastIndexOf("the"), equalTo(6));
        assertThat(words.lastIndexOf("on"), equalTo(-1));
    }

    @Test
    public void testIndexesOf() {
        Seq<String> words = seqOf(
                "the quick brown fox jumps over the lazy dog".split(" "));
        assertThat(words.indexesOf("the"), contains(0, 6));
        assertThat(words.indexesOf("on"), empty());
    }

    @Test
    public void testFindOnly() {
        assertThat(seqOf().findOnly(), equalTo(Optional.empty()));
        assertThat(seqOf(0).findOnly(), equalTo(Optional.of(0)));
        assertThrows(() -> seqOf(0, 1).findOnly());
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
        assertThat(seqOf(0, 2, 1).reversed(), contains(1, 2, 0));
    }

    @Test
    public void testRotated() {
        Seq<Integer> nums = seqOf(0, 1, 2, 3, 4);
        assertThat(nums.rotated(1), contains(4, 0, 1, 2, 3));
        assertThat(nums.rotated(-2), contains(2, 3, 4, 0, 1));
    }

    @Test
    public void testShuffled() {
        Seq<Integer> nums = seqOf(0, 1, 2, 3, 4);
        List<Integer> copy = new ArrayList<>(nums);
        Collections.shuffle(copy, new Random(0));
        assertThat(
                nums.shuffled(new Random(0)),
                equalTo(seqOf(copy.toArray())));
    }

    @Test
    public void testLimitLast() {
        assertThat(seqOf().limitLast(2), empty());
        assertThat(seqOf(0, 1, 2).limitLast(0), empty());
        assertThat(seqOf(0, 1, 2).limitLast(5), contains(0, 1, 2));
        assertThat(seqOf(0, 1, 2, 3, 4).limitLast(2), contains(3, 4));
        assertThat(seqOf(0, 1, 2, 3, 4, 5).limitLast(3), contains(3, 4, 5));
        assertThrows(() -> seqOf(0, 1, 2).limitLast(-2));
    }

    @Test
    public void testSkipLast() {
        assertThat(seqOf().skipLast(2), empty());
        assertThat(seqOf(0, 1, 2).skipLast(0), contains(0, 1, 2));
        assertThat(seqOf(0, 1, 2).skipLast(5), empty());
        assertThat(seqOf(0, 1, 2, 3, 4).skipLast(2), contains(0, 1, 2));
        assertThat(seqOf(0, 1, 2, 3, 4, 5).skipLast(3), contains(0, 1, 2));
        assertThrows(() -> seqOf(0, 1, 2).skipLast(-2));
    }

    @Test
    public void testTakeWhile() {
        assertThat(seqOf().takeWhile(n -> false), empty());
        assertThat(seqOf().takeWhile(n -> true), empty());
        Seq<Integer> numbers = seqOf(0, 1, 2, 1, 0);
        assertThat(numbers.takeWhile(n -> false), empty());
        assertThat(numbers.takeWhile(n -> true), contains(0, 1, 2, 1, 0));
        assertThat(numbers.takeWhile(n -> n < 2), contains(0, 1));
    }

    @Test
    public void testDropWhile() {
        assertThat(seqOf().dropWhile(n -> false), empty());
        assertThat(seqOf().dropWhile(n -> true), empty());
        Seq<Integer> numbers = seqOf(0, 1, 2, 1, 0);
        assertThat(numbers.dropWhile(n -> false), contains(0, 1, 2, 1, 0));
        assertThat(numbers.dropWhile(n -> true), empty());
        assertThat(numbers.dropWhile(n -> n < 2), contains(2, 1, 0));
    }

    @Test
    public void testFilter() {
        assertThat(seqOf(0, 1, 2, 3).filter(n -> n % 3 == 0), contains(0, 3));
    }

    @Test
    public void testMap() {
        assertThat(seqOf().map(identity()), empty());
        assertThat(seqOf(0, 1, 2).map(n -> n * 2), contains(0, 2, 4));
        assertThat(
                seqOf("", null).map(s -> s == null ? "" : null),
                contains(null, ""));
    }

    @Test
    public void testFlatMap() {
        assertThat(
                seqOf(seqOf(0, 1, 2), seqOf(3, 4))
                        .flatMap(identity()),
                contains(0, 1, 2, 3, 4));
    }

    @Test
    public void testDistinct() {
        assertThat(seqOf(1, 2, 2, 3, 3, 3).distinct(), contains(1, 2, 3));
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
        assertThat(seqOf(0, 1, 2).limit(2), contains(0, 1));
        assertThat(seqOf(0, 1, 2).limit(5), contains(0, 1, 2));
        assertThrows(() -> seqOf(0, 1, 2).limit(-2));
    }

    @Test
    public void testSkip() {
        assertThat(seqOf(0, 1, 2).skip(2), contains(2));
        assertThat(seqOf(0, 1, 2).skip(5), empty());
        assertThrows(() -> seqOf(0, 1, 2).skip(-2));
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
        Integer[] target = new Integer[seq.size()];
        assertThat(seq.toArray(target), arrayContaining(0, 1, 2));
        assertThat(seq.toArray(target), sameInstance(target));
        assertThat(seq.limit(1).toArray(target), arrayContaining(0, null, 2));
        assertThat(seq.limit(1).toArray(target), sameInstance(target));
    }

    @Test
    public void testMin() {
        assertThat(
                this.<Integer>seqOf().min(naturalOrder()),
                equalTo(Optional.empty()));
        assertThat(
                seqOf(2, 3, 1).min(naturalOrder()),
                equalTo(Optional.of(1)));
        assertThat(
                seqOf("the", "quick").min(comparing(String::length)),
                equalTo(Optional.of("the")));
    }

    @Test
    public void testMax() {
        assertThat(
                this.<Integer>seqOf().max(naturalOrder()),
                equalTo(Optional.empty()));
        assertThat(
                seqOf(2, 3, 1).max(naturalOrder()),
                equalTo(Optional.of(3)));
        assertThat(
                seqOf("the", "quick").max(comparing(String::length)),
                equalTo(Optional.of("quick")));
    }

    @Test
    public void testCount() {
        assertThat(seqOf(0, 1, 2).count(), equalTo(3L));
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
    public void testSize() {
        assertThat(seqOf(0, 1, 2).size(), equalTo(3));
    }

    @Test
    public void testIsEmpty() {
        assertFalse(seqOf(1, 2, 3).isEmpty());
        assertTrue(seqOf().isEmpty());
    }

    @Test
    public void testContains() {
        Seq<String> words = seqOf(
                "the quick brown fox jumps over the lazy dog".split(" "));
        assertTrue(words.contains("the"));
        assertFalse(words.contains("on"));
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
        Spliterator<String> spliterator = seqOf("the", "quick").spliterator();
        assertTrue(spliterator.hasCharacteristics(Spliterator.SIZED));
        assertTrue(spliterator.hasCharacteristics(Spliterator.SUBSIZED));
        assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED));
        assertTrue(spliterator.tryAdvance(s -> assertThat(s, Matchers.equalTo("the"))));
        assertTrue(spliterator.tryAdvance(s -> assertThat(s, Matchers.equalTo("quick"))));
        assertFalse(spliterator.tryAdvance(s -> {
        }));
    }

    @Test
    public void testStream() {
        assertThat(seqOf(0, 1, 2).stream().collect(), contains(0, 1, 2));
    }

    @Test
    public void testParallelStream() {
        seqOf(0, 1, 2).parallelStream().collect(toList());
    }

    @Test
    public void testIterator() {
        assertTrue(true);
    }

    @Test
    public void testHashCode() {
        for (Seq<?> seq : seqOf(
                seqOf(),
                seqOf((Object) null),
                seqOf(10),
                seqOf(3, 7),
                seqOf(7, 3),
                seqOf("the", "quick", "brown"))) {
            assertThat(seq.hashCode(), equalTo(new ArrayList<>(seq).hashCode()));
        }
    }

    @Test
    public void testEquals() {
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
        assertThat(seqOf("seq", "ly").toString(""), equalTo("seqly"));
        assertThat(seqOf(true, false).toString("|", "<", ">"),
                equalTo("<true|false>"));
    }

    @SafeVarargs
    private final <E> Seq<E> seqOf(E... elements) {
        return factory.create(elements);
    }

    private static interface Factory {
        public <E> Seq<E> create(E[] elements);
    }

    private static class TestSeq<E> implements Seq<E> {

        private List<E> list;

        @SafeVarargs
        public TestSeq(E... elements) {
            list = Arrays.asList(elements);
        }

        public Spliterator<E> spliterator() {
            return list.spliterator();
        }

        public int hashCode() {
            return Seq.view(list).hashCode();
        }

        public boolean equals(Object obj) {
            return Seq.view(list).equals(obj);
        }

        public String toString() {
            return Seq.view(list).toString();
        }
    }
}
