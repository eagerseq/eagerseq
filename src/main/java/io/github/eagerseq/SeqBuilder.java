package io.github.eagerseq;

import java.util.function.IntFunction;

final class SeqBuilder<E> implements Seq.Builder<E> {

    static final Object[] EMPTY = {};

    private static final IntFunction<?> GENERATOR = Object[]::new;
    private static final int MAX_LENGTH = Integer.MAX_VALUE - 8;
    private static final int CONFINED_LENGTH = (MAX_LENGTH - 4) / 2 + 1;

    private final IntFunction<E[]> generator;
    private E[] array;
    private int size;

    @SuppressWarnings("unchecked")
    SeqBuilder() {
        this.generator = (IntFunction<E[]>) GENERATOR;
        this.array = (E[]) EMPTY;
    }

    SeqBuilder(IntFunction<E[]> generator) {
        this.generator = generator;
        this.array = generator.apply(0);
    }

    static int nextLength(int length) {
        if (length < CONFINED_LENGTH) return length * 2 + 4;
        if (length < MAX_LENGTH) return MAX_LENGTH;
        throw new OutOfMemoryError(
                "Seq size exceeds the maximum array length");
    }

    static <A> A[] arrayCopy(
            A[] array, int length, IntFunction<A[]> generator) {
        A[] copy = generator.apply(length);
        System.arraycopy(array, 0, copy, 0, Math.min(array.length, length));
        return copy;
    }

    public void accept(E element) {
        if (size == array.length) {
            array = arrayCopy(array, nextLength(size), generator);
        }
        array[size++] = element;
    }

    public Seq<E> build() {
        return new ArraySeq<>(trim());
    }

    E[] trim() {
        return size == array.length
                ? array
                : arrayCopy(array, size, generator);
    }
}
