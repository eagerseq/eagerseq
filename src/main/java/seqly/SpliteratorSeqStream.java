package seqly;

import java.util.Spliterator;

final class SpliteratorSeqStream<E> implements SeqStream<E> {

    private final Spliterator<E> spliterator;

    @SuppressWarnings("unchecked")
    SpliteratorSeqStream(Spliterator<? extends E> spliterator) {
        this.spliterator = (Spliterator<E>) spliterator;
    }

    public Spliterator<E> spliterator() {
        return spliterator;
    }
}
