package io.github.jancellor.seq.bench;

import io.github.jancellor.seq.Seq;
import io.github.jancellor.seq.SeqStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

/**
 * Public-API pipelines through the library against the equivalent JDK
 * stream and a hand-written loop.
 *
 * <ul>
 * <li>SEQ is the eager API: every stage materializes an array.
 * <li>SEQSTREAM is the lazy API: a push chain of stages.
 * <li>JDK is {@code java.util.stream}.
 * <li>LOOP is a plain loop and is the floor.
 * </ul>
 *
 * The cases cover pipeline shapes rather than operators: map and filter
 * chains, a buffering stage (sorted, distinct), a short-circuiting terminal
 * and stage (anyMatch, takeWhile), flatMap, and each source kind (array,
 * collection, iterator). {@link #setup()} cross-checks every implementation
 * against LOOP so a wrong answer fails loudly.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
public class PipelineBench {

    @Param({"1000"})
    public int size;

    @Param({"SEQ", "SEQSTREAM", "JDK", "LOOP"})
    public Impl impl;

    /** A permutation of 0..size-1, so values are distinct and unsorted. */
    Integer[] a;
    String[] s;
    /** Same values as {@link #a}. */
    List<Integer> list;
    /** Indexed by value in {@link #a}; three elements each. */
    List<Integer>[] nested;
    /** The value at index size/10: short-circuit cases stop here. */
    int needle;

    Seq<Integer> seqA;
    Seq<String> seqS;
    Seq<Integer> seqList;

    @Setup
    @SuppressWarnings("unchecked")
    public void setup() {
        a = new Integer[size];
        s = new String[size];
        nested = new List[size];
        for (int i = 0; i < size; i++) {
            a[i] = (i * 7919) % size;
            s[i] = Integer.toString(a[i]);
            nested[i] = List.of(i, i + 1, i + 2);
        }
        list = new ArrayList<>(Arrays.asList(a));
        needle = a[size / 10];
        seqA = Seq.viewOf(a);
        seqS = Seq.viewOf(s);
        seqList = Seq.viewOf(list);
        for (Impl other : Impl.values()) {
            check(other, "mapToList", Impl.LOOP.mapToList(this),
                    other.mapToList(this));
            check(other, "filterMapToList", Impl.LOOP.filterMapToList(this),
                    other.filterMapToList(this));
            check(other, "mfmSum", Impl.LOOP.mfmSum(this), other.mfmSum(this));
            check(other, "strLenSum", Impl.LOOP.strLenSum(this),
                    other.strLenSum(this));
            check(other, "sortedToList", Impl.LOOP.sortedToList(this),
                    other.sortedToList(this));
            check(other, "distinctCount", Impl.LOOP.distinctCount(this),
                    other.distinctCount(this));
            check(other, "anyMatch", Impl.LOOP.anyMatch(this),
                    other.anyMatch(this));
            check(other, "takeWhileToList", Impl.LOOP.takeWhileToList(this),
                    other.takeWhileToList(this));
            check(other, "flatMapSum", Impl.LOOP.flatMapSum(this),
                    other.flatMapSum(this));
            check(other, "listMapToList", Impl.LOOP.listMapToList(this),
                    other.listMapToList(this));
            check(other, "iteratorMapToList", Impl.LOOP.iteratorMapToList(this),
                    other.iteratorMapToList(this));
        }
    }

    private static void check(Impl impl, String name, Object expected,
            Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(impl + " " + name + ": expected "
                    + expected + " got " + actual);
        }
    }

    public enum Impl {
        SEQ {
            List<Integer> mapToList(PipelineBench b) {
                return b.seqA.map(x -> x + 1).toList();
            }
            List<Integer> filterMapToList(PipelineBench b) {
                return b.seqA.filter(x -> (x & 1) == 0).map(x -> x * 3)
                        .toList();
            }
            int mfmSum(PipelineBench b) {
                return b.seqA.map(x -> x + 1).filter(x -> (x & 1) == 0)
                        .map(x -> x * 3).sumOfInt(x -> x);
            }
            int strLenSum(PipelineBench b) {
                return b.seqS.filter(x -> x.charAt(0) == '1')
                        .map(String::length).sumOfInt(x -> x);
            }
            List<Integer> sortedToList(PipelineBench b) {
                return b.seqA.sorted().toList();
            }
            long distinctCount(PipelineBench b) {
                return b.seqA.map(x -> x >> 1).distinct().count();
            }
            boolean anyMatch(PipelineBench b) {
                return b.seqA.map(x -> x + 1).anyMatch(x -> x == b.needle + 1);
            }
            List<Integer> takeWhileToList(PipelineBench b) {
                return b.seqA.takeWhile(x -> x != b.needle).map(x -> x + 1)
                        .toList();
            }
            int flatMapSum(PipelineBench b) {
                return b.seqA.flatMap(x -> b.nested[x]).sumOfInt(x -> x);
            }
            List<Integer> listMapToList(PipelineBench b) {
                return b.seqList.map(x -> x + 1).toList();
            }
            List<Integer> iteratorMapToList(PipelineBench b) {
                return Seq.copyOf(b.list.iterator()).map(x -> x + 1).toList();
            }
        },
        SEQSTREAM {
            List<Integer> mapToList(PipelineBench b) {
                return b.seqA.stream().map(x -> x + 1).toList();
            }
            List<Integer> filterMapToList(PipelineBench b) {
                return b.seqA.stream().filter(x -> (x & 1) == 0).map(x -> x * 3)
                        .toList();
            }
            int mfmSum(PipelineBench b) {
                return b.seqA.stream().map(x -> x + 1).filter(x -> (x & 1) == 0)
                        .map(x -> x * 3).sumOfInt(x -> x);
            }
            int strLenSum(PipelineBench b) {
                return b.seqS.stream().filter(x -> x.charAt(0) == '1')
                        .map(String::length).sumOfInt(x -> x);
            }
            List<Integer> sortedToList(PipelineBench b) {
                return b.seqA.stream().sorted().toList();
            }
            long distinctCount(PipelineBench b) {
                return b.seqA.stream().map(x -> x >> 1).distinct().count();
            }
            boolean anyMatch(PipelineBench b) {
                return b.seqA.stream().map(x -> x + 1)
                        .anyMatch(x -> x == b.needle + 1);
            }
            List<Integer> takeWhileToList(PipelineBench b) {
                return b.seqA.stream().takeWhile(x -> x != b.needle)
                        .map(x -> x + 1).toList();
            }
            int flatMapSum(PipelineBench b) {
                return b.seqA.stream().flatMap(x -> b.nested[x].stream())
                        .sumOfInt(x -> x);
            }
            List<Integer> listMapToList(PipelineBench b) {
                return b.seqList.stream().map(x -> x + 1).toList();
            }
            List<Integer> iteratorMapToList(PipelineBench b) {
                return SeqStream.viewOf(b.list.iterator()).map(x -> x + 1)
                        .toList();
            }
        },
        JDK {
            List<Integer> mapToList(PipelineBench b) {
                return Arrays.stream(b.a).map(x -> x + 1).toList();
            }
            List<Integer> filterMapToList(PipelineBench b) {
                return Arrays.stream(b.a).filter(x -> (x & 1) == 0)
                        .map(x -> x * 3).toList();
            }
            int mfmSum(PipelineBench b) {
                return Arrays.stream(b.a).map(x -> x + 1)
                        .filter(x -> (x & 1) == 0).map(x -> x * 3)
                        .mapToInt(x -> x).sum();
            }
            int strLenSum(PipelineBench b) {
                return Arrays.stream(b.s).filter(x -> x.charAt(0) == '1')
                        .map(String::length).mapToInt(x -> x).sum();
            }
            List<Integer> sortedToList(PipelineBench b) {
                return Arrays.stream(b.a).sorted().toList();
            }
            long distinctCount(PipelineBench b) {
                return Arrays.stream(b.a).map(x -> x >> 1).distinct().count();
            }
            boolean anyMatch(PipelineBench b) {
                return Arrays.stream(b.a).map(x -> x + 1)
                        .anyMatch(x -> x == b.needle + 1);
            }
            List<Integer> takeWhileToList(PipelineBench b) {
                return Arrays.stream(b.a).takeWhile(x -> x != b.needle)
                        .map(x -> x + 1).toList();
            }
            int flatMapSum(PipelineBench b) {
                return Arrays.stream(b.a).flatMap(x -> b.nested[x].stream())
                        .mapToInt(x -> x).sum();
            }
            List<Integer> listMapToList(PipelineBench b) {
                return b.list.stream().map(x -> x + 1).toList();
            }
            List<Integer> iteratorMapToList(PipelineBench b) {
                Iterator<Integer> it = b.list.iterator();
                return StreamSupport
                        .stream(Spliterators.spliteratorUnknownSize(it, 0),
                                false)
                        .map(x -> x + 1).toList();
            }
        },
        LOOP {
            List<Integer> mapToList(PipelineBench b) {
                List<Integer> out = new ArrayList<>(b.a.length);
                for (Integer x : b.a) out.add(x + 1);
                return out;
            }
            List<Integer> filterMapToList(PipelineBench b) {
                List<Integer> out = new ArrayList<>();
                for (Integer x : b.a) if ((x & 1) == 0) out.add(x * 3);
                return out;
            }
            int mfmSum(PipelineBench b) {
                int sum = 0;
                for (Integer e : b.a) {
                    Integer x = e + 1;
                    if ((x & 1) == 0) sum += x * 3;
                }
                return sum;
            }
            int strLenSum(PipelineBench b) {
                int sum = 0;
                for (String x : b.s) if (x.charAt(0) == '1') sum += x.length();
                return sum;
            }
            List<Integer> sortedToList(PipelineBench b) {
                Integer[] copy = b.a.clone();
                Arrays.sort(copy);
                return new ArrayList<>(Arrays.asList(copy));
            }
            long distinctCount(PipelineBench b) {
                Set<Integer> seen = new HashSet<>();
                for (Integer x : b.a) seen.add(x >> 1);
                return seen.size();
            }
            boolean anyMatch(PipelineBench b) {
                for (Integer x : b.a) if (x + 1 == b.needle + 1) return true;
                return false;
            }
            List<Integer> takeWhileToList(PipelineBench b) {
                List<Integer> out = new ArrayList<>();
                for (Integer x : b.a) {
                    if (x == b.needle) break;
                    out.add(x + 1);
                }
                return out;
            }
            int flatMapSum(PipelineBench b) {
                int sum = 0;
                for (Integer x : b.a) for (Integer y : b.nested[x]) sum += y;
                return sum;
            }
            List<Integer> listMapToList(PipelineBench b) {
                List<Integer> out = new ArrayList<>(b.list.size());
                for (Integer x : b.list) out.add(x + 1);
                return out;
            }
            List<Integer> iteratorMapToList(PipelineBench b) {
                List<Integer> out = new ArrayList<>();
                Iterator<Integer> it = b.list.iterator();
                while (it.hasNext()) out.add(it.next() + 1);
                return out;
            }
        };

        abstract List<Integer> mapToList(PipelineBench b);

        abstract List<Integer> filterMapToList(PipelineBench b);

        abstract int mfmSum(PipelineBench b);

        abstract int strLenSum(PipelineBench b);

        abstract List<Integer> sortedToList(PipelineBench b);

        abstract long distinctCount(PipelineBench b);

        abstract boolean anyMatch(PipelineBench b);

        abstract List<Integer> takeWhileToList(PipelineBench b);

        abstract int flatMapSum(PipelineBench b);

        abstract List<Integer> listMapToList(PipelineBench b);

        abstract List<Integer> iteratorMapToList(PipelineBench b);
    }

    @Benchmark
    public List<Integer> mapToList() {
        return impl.mapToList(this);
    }
    @Benchmark
    public List<Integer> filterMapToList() {
        return impl.filterMapToList(this);
    }
    @Benchmark
    public int mfmSum() {
        return impl.mfmSum(this);
    }
    @Benchmark
    public int strLenSum() {
        return impl.strLenSum(this);
    }
    @Benchmark
    public List<Integer> sortedToList() {
        return impl.sortedToList(this);
    }
    @Benchmark
    public long distinctCount() {
        return impl.distinctCount(this);
    }
    @Benchmark
    public boolean anyMatch() {
        return impl.anyMatch(this);
    }
    @Benchmark
    public List<Integer> takeWhileToList() {
        return impl.takeWhileToList(this);
    }
    @Benchmark
    public int flatMapSum() {
        return impl.flatMapSum(this);
    }
    @Benchmark
    public List<Integer> listMapToList() {
        return impl.listMapToList(this);
    }
    @Benchmark
    public List<Integer> iteratorMapToList() {
        return impl.iteratorMapToList(this);
    }
}
