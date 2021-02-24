package org.bitbucket.seqly;

import java.util.Spliterator;

final class ArraySeq<E> extends AbstractSeq<E> implements Seq<E> {

    private final E[] array;

    @SuppressWarnings("unchecked")
    ArraySeq(Object[] array) {
        this.array = (E[]) array;
    }

    public int size() {
        return array.length;
    }

    public boolean isEmpty() {
        return array.length == 0;
    }

    public E get(int index) {
        return array[index];
    }

    public Spliterator<E> spliterator() {
        return Util.toSpliterator(array);
    }
}
