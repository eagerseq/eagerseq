package org.bitbucket.seqly;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.Spliterator;

final class SeqList<E> extends AbstractList<E> {

    private final Seq<E> seq;

    SeqList(Seq<E> seq) {
        this.seq = seq;
    }

    public E get(int index) {
        return seq.get(index);
    }

    public int size() {
        return seq.size();
    }

    public Iterator<E> iterator() {
        return seq.iterator();
    }

    public Spliterator<E> spliterator() {
        return seq.spliterator();
    }
}
