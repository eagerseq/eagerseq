package io.github.eagerseq;

import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * The growable array shared by the {@code Seq} and {@code SeqStream}
 * builders and used directly as a {@code Consumer} sink by {@link Split}.
 * Knows nothing of {@code Seq} or {@code SeqStream}: subclasses bind
 * {@link #buildArray} to a result type.
 */
class ArrayBuilder<E> implements Consumer<E> {

    static final Object[] EMPTY = {};

    private static final IntFunction<?> GENERATOR = Object[]::new;
    private static final int MAX_LENGTH = Integer.MAX_VALUE - 8;
    private static final int MAX_UNCAPPED_LENGTH = (MAX_LENGTH - 4) / 2;

    private final IntFunction<E[]> generator;
    private E[] array;
    private int size;

    @SuppressWarnings("unchecked")
    ArrayBuilder() {
        this.generator = (IntFunction<E[]>) GENERATOR;
        this.array = (E[]) EMPTY;
    }

    ArrayBuilder(IntFunction<E[]> generator) {
        this.generator = generator;
        this.array = generator.apply(0);
    }

    static int nextLength(int length) {
        if (length <= MAX_UNCAPPED_LENGTH) return length * 2 + 4;
        if (length < MAX_LENGTH) return MAX_LENGTH;
        throw new OutOfMemoryError("maximum array length exceeded");
    }

    public final void accept(E element) {
        checkNotBuilt();
        ensureSize(size + 1);
        array[size++] = element;
    }

    final ArrayBuilder<E> combine(ArrayBuilder<E> that) {
        checkNotBuilt();
        that.checkNotBuilt();
        int combinedSize = size + that.size;
        ensureSize(combinedSize < 0 ? MAX_LENGTH + 1 : combinedSize);
        System.arraycopy(that.array, 0, array, size, that.size);
        size = combinedSize;
        return this;
    }

    final E[] buildArray() {
        checkNotBuilt();
        // careful if ever removing built-check and allowing mutations
        E[] result = size == array.length ? array : arrayCopy(size);
        array = null;
        return result;
    }

    private void ensureSize(int minimumSize) {
        if (array.length >= minimumSize) return;
        int length = array.length;
        do {
            length = nextLength(length);
        } while (length < minimumSize);
        array = arrayCopy(length);
    }

    private E[] arrayCopy(int length) {
        E[] copy = generator.apply(length);
        System.arraycopy(array, 0, copy, 0, size);
        return copy;
    }

    private void checkNotBuilt() {
        if (array == null) {
            throw new IllegalStateException("builder has already been built");
        }
    }
}
