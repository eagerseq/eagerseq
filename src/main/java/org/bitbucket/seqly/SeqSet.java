package org.bitbucket.seqly;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;

final class SeqSet<E> extends AbstractSet<E> {

    private final E[] array;

    // caller must ensure precondition of no duplicates
    @SuppressWarnings("unchecked")
    SeqSet(Object[] array) {
        this.array = (E[]) array;
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
