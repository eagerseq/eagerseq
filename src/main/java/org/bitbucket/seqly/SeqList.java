package org.bitbucket.seqly;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;

final class SeqList<E> extends AbstractList<E> {

    private final E[] array;

    @SuppressWarnings("unchecked")
    SeqList(Object[] array) {
        this.array = (E[]) array;
    }

    public E get(int index) {
        return array[index];
    }

    public int size() {
        return array.length;
    }

    public Iterator<E> iterator() {
        return Spliterators.iterator(spliterator());
    }

    public Spliterator<E> spliterator() {
        return Split.toSpliterator(array);
    }
}
