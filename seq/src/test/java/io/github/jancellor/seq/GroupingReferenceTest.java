package io.github.jancellor.seq;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class GroupingReferenceTest {
    private static final class Value {
        final int key;
        final int identity;
        Value(int key, int identity) {
            this.key = key;
            this.identity = identity;
        }
        public boolean equals(Object other) {
            return other instanceof Value && ((Value) other).key == key;
        }
        public int hashCode() {
            return 0;
        }
        public String toString() {
            return key + ":" + identity;
        }
    }

    @Test
    public void testGroupingAndMultisetsPreserveRepresentatives() {
        Random random = new Random(82403);
        for (int trial = 0; trial < 1000; trial++) {
            List<Value> left = values(random);
            List<Value> right = values(random);
            List<Value> remainder = new ArrayList<>(right);
            List<Value> intersection = new ArrayList<>();
            List<Value> difference = new ArrayList<>();
            for (Value value : left) {
                int at = remainder.indexOf(value);
                if (at < 0) difference.add(value);
                else {
                    intersection.add(value);
                    remainder.remove(at);
                }
            }
            List<Value> union = new ArrayList<>(left);
            union.addAll(remainder);
            List<Value> symmetric = new ArrayList<>(difference);
            symmetric.addAll(remainder);
            Seq<Value> seq = Seq.copyOf(left);
            assertIdentities(intersection, seq.intersection(right).toList());
            assertIdentities(difference, seq.difference(right).toList());
            assertIdentities(union, seq.union(right).toList());
            assertIdentities(symmetric,
                    seq.symmetricDifference(right).toList());
            assertIdentities(intersection,
                    seq.stream().intersection(right.stream()).toList());
            assertIdentities(difference,
                    seq.stream().difference(right.stream()).toList());
            assertIdentities(union,
                    seq.stream().union(right.stream()).toList());
            assertIdentities(symmetric,
                    seq.stream().symmetricDifference(right.stream()).toList());

            Map<Integer, List<Value>> groups = new LinkedHashMap<>();
            for (Value value : left) {
                Integer key = value == null ? null : value.key;
                if (!groups.containsKey(key))
                    groups.put(key, new ArrayList<>());
                groups.get(key).add(value);
            }
            Map<Integer, Seq<Value>> actual = seq
                    .groupBy(v -> v == null ? null : v.key);
            assertEquals(new ArrayList<>(groups.keySet()),
                    new ArrayList<>(actual.keySet()));
            for (Integer key : groups.keySet())
                assertIdentities(groups.get(key), actual.get(key).toList());
            Map<Integer, List<Value>> mapped = seq.toMap(
                    v -> v == null ? null : v.key,
                    v -> new ArrayList<>(Collections.singletonList(v)),
                    (a, b) -> {
                        a.addAll(b);
                        return a;
                    });
            assertEquals(new ArrayList<>(groups.keySet()),
                    new ArrayList<>(mapped.keySet()));
            for (Integer key : groups.keySet())
                assertIdentities(groups.get(key), mapped.get(key));
        }
    }
    private static List<Value> values(Random random) {
        List<Value> values = new ArrayList<>();
        int size = random.nextInt(20);
        for (int i = 0; i < size; i++) values.add(random.nextInt(5) == 0 ? null
                : new Value(random.nextInt(4), i));
        return values;
    }
    private static void assertIdentities(List<Value> expected,
            List<Value> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++)
            assertSame(expected.get(i), actual.get(i));
    }
}
