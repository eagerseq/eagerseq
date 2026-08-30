package org.bitbucket.seqly;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Checks the index arithmetic of every method that does any
 * against a naive reference implementation, exhaustively over all short
 * inputs and every argument in and just outside the legal range.
 * <p>
 * The reference implementations here use only {@code java.util}, never
 * {@code Seq}, so that agreement means something. They are naive on purpose:
 * a reference that is obviously right is worth more than a fast one.
 * <p>
 * {@link SeqTest} says what each method means, one readable case at a time.
 * This says that the meaning holds at every boundary.
 */
@RunWith(Parameterized.class)
public class SeqReferenceTest {

    /**
     * Inputs are every sequence over these elements up to {@link #MAX_LENGTH},
     * so repeats and nulls arrive in every position and combination.
     */
    private static final List<String> ELEMENTS = Arrays.asList("a", "b", null);

    private static final int MAX_LENGTH = 5;

    /**
     * The length used where a test sweeps over pairs of inputs, since that
     * costs the square of the number of inputs.
     */
    private static final int MAX_PAIR_LENGTH = 4;

    private static final List<List<String>> INPUTS = inputs(MAX_LENGTH);

    private static final List<List<String>> PAIR_INPUTS =
            inputs(MAX_PAIR_LENGTH);

    private final Factory factory;

    public SeqReferenceTest(String ignored, Factory factory) {
        this.factory = factory;
    }

    @Parameters(name = "{0}")
    public static Iterable<Object[]> parametersList() {
        return Factory.all();
    }

    @Test
    public void testSlice() {
        forEachInputAndTwoArguments((input, from, to) -> {
            if (from < 0 || to < 0) {
                assertThrows(IndexOutOfBoundsException.class,
                        () -> seq(input).slice(from, to));
            } else {
                assertThat(seq(input).slice(from, to).toList(),
                        equalTo(referenceSlice(input, from, to)));
            }
        });
    }

    @Test
    public void testLimit() {
        forEachInputAndArgument((input, size) -> {
            if (size < 0) {
                assertThrows(IllegalArgumentException.class,
                        () -> seq(input).limit(size));
            } else {
                assertThat(seq(input).limit(size).toList(),
                        equalTo(referenceSlice(input, 0, size)));
            }
        });
    }

    @Test
    public void testSkip() {
        forEachInputAndArgument((input, size) -> {
            if (size < 0) {
                assertThrows(IllegalArgumentException.class,
                        () -> seq(input).skip(size));
            } else {
                assertThat(seq(input).skip(size).toList(),
                        equalTo(referenceSlice(input, size, input.size())));
            }
        });
    }

    @Test
    public void testLimitLast() {
        forEachInputAndArgument((input, size) -> {
            if (size < 0) {
                assertThrows(IllegalArgumentException.class,
                        () -> seq(input).limitLast(size));
            } else {
                assertThat(seq(input).limitLast(size).toList(),
                        equalTo(referenceSlice(
                                input, input.size() - size, input.size())));
            }
        });
    }

    @Test
    public void testSkipLast() {
        forEachInputAndArgument((input, size) -> {
            if (size < 0) {
                assertThrows(IllegalArgumentException.class,
                        () -> seq(input).skipLast(size));
            } else {
                assertThat(seq(input).skipLast(size).toList(),
                        equalTo(referenceSlice(
                                input, 0, input.size() - size)));
            }
        });
    }

    @Test
    public void testGet() {
        forEachInputAndArgument((input, index) -> {
            if (index < 0 || index >= input.size()) {
                assertThrows(IndexOutOfBoundsException.class,
                        () -> seq(input).get(index));
            } else {
                assertThat(seq(input).get(index),
                        equalTo(input.get(index)));
            }
        });
    }

    @Test
    public void testReversed() {
        forEachInput(input -> {
            List<String> expected = new ArrayList<>(input);
            Collections.reverse(expected);
            assertThat(seq(input).reversed().toList(), equalTo(expected));
        });
    }

    @Test
    public void testRotated() {
        forEachInputAndArgument((input, distance) -> {
            List<String> expected = new ArrayList<>(input);
            Collections.rotate(expected, distance);
            assertThat(seq(input).rotated(distance).toList(),
                    equalTo(expected));
        });
    }

    @Test
    public void testIndexes() {
        forEachInput(input -> {
            List<Integer> expected = new ArrayList<>();
            for (int i = 0; i < input.size(); i++) expected.add(i);
            assertThat(seq(input).indexes().toList(), equalTo(expected));
        });
    }

    @Test
    public void testIndexOf() {
        forEachInputAndElement((input, element) -> {
            List<Integer> expected = referenceIndexesOf(input, element);
            assertThat(seq(input).indexOf(element),
                    equalTo(expected.isEmpty() ? -1 : expected.get(0)));
        });
    }

    @Test
    public void testLastIndexOf() {
        forEachInputAndElement((input, element) -> {
            List<Integer> expected = referenceIndexesOf(input, element);
            assertThat(seq(input).lastIndexOf(element),
                    equalTo(expected.isEmpty()
                            ? -1 : expected.get(expected.size() - 1)));
        });
    }

    @Test
    public void testIndexesOf() {
        forEachInputAndElement((input, element) ->
                assertThat(seq(input).indexesOf(element).toList(),
                        equalTo(referenceIndexesOf(input, element))));
    }

    @Test
    public void testIndexesOfSlice() {
        forEachInputPair((input, that) ->
                assertThat(seq(input).indexesOfSlice(that).toList(),
                        equalTo(referenceIndexesOfSlice(input, that))));
    }

    @Test
    public void testIndexOfSlice() {
        forEachInputPair((input, that) -> {
            List<Integer> expected = referenceIndexesOfSlice(input, that);
            assertThat(seq(input).indexOfSlice(that),
                    equalTo(expected.isEmpty() ? -1 : expected.get(0)));
        });
    }

    @Test
    public void testLastIndexOfSlice() {
        forEachInputPair((input, that) -> {
            List<Integer> expected = referenceIndexesOfSlice(input, that);
            assertThat(seq(input).lastIndexOfSlice(that),
                    equalTo(expected.isEmpty()
                            ? -1 : expected.get(expected.size() - 1)));
        });
    }

    @Test
    public void testContainsSlice() {
        forEachInputPair((input, that) ->
                assertThat(seq(input).containsSlice(that),
                        equalTo(!referenceIndexesOfSlice(input, that)
                                .isEmpty())));
    }

    @Test
    public void testStartsWith() {
        forEachInputPair((input, that) ->
                assertThat(seq(input).startsWith(that),
                        equalTo(referenceIndexesOfSlice(input, that)
                                .contains(0))));
    }

    @Test
    public void testEndsWith() {
        forEachInputPair((input, that) ->
                assertThat(seq(input).endsWith(that),
                        equalTo(referenceIndexesOfSlice(input, that)
                                .contains(input.size() - that.size()))));
    }

    @Test
    public void testSum() {
        forEachInputPair((input, that) -> {
            List<String> expected = new ArrayList<>(input);
            expected.addAll(that);
            assertThat(seq(input).sum(that).toList(), equalTo(expected));
        });
    }

    @Test
    public void testIntersection() {
        forEachInputPair((input, that) ->
                assertThat(seq(input).intersection(that).toList(),
                        equalTo(referenceIntersection(input, that))));
    }

    @Test
    public void testDifference() {
        forEachInputPair((input, that) ->
                assertThat(seq(input).difference(that).toList(),
                        equalTo(referenceDifference(input, that))));
    }

    @Test
    public void testUnion() {
        forEachInputPair((input, that) -> {
            // the documented equivalence, sum(that.difference(this))
            List<String> expected = new ArrayList<>(input);
            expected.addAll(referenceDifference(that, input));
            assertThat(seq(input).union(that).toList(), equalTo(expected));
        });
    }

    @Test
    public void testContainsMultiset() {
        forEachInputPair((input, that) ->
                assertThat(seq(input).containsMultiset(that),
                        equalTo(referenceDifference(that, input).isEmpty())));
    }

    @Test
    public void testListEquals() {
        forEachInputPair((input, that) ->
                assertThat(seq(input).listEquals(that),
                        equalTo(input.equals(that))));
    }

    @Test
    public void testMultisetEquals() {
        forEachInputPair((input, that) ->
                assertThat(seq(input).multisetEquals(that),
                        equalTo(referenceDifference(input, that).isEmpty()
                                && referenceDifference(that, input)
                                        .isEmpty())));
    }

    @Test
    public void testSetEquals() {
        forEachInputPair((input, that) ->
                assertThat(seq(input).setEquals(that),
                        equalTo(new HashSet<>(input)
                                .equals(new HashSet<>(that)))));
    }

    @Test
    public void testZip() {
        forEachInputPair((input, that) -> {
            List<String> expected = new ArrayList<>();
            for (int i = 0; i < Math.min(input.size(), that.size()); i++) {
                expected.add(input.get(i) + ":" + that.get(i));
            }
            assertThat(seq(input).zip(that, (e, f) -> e + ":" + f).toList(),
                    equalTo(expected));
        });
    }

    @Test
    public void testPermutations() {
        forEachInput(input ->
                assertThat(toLists(seq(input).permutations()),
                        equalTo(select(input,
                                indexPermutations(indexes(input.size()))))));
    }

    @Test
    public void testCombinations() {
        forEachInputAndArgument((input, size) -> {
            if (size < 0 || size > input.size()) {
                assertThrows(IllegalArgumentException.class,
                        () -> seq(input).combinations(size));
            } else {
                assertThat(toLists(seq(input).combinations(size)),
                        equalTo(select(input, indexCombinations(
                                0, input.size(), size))));
            }
        });
    }

    @Test
    public void testPowerSet() {
        forEachInput(input -> {
            List<List<Integer>> expected = new ArrayList<>();
            for (int size = 0; size <= input.size(); size++) {
                expected.addAll(indexCombinations(0, input.size(), size));
            }
            assertThat(toLists(seq(input).powerSet()),
                    equalTo(select(input, expected)));
        });
    }

    private static List<String> referenceSlice(
            List<String> input, long from, long to) {
        List<String> result = new ArrayList<>();
        for (long i = Math.max(from, 0);
                i < Math.min(to, input.size()); i++) {
            result.add(input.get((int) i));
        }
        return result;
    }

    private static List<Integer> referenceIndexesOf(
            List<String> input, String element) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            if (Objects.equals(input.get(i), element)) result.add(i);
        }
        return result;
    }

    private static List<Integer> referenceIndexesOfSlice(
            List<String> input, List<String> that) {
        List<Integer> result = new ArrayList<>();
        for (int from = 0; from + that.size() <= input.size(); from++) {
            if (input.subList(from, from + that.size()).equals(that)) {
                result.add(from);
            }
        }
        return result;
    }

    /** Those elements of the input present in {@code that}, earliest first. */
    private static List<String> referenceIntersection(
            List<String> input, List<String> that) {
        List<String> remaining = new ArrayList<>(that);
        List<String> result = new ArrayList<>();
        for (String element : input) {
            if (remaining.remove(element)) result.add(element);
        }
        return result;
    }

    /** Those elements of the input absent from {@code that}, latest first. */
    private static List<String> referenceDifference(
            List<String> input, List<String> that) {
        List<String> result = new ArrayList<>(input);
        for (String element : that) result.remove(element);
        return result;
    }

    /** All permutations of the given indexes, in lexical order. */
    private static List<List<Integer>> indexPermutations(
            List<Integer> available) {
        List<List<Integer>> result = new ArrayList<>();
        if (available.isEmpty()) {
            result.add(new ArrayList<>());
            return result;
        }
        for (int i = 0; i < available.size(); i++) {
            List<Integer> rest = new ArrayList<>(available);
            Integer first = rest.remove(i);
            for (List<Integer> tail : indexPermutations(rest)) {
                List<Integer> permutation = new ArrayList<>();
                permutation.add(first);
                permutation.addAll(tail);
                result.add(permutation);
            }
        }
        return result;
    }

    /**
     * The subsets of {@code size} indexes drawn from {@code from} (inclusive)
     * to {@code to} (exclusive), in lexical order.
     */
    private static List<List<Integer>> indexCombinations(
            int from, int to, int size) {
        List<List<Integer>> result = new ArrayList<>();
        if (size == 0) {
            result.add(new ArrayList<>());
            return result;
        }
        for (int i = from; i <= to - size; i++) {
            for (List<Integer> rest : indexCombinations(i + 1, to, size - 1)) {
                List<Integer> combination = new ArrayList<>();
                combination.add(i);
                combination.addAll(rest);
                result.add(combination);
            }
        }
        return result;
    }

    private static List<Integer> indexes(int size) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < size; i++) result.add(i);
        return result;
    }

    private static List<List<String>> select(
            List<String> input, List<List<Integer>> indexLists) {
        List<List<String>> result = new ArrayList<>();
        for (List<Integer> indexes : indexLists) {
            List<String> selection = new ArrayList<>();
            for (int index : indexes) selection.add(input.get(index));
            result.add(selection);
        }
        return result;
    }

    private static List<List<String>> toLists(
            Seq<? extends Seq<String>> seqs) {
        List<List<String>> result = new ArrayList<>();
        for (Seq<String> seq : seqs) result.add(seq.toList());
        return result;
    }

    /** Every sequence over {@link #ELEMENTS} up to the given length. */
    private static List<List<String>> inputs(int maxLength) {
        List<List<String>> result = new ArrayList<>();
        result.add(new ArrayList<>());
        for (int length = 1; length <= maxLength; length++) {
            for (List<String> shorter : new ArrayList<>(result)) {
                if (shorter.size() != length - 1) continue;
                for (String element : ELEMENTS) {
                    List<String> longer = new ArrayList<>(shorter);
                    longer.add(element);
                    result.add(longer);
                }
            }
        }
        return result;
    }

    private void forEachInput(Consumer<List<String>> check) {
        for (List<String> input : INPUTS) {
            try {
                check.accept(input);
            } catch (AssertionError e) {
                throw failure(e, input.toString());
            }
        }
    }

    /**
     * Every input against every argument from just below the legal range to
     * just above it, so the boundaries are covered rather than sampled.
     */
    private void forEachInputAndArgument(
            BiConsumer<List<String>, Integer> check) {
        for (List<String> input : INPUTS) {
            for (int argument = -2; argument <= input.size() + 2; argument++) {
                try {
                    check.accept(input, argument);
                } catch (AssertionError e) {
                    throw failure(e, input + ", " + argument);
                }
            }
        }
    }

    private void forEachInputAndTwoArguments(TwoArgumentCheck check) {
        for (List<String> input : INPUTS) {
            for (int first = -2; first <= input.size() + 2; first++) {
                for (int second = -2; second <= input.size() + 2; second++) {
                    try {
                        check.accept(input, first, second);
                    } catch (AssertionError e) {
                        throw failure(e,
                                input + ", " + first + ", " + second);
                    }
                }
            }
        }
    }

    private void forEachInputAndElement(
            BiConsumer<List<String>, String> check) {
        for (List<String> input : INPUTS) {
            for (String element : Arrays.asList("a", "b", null, "z")) {
                try {
                    check.accept(input, element);
                } catch (AssertionError e) {
                    throw failure(e, input + ", " + element);
                }
            }
        }
    }

    private void forEachInputPair(
            BiConsumer<List<String>, List<String>> check) {
        for (List<String> input : PAIR_INPUTS) {
            for (List<String> that : PAIR_INPUTS) {
                try {
                    check.accept(input, that);
                } catch (AssertionError e) {
                    throw failure(e, input + ", " + that);
                }
            }
        }
    }

    private static AssertionError failure(AssertionError cause, String about) {
        return new AssertionError(about + ": " + cause.getMessage(), cause);
    }

    private static void assertThrows(
            Class<? extends RuntimeException> expected, Runnable action) {
        SeqTest.assertThrows(expected, action);
    }

    private Seq<String> seq(List<String> elements) {
        return factory.create(elements.toArray(new String[0]));
    }

    private interface TwoArgumentCheck {
        void accept(List<String> input, int first, int second);
    }
}
