package org.bitbucket.seqly;

import java.util.Spliterator;

import static java.util.Spliterator.ORDERED;

final class IterableSeq<E> extends AbstractSeq<E> implements Seq<E> {

    private final Iterable<E> iterable;

    @SuppressWarnings("unchecked")
    IterableSeq(Iterable<? extends E> iterable) {
        if (!iterable.spliterator().hasCharacteristics(ORDERED)) {
            throw new IllegalArgumentException(
                    "iterable spliterator was not ORDERED");
        }
        this.iterable = (Iterable<E>) iterable;
    }

    public Spliterator<E> spliterator() {
        return iterable.spliterator();
    }
}
