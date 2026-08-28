package org.bitbucket.seqly;

import java.util.Spliterator;

import static java.util.Objects.requireNonNull;

final class SpliteratorSeqStream<E> implements SeqStream<E> {

    private Spliterator<E> spliterator;

    @SuppressWarnings("unchecked")
    SpliteratorSeqStream(Spliterator<? extends E> spliterator) {
        this.spliterator = (Spliterator<E>) requireNonNull(spliterator);
    }

    public Spliterator<E> spliterator() {
        Spliterator<E> source = spliterator;
        if (source == null) {
            throw new IllegalStateException(
                    "stream has already been operated upon or closed");
        }
        spliterator = null;
        return source;
    }
}
