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
import java.util.Spliterators.AbstractIntSpliterator;
import java.util.Spliterators.AbstractLongSpliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
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
import static java.util.function.Function.identity;

final class Split {

    private Split() {
    }

    static <E> Stream<E> toStream(SeqStream<E> stream) {
        return StreamSupport.stream(stream.spliterator(), false);
    }

    // maybe inline the following conversions

    static <E> Spliterator<E> toSpliterator(Iterator<E> iterator) {
        return Spliterators.spliteratorUnknownSize(iterator, 0);
    }

    static <E> Spliterator<E> toSpliterator(Object[] array) {
        return Spliterators.spliterator(array, ORDERED);
    }

    static <E> Spliterator<E> defer(
            Supplier<Spliterator<E>> supplier,
            int characteristics) {
        requireNonNull(supplier);
        return new UnknownSizeSpliterator<E>(characteristics) {
            private Supplier<Spliterator<E>> pending = supplier;
            private Spliterator<E> delegate;

            boolean advance(Consumer<? super E> action) {
                if (pending != null) {
                    Supplier<Spliterator<E>> supplier = pending;
                    pending = null;
                    delegate = requireNonNull(supplier.get());
                }
                if (delegate == null) {
                    throw new IllegalStateException(
                            "deferred computation previously failed");
                }
                return delegate.tryAdvance(action);
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

    private static <E> Spliterator<E> emptySpliterator(int characteristics) {
        return Spliterators.spliterator(SeqBuilder.EMPTY, characteristics);
    }

    static Object[] toArray(Iterable<?> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toArray();
    }

    static Object[] toArray(Iterator<?> iterator) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(iterator, 0), false).toArray();
    }

    static Object[] toArray(Spliterator<?> spliterator) {
        SeqBuilder<Object> builder = new SeqBuilder<>();
        spliterator.forEachRemaining(builder);
        return builder.trim();
    }

    @SuppressWarnings("unchecked")
    public static <E, A> A[] toArray(
            Spliterator<E> spliterator, IntFunction<A[]> generator) {
        SeqBuilder<A> builder = new SeqBuilder<>(generator);
        spliterator.forEachRemaining((SeqBuilder<E>) builder);
        return builder.trim();
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
        List<E> list = new ArrayList<>();
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
            BinaryOperator<V> mergeFunction) {
        // null is an internal duplicate-key sentinel for private use only
        Map<K, V> map = new LinkedHashMap<>();
        spliterator.forEachRemaining(e -> {
            K key = keyMapper.apply(e);
            V value = valueMapper.apply(e);
            if (map.containsKey(key)) {
                V present = map.get(key);
                if (mergeFunction == null) {
                    throw new IllegalStateException(String.format(
                            "duplicate key %s "
                                    + "(attempted merging values %s and %s)",
                            key, present, value));
                }
                value = mergeFunction.apply(present, value);
            }
            map.put(key, value);
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
        long[] count = new long[1];
        spliterator.forEachRemaining(e -> count[0]++);
        return count[0];
    }

    static int size(Spliterator<?> spliterator) {
        // clamps like Collection.size() documents, rather than throws
        return (int) Math.min(count(spliterator), Integer.MAX_VALUE);
    }

    static boolean isEmpty(Spliterator<?> spliterator) {
        return !spliterator.tryAdvance(e -> {});
    }

    static <E> int listHash(Spliterator<E> spliterator) {
        int[] hash = new int[]{1};
        spliterator.forEachRemaining(e -> {
            hash[0] *= 31;
            hash[0] += Objects.hashCode(e);
        });
        return hash[0];
    }

    static boolean listEquals(Spliterator<?> spl0, Spliterator<?> spl1) {
        Box<Object> next0 = new Box<>();
        Box<Object> next1 = new Box<>();
        while (true) {
            boolean has0 = spl0.tryAdvance(next0);
            boolean has1 = spl1.tryAdvance(next1);
            if (!has0 & !has1) return true;
            if (!has0 | !has1) return false;
            if (!Objects.equals(next0.value, next1.value)) return false;
        }
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

    static <E> Optional<E> findFirst(Spliterator<E> spliterator) {
        return Box.asOptional(firstBox(spliterator));
    }

    static <E> Optional<E> findLast(Spliterator<E> spliterator) {
        return Box.asOptional(lastBox(spliterator));
    }

    static <E> Optional<E> findSingle(Spliterator<E> spliterator) {
        return Box.asOptional(singleBox(spliterator));
    }

    static <E> E getFirst(Spliterator<E> spliterator) {
        return Box.orThrow(firstBox(spliterator), Split::emptySequence);
    }

    static <E> E getLast(Spliterator<E> spliterator) {
        return Box.orThrow(lastBox(spliterator), Split::emptySequence);
    }

    static <E> E getSingle(Spliterator<E> spliterator) {
        return Box.orThrow(singleBox(spliterator), Split::notExactlyOne);
    }

    static <E> Optional<E> toOptional(Spliterator<E> spliterator) {
        Box<E> next = new Box<>();
        if (!spliterator.tryAdvance(next)) return Optional.empty();
        E first = next.value;
        if (spliterator.tryAdvance(next)) {
            throw moreThanOne(first, next.value);
        }
        return Optional.of(first);
    }

    private static <E> Box<E> firstBox(Spliterator<E> spliterator) {
        Box<E> next = new Box<>();
        return spliterator.tryAdvance(next) ? next : null;
    }

    private static <E> Box<E> lastBox(Spliterator<E> spliterator) {
        Box<E> next = new Box<>();
        boolean set = false;
        while (true) {
            Spliterator<E> prefix = spliterator.trySplit();
            if (spliterator.tryAdvance(next)) {
                set = true;
            } else if (prefix != null) {
                spliterator = prefix;
            } else {
                return set ? next : null;
            }
        }
    }

    // Unlike toOptional, an empty source and one with more than one element
    // are both absent, so that findSingle and getSingle agree on both.
    private static <E> Box<E> singleBox(Spliterator<E> spliterator) {
        Box<E> next = new Box<>();
        return spliterator.tryAdvance(next)
                && !spliterator.tryAdvance(e -> {}) ? next : null;
    }

    static <E> Spliterator<E> flatten(
            Spliterator<? extends Spliterator<? extends E>> spliterators) {
        return chain(spliterators, ordered(spliterators));
    }

    @SafeVarargs
    static <E> Spliterator<E> concat(
            Spliterator<? extends E>... spliterators) {
        int c = ORDERED;
        for (Spliterator<?> s : spliterators) c &= ordered(s);
        return chain(toSpliterator(spliterators), c);
    }

    private static <E> Spliterator<E> chain(
            Spliterator<? extends Spliterator<? extends E>> spliterators,
            int characteristics) {
        return new UnknownSizeSpliterator<E>(characteristics) {
            private final Box<Spliterator<? extends E>> current = new Box<>();
            boolean advance(Consumer<? super E> action) {
                while (true) {
                    if (current.value != null
                            && current.value.tryAdvance(action))
                        return true;
                    if (!spliterators.tryAdvance(current)) return false;
                }
            }
        };
    }

    static <E> E get(
            Spliterator<E> spliterator, int index) {
        Box<E> next = new Box<>();
        long length = 0;
        while (spliterator.tryAdvance(next)) {
            if (length++ == index) return next.value;
        }
        throw indexOutOfBounds("index", index, length);
    }

    static <E> Spliterator.OfInt indexesOf(
            Spliterator<E> spliterator, Object object) {
        return new UnknownSizeIntSpliterator(ORDERED) {
            private final Box<E> next = new Box<>();
            private int index;
            boolean advance(IntConsumer action) {
                while (spliterator.tryAdvance(next)) {
                    if (Objects.equals(object, next.value)) {
                        action.accept(index++);
                        return true;
                    }
                    index++;
                }
                return false;
            }
        };
    }

    static <E> int indexOf(
            Spliterator<E> spliterator, Object object) {
        return findFirst(indexesOf(spliterator, object)).orElse(-1);
    }

    static <E> int lastIndexOf(
            Spliterator<E> spliterator, Object object) {
        return findLast(indexesOf(spliterator, object)).orElse(-1);
    }

    static <E> boolean contains(
            Spliterator<E> spliterator, Object object) {
        return indexesOf(spliterator, object).tryAdvance((int e) -> {});
    }

    static <E, F, R> Spliterator<R> zip(
            Spliterator<E> spl0,
            Spliterator<? extends F> spl1,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        return new UnknownSizeSpliterator<R>(ordered(spl0, spl1)) {
            private final Box<E> box0 = new Box<>();
            private final Box<F> box1 = new Box<>();
            boolean advance(Consumer<? super R> action) {
                boolean has0 = spl0.tryAdvance(box0);
                boolean has1 = spl1.tryAdvance(box1);
                if (!has0 | !has1) return false;
                action.accept(mapper.apply(box0.value, box1.value));
                return true;
            }
        };
    }

    static Spliterator.OfInt range(int from, int to) {
        return range(from, to, 0);
    }

    static Spliterator.OfLong range(long from, long to) {
        return range(from, to, 0);
    }

    static Spliterator.OfInt rangeClosed(int from, int to) {
        return range(from, to, from <= to ? 1 : 0);
    }

    static Spliterator.OfLong rangeClosed(long from, long to) {
        return range(from, to, from <= to ? 1 : 0);
    }

    private static Spliterator.OfInt range(
            int from, int to, int pendingLast) {
        return new UnknownSizeIntSpliterator(ORDERED) {
            private int index = from;
            private int last = pendingLast;
            boolean advance(IntConsumer action) {
                if (index < to) {
                    action.accept(index++);
                    return true;
                }
                if (last > 0) {
                    last = 0;
                    action.accept(index);
                    return true;
                }
                return false;
            }
        };
    }

    private static Spliterator.OfLong range(
            long from, long to, int pendingLast) {
        return new UnknownSizeLongSpliterator(ORDERED) {
            private long index = from;
            private int last = pendingLast;
            boolean advance(LongConsumer action) {
                if (index < to) {
                    action.accept(index++);
                    return true;
                }
                if (last > 0) {
                    last = 0;
                    action.accept(index);
                    return true;
                }
                return false;
            }
        };
    }

    static <E> Spliterator<E> iterate(
            E seed, UnaryOperator<E> operator) {
        return new UnknownSizeSpliterator<E>(ORDERED) {
            private E next = seed;
            private boolean started;
            boolean advance(Consumer<? super E> action) {
                if (started) next = operator.apply(next);
                else started = true;
                action.accept(next);
                return true;
            }
        };
    }

    static Spliterator.OfInt indexes(Spliterator<?> spliterator) {
        return new UnknownSizeIntSpliterator(ORDERED) {
            private long index;
            boolean advance(IntConsumer action) {
                if (!spliterator.tryAdvance(e -> {})) return false;
                // throwing is acceptable in this rare case of overflow
                action.accept(Math.toIntExact(index++));
                return true;
            }
        };
    }

    static <E> Spliterator<E> limitLast(
            Spliterator<E> spliterator, long size) {
        if (size == 0) return emptySpliterator(ordered(spliterator));
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private final Box<E> next = new Box<>();
            private int used;
            private int index;
            private boolean skipped;
            private E[] queue;
            boolean advance(Consumer<? super E> action) {
                if (!skipped) {
                    skipped = true;
                    // fill by appending, so that the queue is sized from the
                    // data and not from size, and only then overwrite in
                    // place, by which point its length is settled
                    SeqBuilder<E> builder = new SeqBuilder<>();
                    while (used < size
                            && spliterator.tryAdvance(builder)) {
                        used++;
                    }
                    queue = builder.trim();
                    // used reaches size only if the fill loop filled the
                    // queue rather than exhausting the source
                    if (used == size) {
                        while (spliterator.tryAdvance(next)) {
                            queue[index] = next.value;
                            index++;
                            index %= queue.length;
                        }
                    }
                }
                if (used == 0) return false;
                used--;
                E e = queue[index];
                index++;
                index %= queue.length;
                action.accept(e);
                return true;
            }
        };
    }

    static <E> Spliterator<E> skipLast(
            Spliterator<E> spliterator, long size) {
        if (size == 0) return spliterator;
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private final Box<E> next = new Box<>();
            private int used;
            private int index;
            private boolean skipped;
            private E[] queue;
            boolean advance(Consumer<? super E> action) {
                if (!skipped) {
                    skipped = true;
                    // as limitLast, the queue is sized from the data
                    SeqBuilder<E> builder = new SeqBuilder<>();
                    while (used < size
                            && spliterator.tryAdvance(builder)) {
                        used++;
                    }
                    queue = builder.trim();
                }
                if (used < size) return false;
                if (!spliterator.tryAdvance(next)) return false;
                E e = queue[index];
                queue[index] = next.value;
                index++;
                index %= queue.length;
                action.accept(e);
                return true;
            }
        };
    }

    static <E> Spliterator<E> takeWhile(
            Spliterator<E> spliterator,
            Predicate<? super E> predicate) {
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private final Box<E> next = new Box<>();
            private boolean found;
            boolean advance(Consumer<? super E> action) {
                if (!found) {
                    if (spliterator.tryAdvance(next)) {
                        if (!predicate.test(next.value)) {
                            found = true;
                            return false;
                        }
                        action.accept(next.value);
                        return true;
                    }
                }
                return false;
            }
        };
    }

    static <E> Spliterator<E> dropWhile(
            Spliterator<E> spliterator,
            Predicate<? super E> predicate) {
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private final Box<E> next = new Box<>();
            private boolean found;
            boolean advance(Consumer<? super E> action) {
                if (!found) {
                    while (spliterator.tryAdvance(next)) {
                        if (!predicate.test(next.value)) {
                            found = true;
                            action.accept(next.value);
                            return true;
                        }
                    }
                    return false;
                }
                return spliterator.tryAdvance(action);
            }
        };
    }

    static <E> Spliterator<E> intersection(
            Spliterator<E> first,
            Spliterator<?> second) {
        return multisetOperation(
                first, second, false, false, ordered(first));
    }

    static <E> Spliterator<E> difference(
            Spliterator<E> first,
            Spliterator<?> second) {
        return multisetOperation(
                first, second, true, false, ordered(first));
    }

    static <E> Spliterator<E> union(
            Spliterator<E> first,
            Spliterator<? extends E> second) {
        return multisetOperation(
                second, first, true, true, ordered(first, second));
    }

    static boolean containsMultiset(
            Spliterator<?> first,
            Spliterator<?> second) {
        return !multisetOperation(second, first, true, false, 0)
                .tryAdvance(e -> {});
    }

    static boolean containsAll(
            Spliterator<?> first,
            Spliterator<?> second) {
        return containsMultiset(first, distinct(second));
    }

    static Spliterator.OfInt indexesOfSlice(
            Spliterator<?> spliterator, Spliterator<?> slice) {
        Object[] array = toArray(slice);
        int[] jumps = new int[array.length + 1];
        copyInto(matchLengths(toSpliterator(array), array, jumps, -1), jumps);
        return toMatchIndexes(matchLengths(spliterator, array, jumps, 0),
                array.length);
    }

    static int indexOfSlice(
            Spliterator<?> spliterator, Spliterator<?> slice) {
        return findFirst(indexesOfSlice(spliterator, slice)).orElse(-1);
    }

    static int lastIndexOfSlice(
            Spliterator<?> spliterator, Spliterator<?> slice) {
        return findLast(indexesOfSlice(spliterator, slice)).orElse(-1);
    }

    static boolean containsSlice(
            Spliterator<?> spliterator, Spliterator<?> slice) {
        return indexesOfSlice(spliterator, slice).tryAdvance((int e) -> {});
    }

    static boolean startsWith(
            Spliterator<?> spliterator, Spliterator<?> slice) {
        // return zip(that, Objects::equals).allMatch(e -> e);
        Object[] array = toArray(slice);
        return listEquals(
                limit(spliterator, array.length), toSpliterator(array));
    }

    static boolean endsWith(
            Spliterator<?> spliterator, Spliterator<?> slice) {
        Object[] array = toArray(slice);
        return listEquals(
                limitLast(spliterator, array.length), toSpliterator(array));
    }

    static <E> Spliterator<E[]> permutations(Object[] array) {
        return permutations(array, array.length);
    }

    static <E> Spliterator<E[]> permutations(Object[] array, int k) {
        if (k > array.length) {
            return emptySpliterator(ORDERED);
        }
        return new UnknownSizeSpliterator<E[]>(ORDERED) {
            private int[] index = IntStream.range(0, k).toArray();
            private final boolean[] used = new boolean[array.length];
            {
                Arrays.fill(used, 0, k, true);
            }
            boolean advance(Consumer<? super E[]> action) {
                if (index == null) return false;
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
                action.accept(r);
                return true;
            }
        };
    }

    static <E> Spliterator<E[]> allPermutations(Object[] array) {
        return flatMap(
                range(0, array.length + 1),
                k -> permutations(array, k));
    }

    static <E> Spliterator<E[]> combinations(Object[] array, int k) {
        if (k > array.length) {
            return emptySpliterator(ORDERED);
        }
        return new UnknownSizeSpliterator<E[]>(ORDERED) {
            private int[] index = IntStream.range(0, k).toArray();
            boolean advance(Consumer<? super E[]> action) {
                if (index == null) return false;
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
                action.accept(r);
                return true;
            }
        };
    }

    static <E> Spliterator<E[]> allCombinations(Object[] array) {
        return flatMap(
                range(0, array.length + 1),
                k -> combinations(array, k));
    }

    static <E> Spliterator<E[]> power(Object[] array, int k) {
        return new UnknownSizeSpliterator<E[]>(ORDERED) {
            private int[] index = array.length == 0 && k > 0
                    ? null
                    : new int[k];
            boolean advance(Consumer<? super E[]> action) {
                if (index == null) return false;
                @SuppressWarnings("unchecked")
                E[] r = (E[]) Arrays.stream(index)
                        .mapToObj(i -> array[i]).toArray();
                int a = index.length - 1;
                while (a >= 0 && ++index[a] == array.length) {
                    index[a--] = 0;
                }
                if (a < 0) index = null;
                action.accept(r);
                return true;
            }
        };
    }

    static <E, F, R> Spliterator<R> product(
            Spliterator<E> first,
            Object[] second,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        if (second.length == 0) return emptySpliterator(ordered(first));
        return flatMap(first, e -> map(
                Split.<F>toSpliterator(second), f -> mapper.apply(e, f)));
    }

    static <E> Spliterator<E> filter(
            Spliterator<E> spliterator,
            Predicate<? super E> predicate) {
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private final Box<E> next = new Box<>();
            boolean advance(Consumer<? super E> action) {
                while (spliterator.tryAdvance(next)) {
                    if (predicate.test(next.value)) {
                        action.accept(next.value);
                        return true;
                    }
                }
                return false;
            }
        };
    }

    static <E, R> Spliterator<R> map(
            Spliterator<E> spliterator,
            Function<? super E, ? extends R> mapper) {
        return new UnknownSizeSpliterator<R>(ordered(spliterator)) {
            private final Box<E> next = new Box<>();
            boolean advance(Consumer<? super R> action) {
                if (!spliterator.tryAdvance(next)) return false;
                action.accept(mapper.apply(next.value));
                return true;
            }
        };
    }

    static <E, R> Spliterator<R> flatMap(
            Spliterator<E> spliterator,
            Function<? super E, ? extends Spliterator<R>> mapper) {
        return flatten(map(spliterator, mapper));
    }

    static <E, R> Spliterator<R> mapMulti(
            Spliterator<E> spliterator,
            BiConsumer<? super E, ? super Consumer<R>> mapper) {
        return new UnknownSizeSpliterator<R>(ordered(spliterator)) {
            private final Box<E> next = new Box<>();
            private ArrayList<R> buffer = new ArrayList<>();
            private Consumer<R> sink = buffer::add;
            private int index;
            boolean advance(Consumer<? super R> action) {
                while (index == buffer.size()) {
                    buffer.clear();
                    index = 0;
                    if (!spliterator.tryAdvance(next)) return false;
                    mapper.accept(next.value, sink);
                }
                action.accept(buffer.get(index++));
                return true;
            }
        };
    }

    static <E> Spliterator<E> distinct(Spliterator<E> spliterator) {
        return distinctBy(spliterator, null);
    }

    static <E> Spliterator<E> distinctBy(
            Spliterator<E> spliterator,
            Function<? super E, ?> keyMapper) {
        // null is an internal identity sentinel for private use only
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private final Box<E> next = new Box<>();
            private Set<Object> seen = new HashSet<>();
            boolean advance(Consumer<? super E> action) {
                while (spliterator.tryAdvance(next)) {
                    if (seen.add(keyMapper == null
                            ? next.value
                            : keyMapper.apply(next.value))) {
                        action.accept(next.value);
                        return true;
                    }
                }
                return false;
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <E, K, V> Map<K, V> groupBy(
            Spliterator<E> spliterator,
            Function<? super E, ? extends K> keyMapper,
            Function<? super E[], ? extends V> valueMapper) {
        Map<K, Object> map = new LinkedHashMap<>();
        spliterator.forEachRemaining(e -> ((SeqBuilder<E>) map
                .computeIfAbsent(keyMapper.apply(e), key -> new SeqBuilder<>()))
                .accept(e));
        map.replaceAll((key, builder) -> valueMapper.apply(
                ((SeqBuilder<E>) builder).trim()));
        return (Map<K, V>) Collections.unmodifiableMap(map);
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

    static <E> Spliterator<E> limit(
            Spliterator<E> spliterator, long size) {
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private final Box<E> next = new Box<>();
            private long index;
            boolean advance(Consumer<? super E> action) {
                if (index >= size) return false;
                if (spliterator.tryAdvance(next)) {
                    index++;
                    action.accept(next.value);
                    return true;
                }
                return false;
            }
        };
    }

    static <E> Spliterator<E> skip(
            Spliterator<E> spliterator, long size) {
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private final Box<E> next = new Box<>();
            private long index;
            boolean advance(Consumer<? super E> action) {
                while (spliterator.tryAdvance(next)) {
                    if (index++ >= size) {
                        action.accept(next.value);
                        return true;
                    }
                }
                return false;
            }
        };
    }

    static <E> Spliterator<E> slice(
            Spliterator<E> spliterator, int from, int to) {
        return skip(limit(spliterator, to), from);
    }

    static <E, R> R reduce(
            Spliterator<E> spliterator,
            R identity,
            BiFunction<R, ? super E, R> accumulator) {
        Box<E> next = new Box<>();
        while (spliterator.tryAdvance(next)) {
            identity = accumulator.apply(identity, next.value);
        }
        return identity;
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
        E min;
        Box<E> next = new Box<>();
        if (spliterator.tryAdvance(next)) min = next.value;
        else return Optional.empty();
        while (spliterator.tryAdvance(next)) {
            if (comparator.compare(next.value, min) < 0) min = next.value;
        }
        return Optional.of(min);
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
            Spliterator<E> spliterator,
            Predicate<? super E> predicate) {
        Box<E> next = new Box<>();
        while (spliterator.tryAdvance(next)) {
            if (predicate.test(next.value)) return false;
        }
        return true;
    }

    static <E> boolean anyMatch(
            Spliterator<E> spliterator,
            Predicate<? super E> predicate) {
        return !noneMatch(spliterator, predicate);
    }

    static <E> boolean allMatch(
            Spliterator<E> spliterator,
            Predicate<? super E> predicate) {
        return noneMatch(spliterator, predicate.negate());
    }

    static <E> Spliterator<E> peek(
            Spliterator<E> spliterator,
            Consumer<? super E> peeker) {
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private final Box<E> next = new Box<>();
            boolean advance(Consumer<? super E> action) {
                if (!spliterator.tryAdvance(next)) return false;
                peeker.accept(next.value);
                action.accept(next.value);
                return true;
            }
        };
    }

    static <E> Spliterator<E> unordered(Spliterator<E> spliterator) {
        return new UnknownSizeSpliterator<E>(0) {
            boolean advance(Consumer<? super E> action) {
                return spliterator.tryAdvance(action);
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

    private static Spliterator.OfInt matchLengths(
            Spliterator<?> spliterator, Object[] slice, int[] jumps, int from) {
        return new UnknownSizeIntSpliterator(ORDERED) {
            private final Box<Object> next = new Box<>();
            private int j = from;
            private boolean started;
            boolean advance(IntConsumer action) {
                if (!started) {
                    started = true;
                    action.accept(j);
                    return true;
                }
                if (!spliterator.tryAdvance(next)) return false;
                while (j == slice.length
                        || j >= 0 && !Objects.equals(next.value, slice[j])) {
                    j = jumps[j];
                }
                action.accept(++j);
                return true;
            }
        };
    }

    private static Spliterator.OfInt toMatchIndexes(
            Spliterator.OfInt lengths, int sliceLength) {
        return new UnknownSizeIntSpliterator(ORDERED) {
            private int index;
            private int length;
            private final IntConsumer setLength = e -> length = e;
            boolean advance(IntConsumer action) {
                while (lengths.tryAdvance(setLength)) {
                    if (length == sliceLength) {
                        action.accept(index++ - sliceLength);
                        return true;
                    }
                    index++;
                }
                return false;
            }
        };
    }

    private static void copyInto(Spliterator.OfInt spliterator, int[] slice) {
        int[] i = new int[1];
        spliterator.forEachRemaining((int e) -> slice[i[0]++] = e);
    }

    @SuppressWarnings("unchecked")
    private static <E> Spliterator<E> multisetOperation(
            Spliterator<? extends E> first,
            Spliterator<?> second,
            boolean difference, boolean union, int characteristics) {
        Map<Object, Long> multiset = new HashMap<>();
        return new UnknownSizeSpliterator<E>(characteristics) {
            private final Box<Object> next = new Box<>();
            private boolean concatenated;
            boolean advance(Consumer<? super E> action) {
                if (!concatenated) {
                    while (second.tryAdvance(next)) {
                        multisetAdd(multiset, next.value);
                        if (union) {
                            // if union, second contains E, not Object
                            action.accept((E) next.value);
                            return true;
                        }
                    }
                    concatenated = true;
                }
                while (first.tryAdvance(next)) {
                    if (multisetRemove(multiset, next.value) ^ difference) {
                        // first contains E, not Object
                        action.accept((E) next.value);
                        return true;
                    }
                }
                return false;
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

    private abstract static class UnknownSizeSpliterator<E>
            extends
                AbstractSpliterator<E> {

        @SuppressWarnings("MagicConstant")
        UnknownSizeSpliterator(int characteristics) {
            super(Long.MAX_VALUE, characteristics & ~(SIZED | SUBSIZED));
        }

        public final boolean tryAdvance(Consumer<? super E> action) {
            return advance(requireNonNull(action));
        }

        abstract boolean advance(Consumer<? super E> action);
    }

    private abstract static class UnknownSizeIntSpliterator
            extends
                AbstractIntSpliterator {

        @SuppressWarnings("MagicConstant")
        UnknownSizeIntSpliterator(int characteristics) {
            super(Long.MAX_VALUE, characteristics & ~(SIZED | SUBSIZED));
        }

        public final boolean tryAdvance(IntConsumer action) {
            return advance(requireNonNull(action));
        }

        abstract boolean advance(IntConsumer action);
    }

    private abstract static class UnknownSizeLongSpliterator
            extends
                AbstractLongSpliterator {

        @SuppressWarnings("MagicConstant")
        UnknownSizeLongSpliterator(int characteristics) {
            super(Long.MAX_VALUE, characteristics & ~(SIZED | SUBSIZED));
        }

        public final boolean tryAdvance(LongConsumer action) {
            return advance(requireNonNull(action));
        }

        abstract boolean advance(LongConsumer action);
    }

    private static class Box<E> implements Consumer<E> {
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
