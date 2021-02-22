package org.bitbucket.seqly;

import java.util.Spliterator;

final class IterableSeq<E> extends AbstractSeq<E> implements Seq<E> {

    private final Iterable<E> iterable;

    @SuppressWarnings("unchecked")
    IterableSeq(Iterable<? extends E> iterable) {
        this.iterable = (Iterable<E>) iterable;
    }

    public Spliterator<E> spliterator() {
        return iterable.spliterator();
    }
}
