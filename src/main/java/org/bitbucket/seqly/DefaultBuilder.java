package org.bitbucket.seqly;

import java.util.Arrays;

final class DefaultBuilder<E> implements Seq.Builder<E> {

    private static final Object[] EMPTY = new Object[0];
    @SuppressWarnings("unchecked")
    private E[] array = (E[]) EMPTY;
    private int size;

    public Seq.Builder<E> add(E element) {
        if (size == array.length) {
            array = Arrays.copyOf(array, size * 2 + 1);
        }
        array[size++] = element;
        return this;
    }

    @SuppressWarnings("unchecked")
    public Seq<E> build() {
        return new ArraySeq<>(size == array.length
                ? array : Arrays.copyOf(array, size));
    }
}
