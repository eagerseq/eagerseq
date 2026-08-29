package org.bitbucket.seqly;

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
import java.util.Spliterators.AbstractSpliterator;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
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

    static <E> SeqStream<E> toSeqStream(Iterable<E> iterable) {
        return new SpliteratorSeqStream<>(iterable.spliterator());
    }

    static <E> SeqStream<E> toSeqStream(Object[] array) {
        return new SpliteratorSeqStream<>(toSpliterator(array));
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

    private static int ordered(Spliterator<?> spliterator) {
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
        spliterator.forEachRemaining(builder::add);
        return builder.trim();
    }

    @SuppressWarnings("unchecked")
    public static <E, A> A[] toArray(
            Spliterator<E> spliterator, IntFunction<A[]> generator) {
        SeqBuilder<A> builder = new SeqBuilder<>(generator);
        spliterator.forEachRemaining(((SeqBuilder<E>) builder)::add);
        return builder.trim();
    }

    static <E, T> T[] toArray(
            Spliterator<E> spliterator, T[] ts) {
        Class<?> type = ts.getClass().getComponentType();
        @SuppressWarnings("unchecked")
        IntFunction<T[]> generator =
                length -> (T[]) Array.newInstance(type, length);
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
        Object[] next = new Object[2];
        while (true) {
            boolean has0 = spl0.tryAdvance(e -> next[0] = e);
            boolean has1 = spl1.tryAdvance(e -> next[1] = e);
            if (!has0 & !has1) return true;
            if (!has0 | !has1) return false;
            if (!Objects.equals(next[0], next[1])) return false;
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
            throw new IllegalStateException(String.format(
                    "expected at most one element (found %s and %s)",
                    first, next.value));
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
            Spliterator<Spliterator<E>> spliterators) {
        return new UnknownSizeSpliterator<E>(ordered(spliterators)) {
            private Spliterator<E> current = emptySpliterator(ORDERED);
            boolean advance(Consumer<? super E> action) {
                while (true) {
                    if (current.tryAdvance(action)) return true;
                    if (spliterators.tryAdvance(s -> current = s)) continue;
                    return false;
                }
            }
        };
    }

    static <E> E get(
            Spliterator<E> spliterator, int index) {
        // checked before traversing, since a negative index has no length to
        // report and the source may not be finite
        requireNonNegativeIndex("index", index);
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
            private int index;
            private E next;
            boolean advance(IntConsumer action) {
                while (spliterator.tryAdvance(e -> next = e)) {
                    if (Objects.equals(object, next)) {
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
            private E e0;
            private F e1;
            boolean advance(Consumer<? super R> action) {
                boolean has0 = spl0.tryAdvance(e -> this.e0 = e);
                boolean has1 = spl1.tryAdvance(e -> this.e1 = e);
                if (!has0 | !has1) return false;
                action.accept(mapper.apply(e0, e1));
                return true;
            }
        };
    }

    static Spliterator.OfInt range(int from, int to) {
        return new UnknownSizeIntSpliterator(ORDERED) {
            private int index = from;
            boolean advance(IntConsumer action) {
                if (index < to) {
                    action.accept(index++);
                    return true;
                }
                return false;
            }
        };
    }

    static Spliterator.OfInt indexes(Spliterator<?> spliterator) {
        // throwing is acceptable in this rare case of overflow
        return range(0, Math.toIntExact(count(spliterator)));
    }

    static <E> Spliterator<E> limitLast(
            Spliterator<E> spliterator, long size) {
        requireNonNegativeArgument("size", size);
        if (size == 0) return emptySpliterator(ordered(spliterator));
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private E next;
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
                            && spliterator.tryAdvance(builder::add)) {
                        used++;
                    }
                    queue = builder.trim();
                    // used reaches size only if the fill loop filled the
                    // queue rather than exhausting the source
                    if (used == size) {
                        while (spliterator.tryAdvance(e -> next = e)) {
                            queue[index] = next;
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
        requireNonNegativeArgument("size", size);
        if (size == 0) return spliterator;
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private E next;
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
                            && spliterator.tryAdvance(builder::add)) {
                        used++;
                    }
                    queue = builder.trim();
                }
                if (used < size) return false;
                if (!spliterator.tryAdvance(e -> next = e)) return false;
                E e = queue[index];
                queue[index] = next;
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
            private E next;
            private boolean found;
            boolean advance(Consumer<? super E> action) {
                if (!found) {
                    if (spliterator.tryAdvance(e -> next = e)) {
                        if (!predicate.test(next)) {
                            found = true;
                            return false;
                        }
                        action.accept(next);
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
            private E next;
            private boolean found;
            boolean advance(Consumer<? super E> action) {
                if (!found) {
                    while (spliterator.tryAdvance(e -> next = e)) {
                        if (!predicate.test(next)) {
                            found = true;
                            action.accept(next);
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
        return new UnknownSizeSpliterator<E[]>(ORDERED) {
            private int[] index = IntStream.range(0, array.length).toArray();
            boolean advance(Consumer<? super E[]> action) {
                if (index == null) return false;
                @SuppressWarnings("unchecked")
                E[] r = (E[]) Arrays.stream(index)
                        .mapToObj(i -> array[i]).toArray();
                int a = index.length - 2;
                while (a >= 0 && index[a + 1] < index[a]) a--;
                if (a < 0) {
                    index = null;
                } else {
                    int b = a + 1;
                    while (b < index.length && index[a] < index[b]) b++;
                    swap(a++, b - 1);
                    b = index.length - 1;
                    while (a < b) swap(a++, b--);
                }
                action.accept(r);
                return true;
            }
            private void swap(int a, int b) {
                int t = index[a];
                index[a] = index[b];
                index[b] = t;
            }
        };
    }

    static <E> Spliterator<E[]> combinations(Object[] array, int size) {
        requireNonNegativeArgument("size", size);
        if (size > array.length) {
            throw new IllegalArgumentException(
                    "size " + size + " was greater than length "
                            + array.length);
        }
        return new UnknownSizeSpliterator<E[]>(ORDERED) {
            private int[] index = IntStream.range(0, size).toArray();
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

    static <E> Spliterator<E[]> powerSet(Object[] array) {
        return flatten(map(
                range(0, array.length + 1),
                size -> combinations(array, size)));
    }

    static <E> Spliterator<E> filter(
            Spliterator<E> spliterator,
            Predicate<? super E> predicate) {
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private E next;
            boolean advance(Consumer<? super E> action) {
                while (spliterator.tryAdvance(e -> next = e)) {
                    if (predicate.test(next)) {
                        action.accept(next);
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
            boolean advance(Consumer<? super R> action) {
                return spliterator.tryAdvance(e ->
                        action.accept(mapper.apply(e)));
            }
        };
    }

    static <E, R> Spliterator<R> flatMap(
            Spliterator<E> spliterator,
            Function<? super E, ? extends Spliterator<R>> mapper) {
        return flatten(map(spliterator, mapper.andThen(
                s -> s == null ? emptySpliterator(ORDERED) : s)));
    }

    static <E, R> Spliterator<R> mapMulti(
            Spliterator<E> spliterator,
            BiConsumer<? super E, ? super Consumer<R>> mapper) {
        return new UnknownSizeSpliterator<R>(ordered(spliterator)) {
            private ArrayList<R> buffer = new ArrayList<>();
            private Consumer<R> sink = buffer::add;
            private int index;
            boolean advance(Consumer<? super R> action) {
                while (index == buffer.size()) {
                    buffer.clear();
                    index = 0;
                    if (!spliterator.tryAdvance(e -> mapper.accept(e, sink))) {
                        return false;
                    }
                }
                action.accept(buffer.get(index++));
                return true;
            }
        };
    }

    static <E> Spliterator<E> distinct(Spliterator<E> spliterator) {
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private E next;
            private Set<E> seen = new HashSet<>();
            boolean advance(Consumer<? super E> action) {
                while (spliterator.tryAdvance(e -> next = e)) {
                    if (seen.add(next)) {
                        action.accept(next);
                        return true;
                    }
                }
                return false;
            }
        };
    }

    static <E> E[] sorted(
            Spliterator<E> spliterator,
            Comparator<? super E> comparator) {
        @SuppressWarnings("unchecked")
        E[] array = (E[]) toArray(spliterator);
        Arrays.sort(array, comparator);
        return array;
    }

    static <E> Spliterator<E> limit(
            Spliterator<E> spliterator, long size) {
        requireNonNegativeArgument("size", size);
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private E next;
            private long index;
            boolean advance(Consumer<? super E> action) {
                if (index >= size) return false;
                if (spliterator.tryAdvance(e -> next = e)) {
                    index++;
                    action.accept(next);
                    return true;
                }
                return false;
            }
        };
    }

    static <E> Spliterator<E> skip(
            Spliterator<E> spliterator, long size) {
        requireNonNegativeArgument("size", size);
        return new UnknownSizeSpliterator<E>(ordered(spliterator)) {
            private E next;
            private long index;
            boolean advance(Consumer<? super E> action) {
                while (spliterator.tryAdvance(e -> next = e)) {
                    if (index++ >= size) {
                        action.accept(next);
                        return true;
                    }
                }
                return false;
            }
        };
    }

    static <E> Spliterator<E> slice(
            Spliterator<E> spliterator, int from, int to) {
        // as indexes, and here rather than in limit and skip so that from is
        // reported before to; an index beyond the end still clamps
        requireNonNegativeIndex("from", from);
        requireNonNegativeIndex("to", to);
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

    static <E> Optional<E> max(
            Spliterator<E> spliterator,
            Comparator<? super E> comparator) {
        return min(spliterator, reverseOrder(comparator));
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
            boolean advance(Consumer<? super E> action) {
                return spliterator.tryAdvance(e -> {
                    peeker.accept(e);
                    action.accept(e);
                });
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

    private static Spliterator.OfInt matchLengths(
            Spliterator<?> spliterator, Object[] slice, int[] jumps, int from) {
        return new UnknownSizeIntSpliterator(ORDERED) {
            private int j = from;
            private Object next;
            private boolean started;
            boolean advance(IntConsumer action) {
                if (!started) {
                    started = true;
                    action.accept(j);
                    return true;
                }
                if (!spliterator.tryAdvance(e -> next = e)) return false;
                while (j == slice.length
                        || j >= 0 && !Objects.equals(next, slice[j])) {
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
            boolean advance(IntConsumer action) {
                while (lengths.tryAdvance((IntConsumer) e -> length = e)) {
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
            private E next;
            private boolean concatenated;
            boolean advance(Consumer<? super E> action) {
                if (!concatenated) {
                    while (second.tryAdvance(e -> next = (E) e)) {
                        multisetAdd(multiset, next);
                        if (union) {
                            action.accept(next);
                            return true;
                        }
                    }
                    concatenated = true;
                }
                while (first.tryAdvance(e -> next = e)) {
                    if (multisetRemove(multiset, next) ^ difference) {
                        action.accept(next);
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
            extends AbstractSpliterator<E> {

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
            extends AbstractIntSpliterator {

        @SuppressWarnings("MagicConstant")
        UnknownSizeIntSpliterator(int characteristics) {
            super(Long.MAX_VALUE, characteristics & ~(SIZED | SUBSIZED));
        }

        public final boolean tryAdvance(IntConsumer action) {
            return advance(requireNonNull(action));
        }

        abstract boolean advance(IntConsumer action);
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
