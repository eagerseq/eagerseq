package io.github.eagerseq;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.reverseOrder;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import static java.util.function.Function.identity;

final class Sources {

    private Sources() {
    }

    static <E> Source<E> toSource(Iterator<? extends E> iterator) {
        return new AbstractSource<E>(0) {
            public boolean forEachWhile(Sink<? super E> sink) {
                requireNonNull(sink);
                while (iterator.hasNext()) {
                    if (!sink.push(iterator.next())) return false;
                }
                return true;
            }
        };
    }

    static <E> Source<E> toSource(Object[] array) {
        return new ArraySource<>(array, 0, array.length, ORDERED);
    }

    static <E> Source<E> toSource(Iterable<? extends E> iterable) {
        // an indexed source for RandomAccess lists would beat the adapter's
        // pull loop, if it is ever worth the lines
        return toSource(iterable.spliterator());
    }

    static <E> Source<E> toSource(Stream<? extends E> stream) {
        // a stream built on our spliterator with no operations hands it back
        return toSource(stream.spliterator());
    }

    static <E> Source<E> toSource(
            Spliterator<? extends E> spliterator) {
        if (spliterator instanceof Source) {
            @SuppressWarnings("unchecked")
            Source<E> result = (Source<E>) spliterator;
            return result;
        }
        return new SpliteratorSource<>(spliterator);
    }

    static <E> Source<E> defer(
            Supplier<Source<E>> supplier,
            int characteristics) {
        return defer(supplier, characteristics, -1);
    }

    /**
     * A deferred source that will hold exactly {@code size} elements, or
     * {@code -1} where that is not known. A whole-source operation that
     * rearranges rather than selects, such as sorting or reversing, knows
     * its count from its input before it runs, and saying so lets the
     * collecting terminal downstream allocate once.
     */
    static <E> Source<E> defer(
            Supplier<Source<E>> supplier,
            int characteristics,
            long size) {
        requireNonNull(supplier);
        return new AbstractSource<E>(characteristics) {
            private Supplier<Source<E>> pending = supplier;
            private Source<E> delegate;

            public boolean forEachWhile(Sink<? super E> sink) {
                requireNonNull(sink);
                if (pending != null) {
                    Supplier<Source<E>> supplier = pending;
                    pending = null;
                    delegate = requireNonNull(supplier.get());
                }
                if (delegate == null) {
                    throw new IllegalStateException(
                            "deferred computation previously failed");
                }
                return delegate.forEachWhile(sink);
            }

            public int characteristics() {
                int characteristics = super.characteristics();
                return size < 0 ? characteristics : characteristics | SIZED;
            }

            public long estimateSize() {
                if (delegate != null) return delegate.estimateSize();
                return size < 0 ? super.estimateSize() : size;
            }
        };
    }

    static int ordered(Spliterator<?> spliterator) {
        return spliterator.characteristics() & ORDERED;
    }

    private static int ordered(
            Spliterator<?> first, Spliterator<?> second) {
        return ordered(first) & ordered(second);
    }

    private static <E> Source<E> emptySource(int characteristics) {
        return new ArraySource<>(ArrayBuilder.EMPTY, 0, 0, characteristics);
    }

    /**
     * The element count of a sized spliterator as an {@code int} array
     * length, or {@code -1} where it is unknown or too large to allocate.
     * Named apart from {@code Spliterator.getExactSizeWhenKnown}, which
     * postdates the release target and returns a {@code long} with
     * {@code -1} meaning unknown only. Collecting
     * into a container that starts at this size allocates once instead of
     * growing and copying, which is the difference between about one and
     * about three allocated references per element.
     */
    static int exactSizeInt(Spliterator<?> spliterator) {
        long size = exactSizeLong(spliterator);
        return size >= 0 && size <= ArrayBuilder.MAX_LENGTH ? (int) size : -1;
    }

    /**
     * The element count of a sized spliterator, or {@code -1} where it is
     * unknown. This is {@code Spliterator.getExactSizeWhenKnown}, which
     * postdates the release target, and is named apart from it for that
     * reason. Use {@link #exactSizeInt} for an array or table capacity,
     * which must also fit an {@code int}.
     */
    static long exactSizeLong(Spliterator<?> spliterator) {
        return spliterator.hasCharacteristics(SIZED)
                ? spliterator.estimateSize()
                : -1;
    }

    static Object[] toArray(Iterable<?> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toArray();
    }

    static Object[] toArray(Iterator<?> iterator) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(iterator, 0), false).toArray();
    }

    static Object[] toArray(Spliterator<?> spliterator) {
        ArrayBuilder<Object> builder = new ArrayBuilder<>(
                exactSizeInt(spliterator));
        spliterator.forEachRemaining(builder);
        return builder.buildArray();
    }

    @SuppressWarnings("unchecked")
    public static <E, A> A[] toArray(
            Spliterator<E> spliterator, IntFunction<A[]> generator) {
        ArrayBuilder<A> builder = new ArrayBuilder<>(
                generator, exactSizeInt(spliterator));
        spliterator.forEachRemaining((ArrayBuilder<E>) builder);
        return builder.buildArray();
    }

    static <E, T> T[] toArray(
            Spliterator<E> spliterator, T[] ts) {
        Class<?> type = ts.getClass().getComponentType();
        @SuppressWarnings("unchecked")
        IntFunction<T[]> generator = length -> (T[]) Array.newInstance(type,
                length);
        T[] array = toArray(spliterator, generator);
        if (ts.length < array.length) return array;
        System.arraycopy(array, 0, ts, 0, array.length);
        if (array.length < ts.length) ts[array.length] = null;
        return ts;
    }

    static <E> List<E> toList(Spliterator<E> spliterator) {
        int size = exactSizeInt(spliterator);
        List<E> list = size < 0 ? new ArrayList<>() : new ArrayList<>(size);
        spliterator.forEachRemaining(list::add);
        return Collections.unmodifiableList(list);
    }

    static <E> Set<E> toSet(Spliterator<E> spliterator) {
        Set<E> set = new LinkedHashSet<>();
        spliterator.forEachRemaining(set::add);
        return Collections.unmodifiableSet(set);
    }

    static <E> Map<E, E> toMap(Spliterator<E> spliterator) {
        return toMap(spliterator, identity(), identity(), null);
    }

    static <E, K> Map<K, E> toMap(
            Spliterator<E> spliterator,
            Function<? super E, ? extends K> keyMapper) {
        return toMap(spliterator, keyMapper, identity(), null);
    }

    static <E, K, V> Map<K, V> toMap(
            Spliterator<E> spliterator,
            Function<? super E, ? extends K> keyMapper,
            Function<? super E, ? extends V> valueMapper) {
        return toMap(spliterator, keyMapper, valueMapper, null);
    }

    static <E, K, V> Map<K, V> toMap(
            Spliterator<E> spliterator,
            Function<? super E, ? extends K> keyMapper,
            Function<? super E, ? extends V> valueMapper,
            BinaryOperator<V> merger) {
        Map<K, V> map = new LinkedHashMap<>();
        spliterator.forEachRemaining(e -> {
            K key = keyMapper.apply(e);
            V value = valueMapper.apply(e);
            int before = map.size();
            V present = map.put(key, value);
            if (map.size() == before) {
                if (merger == null) {
                    throw new IllegalStateException(String.format(
                            "duplicate key %s "
                                    + "(attempted merging values %s and %s)",
                            key, present, value));
                }
                map.put(key, merger.apply(present, value));
            }
        });
        return Collections.unmodifiableMap(map);
    }

    static <E> E[] reversed(Spliterator<E> spliterator) {
        @SuppressWarnings("unchecked")
        E[] array = (E[]) toArray(spliterator);
        Collections.reverse(Arrays.asList(array));
        return array;
    }

    static <E> E[] rotated(Spliterator<E> spliterator, int distance) {
        @SuppressWarnings("unchecked")
        E[] array = (E[]) toArray(spliterator);
        Collections.rotate(Arrays.asList(array), distance);
        return array;
    }

    static <E> E[] shuffled(Spliterator<E> spliterator, Random random) {
        @SuppressWarnings("unchecked")
        E[] array = (E[]) toArray(spliterator);
        Collections.shuffle(Arrays.asList(array), random);
        return array;
    }

    static long count(Spliterator<?> spliterator) {
        long size = exactSizeLong(spliterator);
        if (size >= 0) return size;
        long[] count = new long[1];
        spliterator.forEachRemaining(e -> count[0]++);
        return count[0];
    }

    static int size(Spliterator<?> spliterator) {
        // clamps like Collection.size() documents, rather than throws
        return (int) Math.min(count(spliterator), Integer.MAX_VALUE);
    }

    static boolean isEmpty(Source<?> source) {
        long size = exactSizeLong(source);
        if (size >= 0) return size == 0;
        return source.forEachWhile(e -> false);
    }

    static <E> int listHash(Spliterator<E> spliterator) {
        int[] hash = new int[]{1};
        spliterator.forEachRemaining(e -> {
            hash[0] *= 31;
            hash[0] += Objects.hashCode(e);
        });
        return hash[0];
    }

    static boolean listEquals(Source<?> spl0, Spliterator<?> spl1) {
        Box<Object> next1 = new Box<>();
        // spl0 pushes, spl1 is pulled in step; stop on the first difference
        boolean exhausted0 = spl0.forEachWhile(
                e -> spl1.tryAdvance(next1) && Objects.equals(e, next1.value));
        return exhausted0 && !spl1.tryAdvance(next1);
    }

    static boolean setEquals(Spliterator<?> spl0, Spliterator<?> spl1) {
        Set<Object> set0 = new HashSet<>();
        Set<Object> set1 = new HashSet<>();
        spl0.forEachRemaining(set0::add);
        spl1.forEachRemaining(set1::add);
        return set0.equals(set1);
    }

    static boolean multisetEquals(Spliterator<?> spl0, Spliterator<?> spl1) {
        Map<Object, Long> set0 = new HashMap<>();
        Map<Object, Long> set1 = new HashMap<>();
        spl0.forEachRemaining(e -> multisetAdd(set0, e));
        spl1.forEachRemaining(e -> multisetAdd(set1, e));
        return set0.equals(set1);
    }

    static <E> Optional<E> findFirst(Source<E> source) {
        return Box.asOptional(firstBox(source));
    }

    static <E> Optional<E> findLast(Source<E> source) {
        return Box.asOptional(lastBox(source));
    }

    static <E> Optional<E> findOnly(Source<E> source) {
        return Box.asOptional(onlyBox(source));
    }

    static <E> E getFirst(Source<E> source) {
        return Box.orThrow(firstBox(source), Sources::emptySequence);
    }

    static <E> E getLast(Source<E> source) {
        return Box.orThrow(lastBox(source), Sources::emptySequence);
    }

    static <E> E getOnly(Source<E> source) {
        return Box.orThrow(onlyBox(source), Sources::notExactlyOne);
    }

    static <E> Optional<E> toOptional(Source<E> source) {
        Box<E> first = new Box<>();
        Box<E> second = new Box<>();
        int[] count = new int[1];
        source.forEachWhile(e -> {
            (count[0]++ == 0 ? first : second).value = e;
            return count[0] < 2;
        });
        if (count[0] == 0) return Optional.empty();
        if (count[0] > 1) throw moreThanOne(first.value, second.value);
        return Optional.of(first.value);
    }

    private static <E> Box<E> firstBox(Source<E> source) {
        Box<E> next = new Box<>();
        return source.forEachWhile(e -> {
            next.value = e;
            return false;
        }) ? null : next;
    }

    private static <E> Box<E> lastBox(Source<E> source) {
        Box<E> next = new Box<>();
        boolean[] set = new boolean[1];
        source.forEachWhile(e -> {
            next.value = e;
            return set[0] = true;
        });
        return set[0] ? next : null;
    }

    // Unlike toOptional, an empty source and one with more than one element
    // are both absent, so that findOnly and getOnly agree on both.
    private static <E> Box<E> onlyBox(Source<E> source) {
        Box<E> next = new Box<>();
        int[] count = new int[1];
        source.forEachWhile(e -> {
            next.value = e;
            return ++count[0] < 2;
        });
        return count[0] == 1 ? next : null;
    }

    static <P, E> Source<E> flatten(
            Source<? extends P> parts,
            Function<? super P, ? extends Source<? extends E>> toSource) {
        return flatten(parts, toSource, ignored -> {}, ignored -> {});
    }

    static <P, E> Source<E> flatten(
            Source<? extends P> parts,
            Function<? super P, ? extends Source<? extends E>> toSource,
            Consumer<? super P> close,
            Consumer<Runnable> onClose) {
        return new Stage<P, E>(parts) {
            private P currentPart;
            private Source<? extends E> current;
            private boolean closed;
            {
                onClose.accept(() -> {
                    closed = true;
                    closeCurrent();
                });
            }
            public boolean push(P part) {
                currentPart = part;
                if (part == null) return true;
                current = toSource.apply(part);
                if (!current.forEachWhile(down)) return false;
                closeCurrent();
                return true;
            }
            public boolean forEachWhile(Sink<? super E> sink) {
                requireNonNull(sink);
                if (closed) return true;
                // resume the part a previous stop left behind
                if (current != null && !current.forEachWhile(sink)) {
                    return false;
                }
                closeCurrent();
                return super.forEachWhile(sink);
            }
            private void closeCurrent() {
                P part = currentPart;
                currentPart = null;
                current = null;
                if (part == null) return;
                close.accept(part);
            }
        };
    }

    @SafeVarargs
    static <P, E> Source<E> concat(
            Function<? super P, ? extends Source<? extends E>> toSource,
            P... parts) {
        @SuppressWarnings("unchecked")
        Source<? extends E>[] sources = new Source[parts.length];
        int c = ORDERED;
        for (int i = 0; i < parts.length; i++) {
            sources[i] = toSource.apply(parts[i]);
            c &= ordered(sources[i]);
        }
        return new AbstractSource<E>(c) {
            private int index;
            public boolean forEachWhile(Sink<? super E> sink) {
                requireNonNull(sink);
                while (index < sources.length) {
                    if (!sources[index].forEachWhile(sink)) return false;
                    index++;
                }
                return true;
            }
        };
    }

    static <E> E get(
            Source<E> source, int index) {
        Box<E> next = new Box<>();
        long[] length = new long[1];
        boolean exhausted = source.forEachWhile(e -> {
            next.value = e;
            return length[0]++ != index;
        });
        if (!exhausted) return next.value;
        throw indexOutOfBounds("index", index, length[0]);
    }

    static <E> Source<Integer> indexesOf(
            Source<E> source, Object object) {
        return new Stage<E, Integer>(source, ORDERED) {
            private int index;
            public boolean push(E e) {
                int i = index++;
                return !Objects.equals(object, e) || down.push(i);
            }
        };
    }

    static <E> int indexOf(
            Source<E> source, Object object) {
        return findFirst(indexesOf(source, object)).orElse(-1);
    }

    static <E> int lastIndexOf(
            Source<E> source, Object object) {
        return findLast(indexesOf(source, object)).orElse(-1);
    }

    static <E> boolean contains(
            Source<E> source, Object object) {
        return !isEmpty(indexesOf(source, object));
    }

    static <E, F, R> Source<R> zip(
            Source<E> spl0,
            Spliterator<? extends F> spl1,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        return new Stage<E, R>(spl0, ordered(spl0, spl1)) {
            private final Box<F> box1 = new Box<>();
            private boolean done;
            public boolean push(E e) {
                if (!spl1.tryAdvance(box1)) {
                    done = true;
                    return false;
                }
                return down.push(mapper.apply(e, box1.value));
            }
            public boolean forEachWhile(Sink<? super R> sink) {
                requireNonNull(sink);
                return done || super.forEachWhile(sink) || done;
            }
        };
    }

    static Source<Integer> range(int from, int to) {
        return range(from, to, 0);
    }

    static Source<Long> range(long from, long to) {
        return range(from, to, 0);
    }

    static Source<Integer> rangeClosed(int from, int to) {
        return range(from, to, from <= to ? 1 : 0);
    }

    static Source<Long> rangeClosed(long from, long to) {
        return range(from, to, from <= to ? 1 : 0);
    }

    private static Source<Integer> range(
            int from, int to, int pendingLast) {
        return new AbstractSource<Integer>(ORDERED) {
            private int index = from;
            private int last = pendingLast;
            public boolean forEachWhile(Sink<? super Integer> sink) {
                requireNonNull(sink);
                while (index < to) {
                    if (!sink.push(index++)) return false;
                }
                if (last > 0) {
                    last = 0;
                    return sink.push(index);
                }
                return true;
            }
        };
    }

    private static Source<Long> range(
            long from, long to, int pendingLast) {
        return new AbstractSource<Long>(ORDERED) {
            private long index = from;
            private int last = pendingLast;
            public boolean forEachWhile(Sink<? super Long> sink) {
                requireNonNull(sink);
                while (index < to) {
                    if (!sink.push(index++)) return false;
                }
                if (last > 0) {
                    last = 0;
                    return sink.push(index);
                }
                return true;
            }
        };
    }

    static <E> Source<E> repeat(E element) {
        return new AbstractSource<E>(ORDERED) {
            public boolean forEachWhile(Sink<? super E> sink) {
                requireNonNull(sink);
                while (sink.push(element)) {
                }
                return false;
            }
        };
    }

    static <E> Source<E> repeat(E element, int count) {
        return limit(repeat(element), count);
    }

    static <E> Source<E> generate(Supplier<? extends E> supplier) {
        return new AbstractSource<E>(ORDERED) {
            public boolean forEachWhile(Sink<? super E> sink) {
                requireNonNull(sink);
                while (sink.push(supplier.get())) {
                }
                return false;
            }
        };
    }

    static <E> Source<E> generate(
            Supplier<? extends E> supplier, int count) {
        return limit(generate(supplier), count);
    }

    static <E> Source<E> iterate(
            E seed, UnaryOperator<E> operator) {
        return new AbstractSource<E>(ORDERED) {
            private E next = seed;
            private boolean started;
            public boolean forEachWhile(Sink<? super E> sink) {
                requireNonNull(sink);
                if (!started) {
                    started = true;
                    if (!sink.push(next)) return false;
                }
                while (true) {
                    next = operator.apply(next);
                    if (!sink.push(next)) return false;
                }
            }
        };
    }

    static <E> Source<E> iterate(
            E seed, Predicate<? super E> hasNext, UnaryOperator<E> next) {
        return takeWhile(iterate(seed, next), hasNext);
    }

    static <E> Source<Integer> indexes(Source<E> source) {
        return new Stage<E, Integer>(source, ORDERED) {
            private long index;
            public boolean push(E e) {
                // throwing is acceptable in this rare case of overflow
                return down.push(Math.toIntExact(index++));
            }
        };
    }

    static <E> Source<E> limitLast(
            Source<E> source, long size) {
        if (size == 0) return emptySource(ordered(source));
        return new Stage<E, E>(source) {
            private ArrayBuilder<E> builder = new ArrayBuilder<>();
            private int used;
            private int index;
            private boolean filled;
            private E[] queue;
            public boolean push(E e) {
                // fill by appending, so that the queue is sized from the
                // data and not from size, and only then overwrite in
                // place, by which point its length is settled
                if (queue == null) {
                    builder.accept(e);
                    if (++used == size) queue = builder.buildArray();
                } else {
                    queue[index] = e;
                    index++;
                    index %= queue.length;
                }
                return true;
            }
            public boolean forEachWhile(Sink<? super E> sink) {
                requireNonNull(sink);
                if (!filled) {
                    filled = true;
                    // test never stops the traversal, so it drains up
                    up.forEachWhile(this);
                    if (queue == null) queue = builder.buildArray();
                }
                while (used > 0) {
                    used--;
                    E e = queue[index];
                    index++;
                    index %= queue.length;
                    if (!sink.push(e)) return false;
                }
                return true;
            }
        };
    }

    static <E> Source<E> skipLast(
            Source<E> source, long size) {
        if (size == 0) return source;
        return new Stage<E, E>(source) {
            private final ArrayBuilder<E> builder = new ArrayBuilder<>();
            private int used;
            private int index;
            private E[] queue;
            public boolean push(E e) {
                // as limitLast, the queue is sized from the data
                if (queue == null) {
                    builder.accept(e);
                    if (++used == size) queue = builder.buildArray();
                    return true;
                }
                E first = queue[index];
                queue[index] = e;
                index++;
                index %= queue.length;
                return down.push(first);
            }
        };
    }

    static <E> Source<E> takeWhile(
            Source<E> source,
            Predicate<? super E> predicate) {
        return new Stage<E, E>(source) {
            private boolean found;
            public boolean push(E e) {
                if (predicate.test(e)) return down.push(e);
                found = true;
                return false;
            }
            public boolean forEachWhile(Sink<? super E> sink) {
                requireNonNull(sink);
                return found || super.forEachWhile(sink) || found;
            }
        };
    }

    static <E> Source<E> dropWhile(
            Source<E> source,
            Predicate<? super E> predicate) {
        return new Stage<E, E>(source) {
            private boolean found;
            public boolean push(E e) {
                if (!found && predicate.test(e)) return true;
                found = true;
                return down.push(e);
            }
        };
    }

    static <E> Source<E> intersection(
            Source<E> first,
            Spliterator<?> second) {
        return multisetOperation(first, second, false);
    }

    static <E> Source<E> difference(
            Source<E> first,
            Spliterator<?> second) {
        return multisetOperation(first, second, true);
    }

    static <E> Source<E> union(
            Source<E> first,
            Source<? extends E> second) {
        Map<Object, Long> multiset = new HashMap<>();
        return concat(identity(),
                peek(first, e -> multisetAdd(multiset, e)),
                multisetFilter(second, multiset, true));
    }

    static boolean containsMultiset(
            Spliterator<?> first,
            Source<?> second) {
        return isEmpty(multisetOperation(second, first, true));
    }

    static boolean containsAll(
            Spliterator<?> first,
            Source<?> second) {
        return containsMultiset(first, distinct(second));
    }

    static Source<Integer> indexesOfSlice(
            Source<?> source, Spliterator<?> slice) {
        Object[] array = toArray(slice);
        int[] jumps = new int[array.length + 1];
        copyInto(matchLengths(toSource(array), array, jumps, -1), jumps);
        return toMatchIndexes(matchLengths(source, array, jumps, 0),
                array.length);
    }

    static int indexOfSlice(
            Source<?> source, Spliterator<?> slice) {
        return findFirst(indexesOfSlice(source, slice)).orElse(-1);
    }

    static int lastIndexOfSlice(
            Source<?> source, Spliterator<?> slice) {
        return findLast(indexesOfSlice(source, slice)).orElse(-1);
    }

    static boolean containsSlice(
            Source<?> source, Spliterator<?> slice) {
        return !isEmpty(indexesOfSlice(source, slice));
    }

    static boolean startsWith(
            Source<?> source, Spliterator<?> slice) {
        Object[] array = toArray(slice);
        return listEquals(
                limit(source, array.length), toSource(array));
    }

    static boolean endsWith(
            Source<?> source, Spliterator<?> slice) {
        Object[] array = toArray(slice);
        return listEquals(
                limitLast(source, array.length), toSource(array));
    }

    static <E> Source<E[]> permutations(Object[] array) {
        return permutations(array, array.length);
    }

    static <E> Source<E[]> permutations(Object[] array, int k) {
        if (k > array.length) {
            return emptySource(ORDERED);
        }
        return new AbstractSource<E[]>(ORDERED) {
            private int[] index = IntStream.range(0, k).toArray();
            private final boolean[] used = new boolean[array.length];
            {
                Arrays.fill(used, 0, k, true);
            }
            public boolean forEachWhile(Sink<? super E[]> sink) {
                requireNonNull(sink);
                while (index != null) {
                    @SuppressWarnings("unchecked")
                    E[] r = (E[]) Arrays.stream(index)
                            .mapToObj(i -> array[i]).toArray();
                    int a = index.length - 1;
                    for (; a >= 0; a--) {
                        used[index[a]] = false;
                        int i = index[a] + 1;
                        while (i < used.length && used[i]) i++;
                        if (i < used.length) {
                            index[a] = i;
                            used[i] = true;
                            i = 0;
                            for (int b = a + 1; b < index.length; b++) {
                                while (used[i]) i++;
                                index[b] = i;
                                used[i] = true;
                            }
                            break;
                        }
                    }
                    if (a < 0) index = null;
                    if (!sink.push(r)) return false;
                }
                return true;
            }
        };
    }

    static <E> Source<E[]> allPermutations(Object[] array) {
        return flatMap(
                range(0, array.length + 1),
                k -> permutations(array, k));
    }

    static <E> Source<E[]> combinations(Object[] array, int k) {
        if (k > array.length) {
            return emptySource(ORDERED);
        }
        return new AbstractSource<E[]>(ORDERED) {
            private int[] index = IntStream.range(0, k).toArray();
            public boolean forEachWhile(Sink<? super E[]> sink) {
                requireNonNull(sink);
                while (index != null) {
                    @SuppressWarnings("unchecked")
                    E[] r = (E[]) Arrays.stream(index)
                            .mapToObj(i -> array[i]).toArray();
                    int a = index.length - 1;
                    int i = array.length - 1;
                    while (a >= 0 && index[a] == i--) a--;
                    if (a < 0) {
                        index = null;
                    } else {
                        i = ++index[a++] + 1;
                        while (a < index.length) index[a++] = i++;
                    }
                    if (!sink.push(r)) return false;
                }
                return true;
            }
        };
    }

    static <E> Source<E[]> allCombinations(Object[] array) {
        return flatMap(
                range(0, array.length + 1),
                k -> combinations(array, k));
    }

    static <E> Source<E[]> power(Object[] array, int k) {
        return new AbstractSource<E[]>(ORDERED) {
            private int[] index = array.length == 0 && k > 0
                    ? null
                    : new int[k];
            public boolean forEachWhile(Sink<? super E[]> sink) {
                requireNonNull(sink);
                while (index != null) {
                    @SuppressWarnings("unchecked")
                    E[] r = (E[]) Arrays.stream(index)
                            .mapToObj(i -> array[i]).toArray();
                    int a = index.length - 1;
                    while (a >= 0 && ++index[a] == array.length) {
                        index[a--] = 0;
                    }
                    if (a < 0) index = null;
                    if (!sink.push(r)) return false;
                }
                return true;
            }
        };
    }

    static <E, F, R> Source<R> product(
            Source<E> first,
            Object[] second,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        if (second.length == 0) return emptySource(ordered(first));
        return flatMap(first, e -> map(
                Sources.<F>toSource(second), f -> mapper.apply(e, f)));
    }

    static <E> Source<E> filter(
            Source<E> source,
            Predicate<? super E> predicate) {
        return new Stage<E, E>(source) {
            public boolean push(E e) {
                return !predicate.test(e) || down.push(e);
            }
        };
    }

    static <E, R> Source<R> map(
            Source<E> source,
            Function<? super E, ? extends R> mapper) {
        return new MappingStage<E, R>(source) {
            R map(E e) {
                return mapper.apply(e);
            }
        };
    }

    static <E, R> Source<R> mapIndexed(
            Source<E> source,
            BiFunction<? super Integer, ? super E, ? extends R> mapper) {
        return new MappingStage<E, R>(source) {
            private long index;
            R map(E e) {
                return mapper.apply(Math.toIntExact(index++), e);
            }
        };
    }

    static <E, R> Source<R> flatMap(
            Source<E> source,
            Function<? super E, ? extends Source<? extends R>> mapper) {
        return flatMap(source, mapper, value -> value);
    }

    static <E, P, R> Source<R> flatMap(
            Source<E> source,
            Function<? super E, ? extends P> mapper,
            Function<? super P, ? extends Source<? extends R>> toSource) {
        return flatten(map(source, mapper), toSource);
    }

    static <E, P, R> Source<R> flatMap(
            Source<E> source,
            Function<? super E, ? extends P> mapper,
            Function<? super P, ? extends Source<? extends R>> toSource,
            Consumer<? super P> close,
            Consumer<Runnable> onClose) {
        return flatten(
                map(source, mapper), toSource, close, onClose);
    }

    static <E, R> Source<R> mapMulti(
            Source<E> source,
            BiConsumer<? super E, ? super Consumer<R>> mapper) {
        return new Stage<E, R>(source) {
            // pushed straight through until the sink stops, after which
            // the rest of that element's results are buffered for later
            @SuppressWarnings("unchecked")
            private R[] buffer = (R[]) ArrayBuilder.EMPTY;
            private int index;
            private ArrayBuilder<R> overflow;
            private final Consumer<R> sink = r -> {
                if (overflow != null) overflow.accept(r);
                else if (!down.push(r)) overflow = new ArrayBuilder<>();
            };
            public boolean push(E e) {
                mapper.accept(e, sink);
                if (overflow == null) return true;
                buffer = overflow.buildArray();
                overflow = null;
                index = 0;
                return false;
            }
            public boolean forEachWhile(Sink<? super R> sink) {
                requireNonNull(sink);
                while (index < buffer.length) {
                    if (!sink.push(buffer[index++])) return false;
                }
                return super.forEachWhile(sink);
            }
        };
    }

    static <E> Source<E> distinct(Source<E> source) {
        return distinctBy(source, null);
    }

    static <E> Source<E> distinctBy(
            Source<E> source,
            Function<? super E, ?> keyMapper) {
        // null is an internal identity sentinel for private use only
        return new Stage<E, E>(source) {
            private final Set<Object> seen = new HashSet<>();
            public boolean push(E e) {
                return !seen.add(keyMapper == null ? e : keyMapper.apply(e))
                        || down.push(e);
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <E, K, V> Map<K, V> groupBy(
            Spliterator<E> spliterator,
            Function<? super E, ? extends K> keyMapper,
            Function<? super E[], ? extends V> valueMapper) {
        Map<K, Object> map = new LinkedHashMap<>();
        spliterator.forEachRemaining(e -> ((ArrayBuilder<E>) map
                .computeIfAbsent(keyMapper.apply(e),
                        key -> new ArrayBuilder<>()))
                .accept(e));
        map.replaceAll((key, builder) -> valueMapper.apply(
                ((ArrayBuilder<E>) builder).buildArray()));
        return (Map<K, V>) Collections.unmodifiableMap(map);
    }

    static <E, V> Map<Boolean, V> partitionBy(
            Spliterator<E> spliterator,
            Predicate<? super E> predicate,
            Function<? super E[], ? extends V> valueMapper) {
        ArrayBuilder<E> rejected = new ArrayBuilder<>();
        ArrayBuilder<E> selected = new ArrayBuilder<>();
        spliterator.forEachRemaining(
                e -> (predicate.test(e) ? selected : rejected).accept(e));
        Map<Boolean, V> map = new LinkedHashMap<>();
        map.put(false, valueMapper.apply(rejected.buildArray()));
        map.put(true, valueMapper.apply(selected.buildArray()));
        return Collections.unmodifiableMap(map);
    }

    static <E> E[] sorted(Spliterator<E> spliterator) {
        return sorted(spliterator, null);
    }

    static <E> E[] sorted(
            Spliterator<E> spliterator,
            Comparator<? super E> comparator) {
        // null is an internal natural-order sentinel for private use only
        @SuppressWarnings("unchecked")
        E[] array = (E[]) toArray(spliterator);
        Arrays.sort(array, comparator);
        return array;
    }

    static <E> Source<E> limit(
            Source<E> source, long size) {
        return new Stage<E, E>(source) {
            private long index;
            private boolean more;
            public boolean push(E e) {
                index++;
                more = down.push(e);
                return more && index < size;
            }
            public boolean forEachWhile(Sink<? super E> sink) {
                requireNonNull(sink);
                // exhausted once the limit is reached, unless it was the
                // sink that stopped the traversal
                return index >= size || super.forEachWhile(sink) || more;
            }
        };
    }

    static <E> Source<E> skip(
            Source<E> source, long size) {
        return new Stage<E, E>(source) {
            private long index;
            public boolean push(E e) {
                return index++ < size || down.push(e);
            }
        };
    }

    static <E> Source<E> slice(
            Source<E> source, int from, int to) {
        return skip(limit(source, to), from);
    }

    static <E, R> R reduce(
            Spliterator<E> spliterator,
            R identity,
            BiFunction<R, ? super E, R> accumulator) {
        Box<R> result = new Box<>();
        result.value = identity;
        spliterator.forEachRemaining(
                e -> result.value = accumulator.apply(result.value, e));
        return result.value;
    }

    static <E> Optional<E> reduce(
            Spliterator<E> spliterator,
            BinaryOperator<E> accumulator) {
        Box<E> next = new Box<>();
        if (!spliterator.tryAdvance(next)) return Optional.empty();
        return Optional.of(reduce(spliterator, next.value, accumulator));
    }

    static <E, R> R collect(
            Spliterator<E> spliterator,
            Supplier<R> supplier,
            BiConsumer<R, ? super E> accumulator) {
        R acc = supplier.get();
        spliterator.forEachRemaining(e -> accumulator.accept(acc, e));
        return acc;
    }

    static <E, A, R> R collect(
            Spliterator<E> spliterator,
            Collector<? super E, A, R> collector) {
        return collector.finisher().apply(collect(
                spliterator, collector.supplier(), collector.accumulator()));
    }

    static <E, R> R collectWhile(
            Source<E> source,
            Supplier<R> supplier,
            BiPredicate<R, ? super E> accumulator) {
        R acc = supplier.get();
        source.forEachWhile(e -> accumulator.test(acc, e));
        return acc;
    }

    static <E> int sumOfInt(
            Spliterator<E> spliterator,
            ToIntFunction<? super E> mapper) {
        int[] sum = new int[1];
        spliterator.forEachRemaining(
                e -> sum[0] += mapper.applyAsInt(e));
        return sum[0];
    }

    static <E> long sumOfLong(
            Spliterator<E> spliterator,
            ToLongFunction<? super E> mapper) {
        long[] sum = new long[1];
        spliterator.forEachRemaining(
                e -> sum[0] += mapper.applyAsLong(e));
        return sum[0];
    }

    static <E> double sumOfDouble(
            Spliterator<E> spliterator,
            ToDoubleFunction<? super E> mapper) {
        double[] sum = new double[1];
        spliterator.forEachRemaining(
                e -> sum[0] += mapper.applyAsDouble(e));
        return sum[0];
    }

    static <E> int productOfInt(
            Spliterator<E> spliterator,
            ToIntFunction<? super E> mapper) {
        int[] product = {1};
        spliterator.forEachRemaining(
                e -> product[0] *= mapper.applyAsInt(e));
        return product[0];
    }

    static <E> long productOfLong(
            Spliterator<E> spliterator,
            ToLongFunction<? super E> mapper) {
        long[] product = {1L};
        spliterator.forEachRemaining(
                e -> product[0] *= mapper.applyAsLong(e));
        return product[0];
    }

    static <E> double productOfDouble(
            Spliterator<E> spliterator,
            ToDoubleFunction<? super E> mapper) {
        double[] product = {1.0};
        spliterator.forEachRemaining(
                e -> product[0] *= mapper.applyAsDouble(e));
        return product[0];
    }

    static <E> Optional<E> min(Spliterator<E> spliterator) {
        return min(spliterator, naturalOrder());
    }

    static <E> Optional<E> min(
            Spliterator<E> spliterator,
            Comparator<? super E> comparator) {
        Box<E> min = new Box<>();
        if (!spliterator.tryAdvance(min)) return Optional.empty();
        spliterator.forEachRemaining(e -> {
            if (comparator.compare(e, min.value) < 0) min.value = e;
        });
        return Optional.of(min.value);
    }

    static <E> Optional<E> max(Spliterator<E> spliterator) {
        return max(spliterator, naturalOrder());
    }

    static <E> Optional<E> max(
            Spliterator<E> spliterator,
            Comparator<? super E> comparator) {
        return min(spliterator, reverseOrder(comparator));
    }

    private static <E> Comparator<? super E> naturalOrder() {
        // as unchecked as the natural ordering of Arrays.sort(array, null)
        @SuppressWarnings({"unchecked", "rawtypes"})
        Comparator<? super E> order = (Comparator) Comparator.naturalOrder();
        return order;
    }

    static <E> boolean noneMatch(
            Source<E> source,
            Predicate<? super E> predicate) {
        return source.forEachWhile(e -> !predicate.test(e));
    }

    static <E> boolean anyMatch(
            Source<E> source,
            Predicate<? super E> predicate) {
        return !noneMatch(source, predicate);
    }

    static <E> boolean allMatch(
            Source<E> source,
            Predicate<? super E> predicate) {
        return noneMatch(source, predicate.negate());
    }

    static <E> Source<E[]> windowFixed(
            Source<E> source, int size) {
        return window(source, size, size);
    }

    static <E> Source<E[]> windowSliding(
            Source<E> source, int size) {
        return window(source, size, 1);
    }

    static <E> Source<E[]> window(
            Source<E> source, int size, int step) {
        // step >= 1
        return new Stage<E, E[]>(source) {
            private ArrayBuilder<E> builder = new ArrayBuilder<>();
            private E[] window;
            private int count;
            private int retained;
            private int toSkip;
            private boolean finished;
            public boolean push(E e) {
                if (toSkip > 0) {
                    toSkip--;
                    return true;
                }
                if (window == null) {
                    // the first window is sized from the data
                    builder.accept(e);
                    if (++count < size) return true;
                    window = builder.buildArray();
                } else {
                    window[count++] = e;
                    if (count < size) return true;
                }
                E[] full = window;
                int from = Math.min(size, step);
                window = Arrays.copyOfRange(full, from, from + size);
                count = retained = size - from;
                toSkip = step - from;
                return down.push(full);
            }
            public boolean forEachWhile(Sink<? super E[]> sink) {
                requireNonNull(sink);
                if (finished) return true;
                if (!super.forEachWhile(sink)) return false;
                finished = true;
                // the short window is dropped unless the source added to
                // the elements retained from the last
                if (count <= retained) return true;
                return sink.push(window == null
                        ? builder.buildArray()
                        : Arrays.copyOf(window, count));
            }
        };
    }

    static <E, R> Source<R> scan(
            Source<E> source, Supplier<R> initial,
            BiFunction<? super R, ? super E, ? extends R> scanner) {
        return new MappingStage<E, R>(source) {
            private R accumulated = initial.get();
            R map(E e) {
                accumulated = scanner.apply(accumulated, e);
                return accumulated;
            }
        };
    }

    static <E> Source<E> peek(
            Source<E> source,
            Consumer<? super E> peeker) {
        return new MappingStage<E, E>(source) {
            E map(E e) {
                peeker.accept(e);
                return e;
            }
        };
    }

    static <E> Source<E> unordered(Source<E> source) {
        return new AbstractSource<E>(0) {
            public boolean forEachWhile(Sink<? super E> sink) {
                requireNonNull(sink);
                return source.forEachWhile(sink);
            }
        };
    }

    static String toString(
            Spliterator<?> spliterator,
            CharSequence delimiter,
            CharSequence prefix,
            CharSequence suffix) {
        StringJoiner joiner = new StringJoiner(delimiter, prefix, suffix);
        spliterator.forEachRemaining(e -> joiner.add(String.valueOf(e)));
        return joiner.toString();
    }

    static void requirePositiveArgument(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(
                    name + " " + value + " was not positive");
        }
    }

    static void requireNonNegativeArgument(String name, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    name + " " + value + " was negative");
        }
    }

    static void requireNonNegativeIndex(String name, int value) {
        if (value < 0) {
            throw new IndexOutOfBoundsException(
                    name + " " + value + " was negative");
        }
    }

    static IndexOutOfBoundsException indexOutOfBounds(
            String name, int index, long length) {
        return new IndexOutOfBoundsException(
                name + " " + index + " out of bounds for length " + length);
    }

    static NoSuchElementException emptySequence() {
        return new NoSuchElementException("sequence is empty");
    }

    static NoSuchElementException notExactlyOne() {
        return new NoSuchElementException(
                "sequence does not contain exactly one element");
    }

    static IllegalStateException moreThanOne(Object first, Object second) {
        return new IllegalStateException(String.format(
                "expected at most one element (found %s and %s)",
                first, second));
    }

    private static <E> Source<Integer> matchLengths(
            Source<E> source, Object[] slice, int[] jumps,
            int from) {
        return new Stage<E, Integer>(source, ORDERED) {
            private int j = from;
            private boolean started;
            public boolean push(E e) {
                while (j == slice.length
                        || j >= 0 && !Objects.equals(e, slice[j])) {
                    j = jumps[j];
                }
                return down.push(++j);
            }
            public boolean forEachWhile(Sink<? super Integer> sink) {
                requireNonNull(sink);
                if (!started) {
                    started = true;
                    if (!sink.push(j)) return false;
                }
                return super.forEachWhile(sink);
            }
        };
    }

    private static Source<Integer> toMatchIndexes(
            Source<Integer> lengths, int sliceLength) {
        return new Stage<Integer, Integer>(lengths, ORDERED) {
            private int index;
            public boolean push(Integer length) {
                int i = index++;
                return length != sliceLength || down.push(i - sliceLength);
            }
        };
    }

    private static void copyInto(Source<Integer> source,
            int[] slice) {
        int[] i = new int[1];
        source.forEachRemaining(e -> slice[i[0]++] = e);
    }

    private static <E> Source<E> multisetOperation(
            Source<E> first,
            Spliterator<?> second,
            boolean difference) {
        // second is drained on the first traversal, not on construction
        Map<Object, Long> multiset = new HashMap<>();
        return defer(() -> {
            second.forEachRemaining(e -> multisetAdd(multiset, e));
            return multisetFilter(first, multiset, difference);
        }, ordered(first));
    }

    private static <E> Source<E> multisetFilter(
            Source<? extends E> first,
            Map<Object, Long> multiset,
            boolean difference) {
        return new Stage<E, E>(first) {
            public boolean push(E e) {
                return multisetRemove(multiset, e) == difference
                        || down.push(e);
            }
        };
    }

    private static void multisetAdd(
            Map<Object, Long> set, Object element) {
        Long count = set.get(element);
        if (count == null) {
            count = 0L;
        }
        set.put(element, count + 1L);
    }

    private static boolean multisetRemove(
            Map<Object, Long> set, Object element) {
        Long count = set.get(element);
        if (count != null) {
            count--;
            if (count == 0L) set.remove(element);
            else set.put(element, count);
            return true;
        }
        return false;
    }

    static class Box<E> implements Consumer<E> {
        E value;

        public void accept(E e) {
            value = e;
        }

        static <E> Optional<E> asOptional(Box<E> box) {
            return box == null ? Optional.empty() : Optional.of(box.value);
        }

        static <E> E orThrow(
                Box<E> box, Supplier<? extends RuntimeException> exception) {
            if (box == null) throw exception.get();
            return box.value;
        }
    }
}
