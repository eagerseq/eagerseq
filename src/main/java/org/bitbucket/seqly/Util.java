package org.bitbucket.seqly;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Spliterators.AbstractIntSpliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterators.emptySpliterator;

class Util {

    private Util() {
    }

    static <E> SeqStream<E> toSeqStream(Iterable<E> iterable) {
        return new SpliteratorSeqStream<>(iterable.spliterator());
    }

    static <E> SeqStream<E> toSeqStream(Object[] array) {
        return new SpliteratorSeqStream<>(Spliterators.spliterator(array, 0));
    }

    static <E> Stream<E> toStream(SeqStream<E> stream) {
        return StreamSupport.stream(stream.spliterator(), false);
    }

    static <E> Spliterator<E> toSpliterator(Iterator<E> iterator) {
        return Spliterators.spliteratorUnknownSize(iterator, 0);
    }

    @SuppressWarnings("unchecked")
    static <E> Spliterator<E> toSpliterator(Object[] array) {
        return Spliterators.spliterator(array, 0);
    }

    static Object[] toArray(Iterable<?> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toArray();
    }

    static Object[] toArray(Iterator<?> iterator) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(iterator, 0), false).toArray();
    }

    static Object[] toArray(Spliterator<?> spliterator) {
        return StreamSupport.stream(spliterator, false).toArray();
    }

    static <T> T[] toArray(
            Function<IntFunction<T[]>, T[]> maker, T[] ts) {
        Class<?> type = ts.getClass().getComponentType();
        @SuppressWarnings("unchecked")
        IntFunction<T[]> generator = length -> (T[]) Array.newInstance(type, length);
        T[] array = maker.apply(generator);
        if (ts.length < array.length) return array;
        System.arraycopy(array, 0, ts, 0, array.length);
        if (array.length < ts.length) ts[array.length] = null;
        return ts;
    }

    static int toInt(long value) {
        int intValue = (int) value;
        if (intValue != value) {
            throw new RuntimeException("index overflowed int");
        }
        return intValue;
    }

    static void reverse(Object[] array) {
        Collections.reverse(Arrays.asList(array));
    }

    static void rotate(Object[] array, int size) {
        Collections.rotate(Arrays.asList(array), size);
    }

    static void shuffle(Object[] array, Random random) {
        Collections.shuffle(Arrays.asList(array), random);
    }

    static long count(Spliterator<?> spliterator) {
        return (spliterator.characteristics() & Spliterator.SIZED) == 0
                ? StreamSupport.stream(spliterator, false).count()
                : spliterator.estimateSize();
    }

    static <E> int listHash(Spliterator<E> spliterator) {
        int[] hash = new int[]{1};
        while (spliterator.tryAdvance(e -> {
            hash[0] *= 31;
            hash[0] += Objects.hashCode(e);
        })) {
        }
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

    static <E> Optional<E> findOnly(
            Spliterator<? extends E> spliterator) {
        @SuppressWarnings("unchecked")
        E[] next = (E[]) new Object[1];
        if (spliterator.tryAdvance(e -> next[0] = e)) {
            if (spliterator.tryAdvance(e -> {})) {
                return Optional.empty();
            }
            return Optional.of(next[0]);
        }
        return Optional.empty();
    }

    static <E> Optional<E> findLast(
            Spliterator<? extends E> spliterator) {
        @SuppressWarnings("unchecked")
        E[] next = (E[]) new Object[1];
        boolean set = false;
        while (true) {
            Spliterator<? extends E> prefix = spliterator.trySplit();
            if (spliterator.tryAdvance(e -> next[0] = e)) {
                set = true;
            } else if (prefix != null) {
                spliterator = prefix;
            } else {
                return set ? Optional.of(next[0]) : Optional.empty();
            }
        }
    }

    static <E> Spliterator<E> flatten(
            Spliterator<? extends Spliterator<? extends E>> spliterators) {
        return new AbstractSpliterator<E>(Long.MAX_VALUE, 0) {
            private Spliterator<? extends E> current = emptySpliterator();
            public boolean tryAdvance(Consumer<? super E> action) {
                while (true) {
                    if (current.tryAdvance(action)) return true;
                    if (spliterators.tryAdvance(s -> current = s)) continue;
                    return false;
                }
            }
        };
    }

    static <E> Spliterator.OfInt indexesOf(
            Spliterator<? extends E> spliterator, Object object) {
        return new AbstractIntSpliterator(Long.MAX_VALUE, 0) {
            private int index;
            private E next;
            public boolean tryAdvance(IntConsumer action) {
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

    static <E, F, R> Spliterator<R> zip(
            Spliterator<? extends E> spl0,
            Spliterator<? extends F> spl1,
            BiFunction<? super E, ? super F, ? extends R> mapper) {
        return new AbstractSpliterator<R>(Long.MAX_VALUE, 0) {
            private E e0;
            private F e1;
            public boolean tryAdvance(Consumer<? super R> action) {
                boolean has0 = spl0.tryAdvance(e -> this.e0 = e);
                boolean has1 = spl1.tryAdvance(e -> this.e1 = e);
                if (!has0 | !has1) return false;
                action.accept(mapper.apply(e0, e1));
                return true;
            }
        };
    }

    static Spliterator.OfInt range(int from, int to) {
        return new AbstractIntSpliterator(Long.MAX_VALUE, 0) {
            private int index = from;
            public boolean tryAdvance(IntConsumer action) {
                if (index < to) {
                    action.accept(index++);
                    return true;
                }
                return false;
            }
        };
    }

    static Spliterator.OfInt indexes(Spliterator<?> spliterator) {
        return range(0, toInt(count(spliterator)));
    }

//    static <E> Spliterator slice(
//            Spliterator<E> spliterator, int from, int to) {
//        if (to < from | from < 0) throw new IllegalArgumentException();
//        return new AbstractSpliterator<E>(Long.MAX_VALUE, 0) {
//            private E next;
//            private int index;
//            public boolean tryAdvance(Consumer<? super E> action) {
//                while (index < from) {
//                    if (!tryAdvance(e -> {})) {
//                        throw new IllegalArgumentException();
//                    }
//                    index++;
//                }
//                if (index < to) {
//                    if (!spliterator.tryAdvance(action)) {
//                        throw new IllegalArgumentException();
//                    }
//                    index++;
//                    return true;
//                }
//                return false;
//            }
//        };
//    }

    static <E> Spliterator limitLast(
            Spliterator<E> spliterator, long sizeLong) {
        int size = toInt(sizeLong); // consistency with limit()
        if (size == 0) return emptySpliterator();
        if (size < 0) throw new IllegalArgumentException();
        return new AbstractSpliterator<E>(Long.MAX_VALUE, 0) {
            private E next;
            private int used;
            private int index;
            private boolean skipped;
            private E[] queue = (E[]) new Object[size];
            public boolean tryAdvance(Consumer<? super E> action) {
                if (!skipped) {
                    skipped = true;
                    while (spliterator.tryAdvance(e -> next = e)) {
                        queue[index] = next;
                        index++;
                        index %= size;
                        if (used < size) used++;
                    }
                    if (used < size) index -= used;
                }
                if (used == 0) return false;
                used--;
                E e = queue[index];
                index++;
                index %= size;
                action.accept(e);
                return true;
            }
        };
    }

    static <E> Spliterator skipLast(
            Spliterator<E> spliterator, long sizeLong) {
        int size = toInt(sizeLong); // consistency with skip()
        if (size == 0) return spliterator;
        if (size < 0) throw new IllegalArgumentException();
        return new AbstractSpliterator<E>(Long.MAX_VALUE, 0) {
            private E next;
            private int used;
            private int index;
            private boolean skipped;
            private E[] queue = (E[]) new Object[size];
            public boolean tryAdvance(Consumer<? super E> action) {
                if (!skipped) {
                    skipped = true;
                    while (spliterator.tryAdvance(e -> next = e)) {
                        queue[index] = next;
                        index++;
                        index %= size;
                        if (used < size) used++;
                        if (used == size) break;
                    }
                }
                if (used < size) return false;
                if (!spliterator.tryAdvance(e -> next = e)) return false;
                E e = queue[index];
                queue[index] = next;
                index++;
                index %= size;
                action.accept(e);
                return true;
            }
        };
    }

    // Can remove once Java 8 support is dropped.
    static <E> Spliterator<E> takeWhile(
            Spliterator<? extends E> spliterator,
            Predicate<? super E> predicate) {
        return new AbstractSpliterator<E>(Long.MAX_VALUE, 0) {
            private E next;
            private boolean found;
            public boolean tryAdvance(Consumer<? super E> action) {
                if (!found) {
                    if (spliterator.tryAdvance(e -> next = e)) {
                        if (!predicate.test(next)) {
                            found = true;
                            return false;
                        } else {
                            action.accept(next);
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    // Can remove once Java 8 support is dropped.
    static <E> Spliterator<E> dropWhile(
            Spliterator<? extends E> spliterator,
            Predicate<? super E> predicate) {
        return new AbstractSpliterator<E>(Long.MAX_VALUE, 0) {
            private E next;
            private boolean found;
            public boolean tryAdvance(Consumer<? super E> action) {
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

    @SuppressWarnings("unchecked")
    static <E> Spliterator<E> intersection(
            Spliterator<? extends E> first,
            Spliterator<?> second) {
        return multisetOperation(first, (Spliterator<E>) second, false, false);
    }

    @SuppressWarnings("unchecked")
    static <E> Spliterator<E> difference(
            Spliterator<? extends E> first,
            Spliterator<?> second) {
        return multisetOperation(first, (Spliterator<E>) second, true, false);
    }

    static <E> Spliterator<E> union(
            Spliterator<? extends E> first,
            Spliterator<? extends E> second) {
        return multisetOperation(second, first, true, true);
    }

    static Spliterator.OfInt indexesOfSlice(
            Spliterator<?> spliterator, Spliterator<?> slice) {
        Object[] array = toArray(slice);
        int[] jumps = new int[array.length + 1];
        copyInto(matchLengths(toSpliterator(array), array, jumps, -1), jumps);
        return toMatchIndexes(matchLengths(spliterator, array, jumps, 0), array.length);
    }

    static Spliterator<Object[]> permutations(Object[] array) {
        return new AbstractSpliterator<Object[]>(Long.MAX_VALUE, 0) {
            private int[] index = IntStream.range(0, array.length).toArray();
            public boolean tryAdvance(Consumer<? super Object[]> action) {
                if (index == null) return false;
                Object[] r = Arrays.stream(index).mapToObj(i -> array[i]).toArray();
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

    static Spliterator<Object[]> combinations(Object[] array, int size) {
        if (size < 0 | size > array.length) throw new IllegalArgumentException();
        return new AbstractSpliterator<Object[]>(Long.MAX_VALUE, 0) {
            private int[] index = IntStream.range(0, size).toArray();
            public boolean tryAdvance(Consumer<? super Object[]> action) {
                if (index == null) return false;
                Object[] r = Arrays.stream(index).mapToObj(i -> array[i]).toArray();
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

    private static Spliterator.OfInt matchLengths(
            Spliterator<?> spliterator, Object[] slice, int[] jumps, int from) {
        return new AbstractIntSpliterator(Long.MAX_VALUE, 0) {
            private int j = from;
            private Object next;
            private boolean started;
            public boolean tryAdvance(IntConsumer action) {
                if (!started) {
                    started = true;
                    action.accept(j);
                    return true;
                }
                if (!spliterator.tryAdvance(e -> next = e)) return false;
                while (j == slice.length || j >= 0 && !Objects.equals(next, slice[j])) {
                    j = jumps[j];
                }
                action.accept(++j);
                return true;
            }
        };
    }

    private static Spliterator.OfInt toMatchIndexes(
            Spliterator.OfInt lengths, int sliceLength) {
        return new AbstractIntSpliterator(Long.MAX_VALUE, 0) {
            private int index;
            private int length;
            public boolean tryAdvance(IntConsumer action) {
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
        spliterator.forEachRemaining((IntConsumer) e -> slice[i[0]++] = e);
    }

    private static <E> Spliterator<E> multisetOperation(
            Spliterator<? extends E> first,
            Spliterator<? extends E> second,
            boolean difference, boolean union) {
        Map<Object, Long> multiset = new HashMap<>();
        return new AbstractSpliterator<E>(Long.MAX_VALUE, 0) {
            private E next;
            private boolean concatenated;
            public boolean tryAdvance(Consumer<? super E> action) {
                if (!concatenated) {
                    while (second.tryAdvance(e -> next = e)) {
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
}
