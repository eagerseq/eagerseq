package io.github.jancellor.seq;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A sized source over a range of an array.
 */
final class ArraySource<E> implements Source<E> {

    private final Object[] array;
    private int index;
    private final int fence;
    private final int characteristics;

    ArraySource(Object[] array, int index, int fence, int characteristics) {
        this.array = array;
        this.index = index;
        this.fence = fence;
        this.characteristics = characteristics | SIZED | SUBSIZED;
    }

    @SuppressWarnings("unchecked")
    public boolean forEachWhile(Predicate<? super E> action) {
        requireNonNull(action);
        int i = index;
        try {
            while (i < fence) {
                if (!action.test((E) array[i++])) return false;
            }
            return true;
        } finally {
            index = i;
        }
    }

    @SuppressWarnings("unchecked")
    public void forEachRemaining(Consumer<? super E> action) {
        requireNonNull(action);
        int i = index;
        try {
            while (i < fence) action.accept((E) array[i++]);
        } finally {
            index = i;
        }
    }

    @SuppressWarnings("unchecked")
    public boolean tryAdvance(Consumer<? super E> action) {
        requireNonNull(action);
        if (index >= fence) return false;
        action.accept((E) array[index++]);
        return true;
    }

    public Spliterator<E> trySplit() {
        int from = index;
        int mid = (from + fence) >>> 1;
        if (from >= mid) return null;
        index = mid;
        return new ArraySource<>(array, from, mid, characteristics);
    }

    public long estimateSize() {
        return fence - index;
    }

    public int characteristics() {
        return characteristics;
    }
}
