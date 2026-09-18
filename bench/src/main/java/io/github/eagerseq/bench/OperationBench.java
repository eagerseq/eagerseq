package io.github.eagerseq.bench;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

import io.github.eagerseq.Seq;
import io.github.eagerseq.SeqStream;

/**
 * Broad operator coverage of {@link SeqStream} against the equivalent JDK
 * {@link Stream} pipeline. Unlike {@link PipelineBench}, which covers pipeline
 * shapes with four implementations, this covers many operators with two.
 *
 * <p>Each case is a pair of methods, {@code name_seq} and {@code name_jdk}.
 * {@link #setup()} pairs them by reflection and compares their results, so
 * a case that does not measure the same work fails before it is timed.
 *
 * <p>Cases in the last group have no direct JDK equivalent; the JDK side is
 * the idiomatic stream that does the same job.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
public class OperationBench {

    @Param({"1000"})
    public int size;

    /** A permutation of 0..size-1, so values are distinct and unsorted. */
    Integer[] a;
    String[] s;
    Integer[] half;
    List<Integer>[] nested;
    /** The value at index size/10: short-circuit cases stop here. */
    int needle;
    int tenth;
    Seq<Integer> seqA;
    Seq<String> seqS;
    /** Accumulator for cases whose terminal must consume every element. */
    int sink;
    /** Many small arrays, for the concat cases. */
    Integer[][] chunks;

    @Setup
    @SuppressWarnings("unchecked")
    public void setup() throws Exception {
        a = new Integer[size];
        s = new String[size];
        half = new Integer[size];
        nested = new List[size];
        for (int i = 0; i < size; i++) {
            a[i] = (i * 7919) % size;
            s[i] = Integer.toString(a[i]);
            half[i] = a[i] >> 1;
            nested[i] = List.of(i, i + 1, i + 2);
        }
        chunks = new Integer[50][];
        for (int i = 0; i < chunks.length; i++) {
            chunks[i] = new Integer[] {i, i + 1};
        }
        tenth = Math.max(1, size / 10);
        needle = a[tenth];
        seqA = Seq.viewOf(a);
        seqS = Seq.viewOf(s);
        crossCheck();
    }

    private SeqStream<Integer> seq() {
        return seqA.stream();
    }

    private Stream<Integer> jdk() {
        return Arrays.stream(a);
    }

    @SuppressWarnings("unchecked")
    private Stream<Integer>[] parts() {
        Stream<Integer>[] parts = new Stream[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            parts[i] = Arrays.stream(chunks[i]);
        }
        return parts;
    }

    // ---- intermediate operations -------------------------------------

    @Benchmark public Object filter_seq() { return seq().filter(x -> (x & 1) == 0).toList(); }
    @Benchmark public Object filter_jdk() { return jdk().filter(x -> (x & 1) == 0).toList(); }

    @Benchmark public Object map_seq() { return seq().map(x -> x + 1).toList(); }
    @Benchmark public Object map_jdk() { return jdk().map(x -> x + 1).toList(); }

    @Benchmark public Object mapToInt_seq() { return seq().mapToInt(x -> x + 1).sum(); }
    @Benchmark public Object mapToInt_jdk() { return jdk().mapToInt(x -> x + 1).sum(); }

    @Benchmark public Object flatMap_seq() { sink = 0; seq().flatMap(x -> nested[x].stream()).forEach(this::add); return sink; }
    @Benchmark public Object flatMap_jdk() { sink = 0; jdk().flatMap(x -> nested[x].stream()).forEach(this::add); return sink; }

    @Benchmark public Object mapMulti_seq() { sink = 0; seq().<Integer>mapMulti((x, c) -> { c.accept(x); c.accept(x + 1); }).forEach(this::add); return sink; }
    @Benchmark public Object mapMulti_jdk() { sink = 0; jdk().<Integer>mapMulti((x, c) -> { c.accept(x); c.accept(x + 1); }).forEach(this::add); return sink; }

    @Benchmark public Object distinct_seq() { sink = 0; Seq.viewOf(half).stream().distinct().forEach(this::add); return sink; }
    @Benchmark public Object distinct_jdk() { sink = 0; Arrays.stream(half).distinct().forEach(this::add); return sink; }

    @Benchmark public Object sorted_seq() { return seq().sorted().toList(); }
    @Benchmark public Object sorted_jdk() { return jdk().sorted().toList(); }

    @Benchmark public Object sortedComparator_seq() { return seq().sorted(Comparator.reverseOrder()).toList(); }
    @Benchmark public Object sortedComparator_jdk() { return jdk().sorted(Comparator.reverseOrder()).toList(); }

    @Benchmark public Object limit_seq() { return seq().limit(tenth).toList(); }
    @Benchmark public Object limit_jdk() { return jdk().limit(tenth).toList(); }

    @Benchmark public Object skip_seq() { return seq().skip(tenth).toList(); }
    @Benchmark public Object skip_jdk() { return jdk().skip(tenth).toList(); }

    @Benchmark public Object takeWhile_seq() { return seq().takeWhile(x -> x != needle).toList(); }
    @Benchmark public Object takeWhile_jdk() { return jdk().takeWhile(x -> x != needle).toList(); }

    @Benchmark public Object dropWhile_seq() { return seq().dropWhile(x -> x != needle).toList(); }
    @Benchmark public Object dropWhile_jdk() { return jdk().dropWhile(x -> x != needle).toList(); }

    @Benchmark public Object peek_seq() { int[] n = {0}; seq().peek(x -> n[0]++).toList(); return n[0]; }
    @Benchmark public Object peek_jdk() { int[] n = {0}; jdk().peek(x -> n[0]++).toList(); return n[0]; }

    @Benchmark public Object chain_seq() { return seq().map(x -> x + 1).filter(x -> (x & 1) == 0).map(x -> x * 3).toList(); }
    @Benchmark public Object chain_jdk() { return jdk().map(x -> x + 1).filter(x -> (x & 1) == 0).map(x -> x * 3).toList(); }

    @Benchmark public Object deepChain_seq() {
        sink = 0;
        seq().map(x -> x + 1).map(x -> x + 1).map(x -> x + 1).map(x -> x + 1)
                .filter(x -> x > 0).map(x -> x + 1).map(x -> x + 1).forEach(this::add);
        return sink;
    }
    @Benchmark public Object deepChain_jdk() {
        sink = 0;
        jdk().map(x -> x + 1).map(x -> x + 1).map(x -> x + 1).map(x -> x + 1)
                .filter(x -> x > 0).map(x -> x + 1).map(x -> x + 1).forEach(this::add);
        return sink;
    }

    // Stage cost isolated from the terminal: these drain with forEach,
    // whose own overhead is close to equal on the two sides, so the
    // difference between them is the cost of the stages.

    @Benchmark public Object drain0_seq() { sink = 0; seq().forEach(this::add); return sink; }
    @Benchmark public Object drain0_jdk() { sink = 0; jdk().forEach(this::add); return sink; }

    @Benchmark public Object drain1_seq() { sink = 0; seq().map(x -> x + 1).forEach(this::add); return sink; }
    @Benchmark public Object drain1_jdk() { sink = 0; jdk().map(x -> x + 1).forEach(this::add); return sink; }

    @Benchmark public Object drain2_seq() { sink = 0; seq().map(x -> x + 1).map(x -> x + 1).forEach(this::add); return sink; }
    @Benchmark public Object drain2_jdk() { sink = 0; jdk().map(x -> x + 1).map(x -> x + 1).forEach(this::add); return sink; }

    @Benchmark public Object drain4_seq() { sink = 0; seq().map(x -> x + 1).map(x -> x + 1).map(x -> x + 1).map(x -> x + 1).forEach(this::add); return sink; }
    @Benchmark public Object drain4_jdk() { sink = 0; jdk().map(x -> x + 1).map(x -> x + 1).map(x -> x + 1).map(x -> x + 1).forEach(this::add); return sink; }

    @Benchmark public Object drainFilter_seq() { sink = 0; seq().filter(x -> (x & 1) == 0).forEach(this::add); return sink; }
    @Benchmark public Object drainFilter_jdk() { sink = 0; jdk().filter(x -> (x & 1) == 0).forEach(this::add); return sink; }

    /** An iterator over a stage, not over the source: pulls one at a time. */
    @Benchmark public Object iteratorMapped_seq() { return drain(seq().map(x -> x + 1).iterator()); }
    @Benchmark public Object iteratorMapped_jdk() { return drain(jdk().map(x -> x + 1).iterator()); }

    // ---- terminal operations -----------------------------------------

    @Benchmark public Object toList_seq() { return seq().toList(); }
    @Benchmark public Object toList_jdk() { return jdk().toList(); }

    @Benchmark public Object toArray_seq() { return seq().toArray(); }
    @Benchmark public Object toArray_jdk() { return jdk().toArray(); }

    @Benchmark public Object toArrayGenerator_seq() { return seq().toArray(Integer[]::new); }
    @Benchmark public Object toArrayGenerator_jdk() { return jdk().toArray(Integer[]::new); }

    @Benchmark public Object toSet_seq() { return seq().toSet(); }
    @Benchmark public Object toSet_jdk() { return jdk().collect(Collectors.toSet()); }

    // count() is the operation under test here, not a way of draining the
    // pipeline. On a sized source the JDK answers from the size without
    // traversing, and is allowed to skip the pipeline's side effects.
    @Benchmark public Object count_seq() { return seq().count(); }
    @Benchmark public Object count_jdk() { return jdk().count(); }

    @Benchmark public Object countFiltered_seq() { return seq().filter(x -> (x & 1) == 0).count(); }
    @Benchmark public Object countFiltered_jdk() { return jdk().filter(x -> (x & 1) == 0).count(); }

    @Benchmark public Object countMapped_seq() { return seq().map(x -> x + 1).count(); }
    @Benchmark public Object countMapped_jdk() { return jdk().map(x -> x + 1).count(); }

    @Benchmark public Object reduce_seq() { return seq().reduce(Integer::sum); }
    @Benchmark public Object reduce_jdk() { return jdk().reduce(Integer::sum); }

    @Benchmark public Object reduceIdentity_seq() { return seq().reduce(0, Integer::sum); }
    @Benchmark public Object reduceIdentity_jdk() { return jdk().reduce(0, Integer::sum); }

    @Benchmark public Object collectToList_seq() { return seq().collect(Collectors.toList()); }
    @Benchmark public Object collectToList_jdk() { return jdk().collect(Collectors.toList()); }

    @Benchmark public Object collectSupplier_seq() { return seq().collect(ArrayList::new, ArrayList::add, ArrayList::addAll); }
    @Benchmark public Object collectSupplier_jdk() { return jdk().collect(ArrayList::new, ArrayList::add, ArrayList::addAll); }

    @Benchmark public Object forEach_seq() { int[] n = {0}; seq().forEach(x -> n[0] += x); return n[0]; }
    @Benchmark public Object forEach_jdk() { int[] n = {0}; jdk().forEach(x -> n[0] += x); return n[0]; }

    @Benchmark public Object min_seq() { return seq().min(); }
    @Benchmark public Object min_jdk() { return jdk().min(Comparator.naturalOrder()); }

    @Benchmark public Object max_seq() { return seq().max(); }
    @Benchmark public Object max_jdk() { return jdk().max(Comparator.naturalOrder()); }

    @Benchmark public Object anyMatchEarly_seq() { return seq().anyMatch(x -> x == needle); }
    @Benchmark public Object anyMatchEarly_jdk() { return jdk().anyMatch(x -> x == needle); }

    @Benchmark public Object anyMatchNever_seq() { return seq().anyMatch(x -> x < 0); }
    @Benchmark public Object anyMatchNever_jdk() { return jdk().anyMatch(x -> x < 0); }

    @Benchmark public Object allMatch_seq() { return seq().allMatch(x -> x >= 0); }
    @Benchmark public Object allMatch_jdk() { return jdk().allMatch(x -> x >= 0); }

    @Benchmark public Object findFirst_seq() { return seq().filter(x -> x == needle).findFirst(); }
    @Benchmark public Object findFirst_jdk() { return jdk().filter(x -> x == needle).findFirst(); }

    @Benchmark public Object iterator_seq() { return drain(seq().iterator()); }
    @Benchmark public Object iterator_jdk() { return drain(jdk().iterator()); }

    @Benchmark public Object toMap_seq() { return seq().toMap(x -> x, x -> x + 1); }
    @Benchmark public Object toMap_jdk() { return jdk().collect(Collectors.toMap(x -> x, x -> x + 1)); }

    @Benchmark public Object groupBy_seq() { return seq().groupBy(x -> x & 15); }
    @Benchmark public Object groupBy_jdk() { return jdk().collect(Collectors.groupingBy(x -> x & 15)); }

    @Benchmark public Object groupByCounting_seq() { return seq().groupBy(x -> x & 15, Seq::size); }
    @Benchmark public Object groupByCounting_jdk() { return jdk().collect(Collectors.groupingBy(x -> x & 15, Collectors.summingInt(x -> 1))); }

    @Benchmark public Object partitionBy_seq() { return seqA.stream().partitionBy(x -> (x & 1) == 0); }
    @Benchmark public Object partitionBy_jdk() { return jdk().collect(Collectors.partitioningBy(x -> (x & 1) == 0)); }

    @Benchmark public Object joining_seq() { return seqS.stream().toString(",", "[", "]"); }
    @Benchmark public Object joining_jdk() { return Arrays.stream(s).collect(Collectors.joining(",", "[", "]")); }

    @Benchmark public Object summingCollector_seq() { return seq().collect(Collectors.summingInt(x -> x)); }
    @Benchmark public Object summingCollector_jdk() { return jdk().collect(Collectors.summingInt(x -> x)); }

    // ---- sources -----------------------------------------------------

    @Benchmark public Object range_seq() { sink = 0; SeqStream.range(0, size).map(x -> x + 1).forEach(this::add); return sink; }
    @Benchmark public Object range_jdk() { sink = 0; IntStream.range(0, size).boxed().map(x -> x + 1).forEach(this::add); return sink; }

    @Benchmark public Object generate_seq() { int[] n = {0}; sink = 0; SeqStream.generate(() -> n[0]++).limit(size).forEach(this::add); return sink; }
    @Benchmark public Object generate_jdk() { int[] n = {0}; sink = 0; Stream.generate(() -> n[0]++).limit(size).forEach(this::add); return sink; }

    @Benchmark public Object iterate_seq() { sink = 0; SeqStream.iterate(0, x -> x + 1).limit(size).forEach(this::add); return sink; }
    @Benchmark public Object iterate_jdk() { sink = 0; Stream.iterate(0, x -> x + 1).limit(size).forEach(this::add); return sink; }

    @Benchmark public Object iterateBounded_seq() { sink = 0; SeqStream.iterate(0, x -> x < size, x -> x + 1).forEach(this::add); return sink; }
    @Benchmark public Object iterateBounded_jdk() { sink = 0; Stream.iterate(0, x -> x < size, x -> x + 1).forEach(this::add); return sink; }

    /**
     * Concatenating the library's own streams, where each part's
     * spliterator is already a Source and needs no adapter. The
     * {@code concat} case above feeds JDK streams to both sides, which
     * forces the library through SpliteratorSource and is the cost of
     * accepting a foreign stream rather than the cost of concat.
     */
    @Benchmark public Object concatNative_seq() { sink = 0; SeqStream.concat(seq(), seq()).forEach(this::add); return sink; }
    @Benchmark public Object concatNative_jdk() { sink = 0; Stream.concat(jdk(), jdk()).forEach(this::add); return sink; }

    /** Many small parts, where concat's per-part fixed cost dominates. */
    @Benchmark public Object concatMany_seq() { sink = 0; SeqStream.concat(parts()).forEach(this::add); return sink; }
    @Benchmark public Object concatMany_jdk() { sink = 0; Arrays.stream(parts()).flatMap(p -> p).forEach(this::add); return sink; }

    @Benchmark public Object concat_seq() { sink = 0; SeqStream.concat(Arrays.stream(a), Arrays.stream(a)).forEach(this::add); return sink; }
    @Benchmark public Object concat_jdk() { sink = 0; Stream.concat(Arrays.stream(a), Arrays.stream(a)).forEach(this::add); return sink; }

    @Benchmark public Object listSource_seq() { sink = 0; Seq.viewOf(Arrays.asList(a)).stream().map(x -> x + 1).forEach(this::add); return sink; }
    @Benchmark public Object listSource_jdk() { sink = 0; Arrays.asList(a).stream().map(x -> x + 1).forEach(this::add); return sink; }

    @Benchmark public Object builder_seq() {
        SeqStream.Builder<Integer> b = SeqStream.builder();
        for (Integer x : a) b.accept(x);
        sink = 0;
        b.build().forEach(this::add);
        return sink;
    }
    @Benchmark public Object builder_jdk() {
        Stream.Builder<Integer> b = Stream.builder();
        for (Integer x : a) b.accept(x);
        sink = 0;
        b.build().forEach(this::add);
        return sink;
    }

    @Benchmark public Object streamSource_seq() { sink = 0; SeqStream.viewOf(Arrays.stream(a)).map(x -> x + 1).forEach(this::add); return sink; }
    @Benchmark public Object streamSource_jdk() { sink = 0; Arrays.stream(a).map(x -> x + 1).forEach(this::add); return sink; }

    // ---- no direct JDK equivalent ------------------------------------

    @Benchmark public Object zip_seq() { sink = 0; seq().zip(Arrays.stream(a), Integer::sum).forEach(this::add); return sink; }
    @Benchmark public Object zip_jdk() { sink = 0; IntStream.range(0, size).mapToObj(i -> a[i] + a[i]).forEach(this::add); return sink; }

    @Benchmark public Object mapIndexed_seq() { sink = 0; seq().mapIndexed((i, x) -> i + x).forEach(this::add); return sink; }
    @Benchmark public Object mapIndexed_jdk() { sink = 0; IntStream.range(0, size).mapToObj(i -> i + a[i]).forEach(this::add); return sink; }

    @Benchmark public Object windowSliding_seq() { sink = 0; seq().windowSliding(3).forEach(this::add); return sink; }
    @Benchmark public Object windowSliding_jdk() { sink = 0; IntStream.range(0, Math.max(0, size - 2)).mapToObj(i -> List.of(a[i], a[i + 1], a[i + 2])).forEach(this::add); return sink; }

    @Benchmark public Object reversed_seq() { return seq().reversed().toList(); }
    @Benchmark public Object reversed_jdk() { return IntStream.range(0, size).mapToObj(i -> a[size - 1 - i]).toList(); }

    @Benchmark public Object limitLast_seq() { return seq().limitLast(tenth).toList(); }
    @Benchmark public Object limitLast_jdk() { return jdk().skip(size - tenth).toList(); }

    @Benchmark public Object scan_seq() { sink = 0; seq().scan(() -> 0, Integer::sum).forEach(this::add); return sink; }
    @Benchmark public Object scan_jdk() { int[] n = {0}; sink = 0; jdk().map(x -> n[0] += x).forEach(this::add); return sink; }

    @Benchmark public Object indexOf_seq() { return seq().indexOf(needle); }
    @Benchmark public Object indexOf_jdk() { return Arrays.asList(a).indexOf(needle); }

    // ---- plumbing ----------------------------------------------------

    /**
     * Consumes an element. Cases that would otherwise end in {@code count()}
     * end here instead: a JDK {@code count()} on a sized source is allowed
     * to skip the pipeline entirely, so it would not measure the operator
     * under test.
     */
    private void add(Object o) {
        sink += o instanceof Collection<?> c ? c.size() : (Integer) o;
    }

    private static int drain(Iterator<Integer> it) {
        int sum = 0;
        while (it.hasNext()) sum += it.next();
        return sum;
    }

    /**
     * Invokes every {@code name_seq} / {@code name_jdk} pair and compares
     * the results, so a mismatched pair fails before it is timed.
     */
    private void crossCheck() throws Exception {
        Map<String, Method> seq = new TreeMap<>();
        Map<String, Method> jdk = new HashMap<>();
        for (Method m : getClass().getDeclaredMethods()) {
            if (m.getAnnotation(Benchmark.class) == null) continue;
            String name = m.getName();
            int cut = name.lastIndexOf('_');
            (name.endsWith("_seq") ? seq : jdk).put(name.substring(0, cut), m);
        }
        for (Map.Entry<String, Method> e : seq.entrySet()) {
            Method other = jdk.get(e.getKey());
            if (other == null) {
                throw new AssertionError(e.getKey() + ": no _jdk counterpart");
            }
            Object expected = normalize(other.invoke(this));
            Object actual = normalize(e.getValue().invoke(this));
            if (!Objects.deepEquals(expected, actual)) {
                throw new AssertionError(e.getKey() + ": _jdk gave " + brief(expected)
                        + " but _seq gave " + brief(actual));
            }
        }
    }

    /**
     * Makes results of the two sides comparable: {@code Seq} is a
     * {@code Collection} but not a {@code List}, so it never equals the
     * JDK side's list, and nested results have the same problem.
     */
    private static Object normalize(Object o) {
        if (o instanceof Optional<?> opt) {
            return opt.map(OperationBench::normalize);
        }
        if (o instanceof Map<?, ?> map) {
            Map<Object, Object> out = new HashMap<>();
            map.forEach((k, v) -> out.put(k, normalize(v)));
            return out;
        }
        if (o instanceof Set<?> set) {
            return set;
        }
        if (o instanceof Collection<?> c) {
            List<Object> out = new ArrayList<>(c.size());
            for (Object e : c) out.add(normalize(e));
            return out;
        }
        if (o instanceof Object[] array) {
            Object[] out = new Object[array.length];
            for (int i = 0; i < array.length; i++) out[i] = normalize(array[i]);
            return out;
        }
        return o;
    }

    private static String brief(Object o) {
        String text = o instanceof Object[] array ? Arrays.toString(array) : String.valueOf(o);
        return text.length() <= 120 ? text : text.substring(0, 120) + "...";
    }
}
