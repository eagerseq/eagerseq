package seqly;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class Util {

    private Util() {
    }

    public static <E> Stream<E> stream(Iterable<? extends E> iterable) {
        return stream(iterable.spliterator());
    }

    public static <E> Stream<E> stream(Optional<? extends E> optional) {
        return optional.<Stream<E>>map(Stream::of).orElseGet(Stream::empty);
    }

    public static <E> Stream<E> stream(Iterator<? extends E> iterator) {
        return stream(Spliterators.spliteratorUnknownSize(iterator, 0));
    }

    @SuppressWarnings("unchecked")
    public static <E> Stream<E> stream(Spliterator<? extends E> spliterator) {
        return (Stream<E>) StreamSupport.stream(spliterator, false);
    }

    public static <E> Stream<E> stream(Object[] array) {
        return stream(spliterator(array));
    }

    @SuppressWarnings("unchecked")
    public static <E> Spliterator<E> spliterator(Object[] array) {
        return (Spliterator<E>) Spliterators.spliterator(
                array, Spliterator.ORDERED);
    }

    public static Object[] toArray(Iterable<?> iterable) {
        return stream(iterable).toArray();
    }

    public static Object[] toArray(Iterator<?> iterator) {
        return stream(iterator).toArray();
    }

    public static Object[] toArray(Spliterator<?> spliterator) {
        return stream(spliterator).toArray();
    }

    public static int toInt(long value) {
        int intValue = (int) value;
        if (intValue != value) {
            throw new RuntimeException("index overflowed int");
        }
        return intValue;
    }

    public static <T> T[] toArray(
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

    public static void reverse(Object[] array) {
        Collections.reverse(Arrays.asList(array));
    }

    public static void rotate(Object[] array, int size) {
        Collections.rotate(Arrays.asList(array), size);
    }

    public static void shuffle(Object[] array, Random random) {
        Collections.shuffle(Arrays.asList(array), random);
    }

    public static <E> Spliterator<E> cycle(Iterable<? extends E> iterable) {
        return new AbstractSpliterator<E>(Long.MAX_VALUE, 0) {
            private Iterator<? extends E> iterator = iterable.iterator();
            public boolean tryAdvance(Consumer<? super E> action) {
                if (!iterator.hasNext()) {
                    throw new RuntimeException("cannot cycle empty iterable");
                }
                action.accept(iterator.next());
                if (!iterator.hasNext()) {
                    iterator = iterable.iterator();
                }
                return true;
            }
        };
    }

    public static long count(Spliterator<?> spliterator) {
        return (spliterator.characteristics() & Spliterator.SIZED) == 0
                ? stream(spliterator).count()
                : spliterator.estimateSize();
    }

    public static <E> Optional<E> findOnly(
            Spliterator<? extends E> spliterator) {
        @SuppressWarnings("unchecked")
        E[] o = (E[]) new Object[1];
        if (spliterator.tryAdvance(e -> o[0] = e)) {
            if (spliterator.tryAdvance(e -> {
            })) {
                throw new RuntimeException("multiple elements");
            }
            return Optional.of(o[0]);
        }
        return Optional.empty();
    }

    public static <E> Optional<E> findLast(
            Spliterator<? extends E> spliterator) {
        @SuppressWarnings("unchecked")
        E[] o = (E[]) new Object[1];
        boolean set = false;
        while (true) {
            Spliterator<? extends E> prefix = spliterator.trySplit();
            if (spliterator.tryAdvance(e -> o[0] = e)) {
                set = true;
            } else if (prefix != null) {
                spliterator = prefix;
            } else {
                return set ? Optional.of(o[0]) : Optional.empty();
            }
        }
    }

    public static <E> Spliterator<Integer> indexesOf(
            Spliterator<? extends E> spliterator, Object object) {
        return new AbstractSpliterator<Integer>(Long.MAX_VALUE, 0) {
            private int index;
            private boolean found;
            public boolean tryAdvance(Consumer<? super Integer> action) {
                found = false;
                while (spliterator.tryAdvance(e -> {
                    if (Objects.equals(object, e)) {
                        action.accept(index);
                        found = true;
                    }
                    index++;
                })) {
                    if (found) return true;
                }
                return false;
            }
        };
    }

    public static <E> Spliterator<E> intersection(
            Spliterator<? extends E> first,
            Spliterator<?> second) {
        return intersection(first, second, false);
    }

    public static <E> Spliterator<E> difference(
            Spliterator<? extends E> first,
            Spliterator<?> second) {
        return intersection(first, second, true);
    }

    public static <E> Spliterator<E> takeWhile(
            Spliterator<? extends E> spliterator,
            Predicate<? super E> predicate) {
        return new AbstractSpliterator<E>(Long.MAX_VALUE, 0) {
            private boolean found;
            public boolean tryAdvance(Consumer<? super E> action) {
                if (spliterator.tryAdvance(e -> {
                    if (predicate.test(e)) {
                        action.accept(e);
                    } else {
                        found = true;
                    }
                })) {
                    return !found;
                }
                return false;
            }
        };
    }

    public static <E> Spliterator<E> dropWhile(
            Spliterator<? extends E> spliterator,
            Predicate<? super E> predicate) {
        return new AbstractSpliterator<E>(Long.MAX_VALUE, 0) {
            private boolean found;
            public boolean tryAdvance(Consumer<? super E> action) {
                if (!found) {
                    while (spliterator.tryAdvance(e -> {
                        if (!predicate.test(e)) {
                            action.accept(e);
                            found = true;
                        }
                    })) {
                        if (found) return true;
                    }
                    return false;
                }
                return spliterator.tryAdvance(action);
            }
        };
    }

    private static <E> Spliterator<E> intersection(
            Spliterator<? extends E> first,
            Spliterator<?> second,
            boolean complement) {
        LinkedHashMap<Object, Integer> set = toMultiset(second);
        return new AbstractSpliterator<E>(Long.MAX_VALUE, 0) {
            private boolean found;
            public boolean tryAdvance(Consumer<? super E> action) {
                found = false;
                while (first.tryAdvance(element -> {
                    if (removeOne(set, element) != complement) {
                        action.accept(element);
                        found = true;
                    }
                })) {
                    if (found) return true;
                }
                return false;
            }
        };
    }

    private static LinkedHashMap<Object, Integer> toMultiset(
            Spliterator<?> spliterator) {
        LinkedHashMap<Object, Integer> set = new LinkedHashMap<>();
        spliterator.forEachRemaining(element -> {
            Integer count = set.get(element);
            if (count == null) {
                count = 0;
            }
            set.put(element, count + 1);
        });
        return set;
    }

    private static <E> boolean removeOne(
            LinkedHashMap<E, Integer> set, E element) {
        Integer count = set.get(element);
        if (count != null) {
            count--;
            if (count == 0) set.remove(element);
            else set.put(element, count);
            return true;
        }
        return false;
    }
}
