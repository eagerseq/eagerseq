package seqly;

import java.util.Iterator;
import java.util.Spliterator;

final class IterableSeq<E> extends AbstractSeq<E> implements Seq<E> {

    private final Iterable<E> iterable;

    @SuppressWarnings("unchecked")
    IterableSeq(Iterable<? extends E> iterable) {
        this.iterable = (Iterable<E>) iterable;
    }

    public Iterator<E> iterator() {
        return iterable.iterator();
    }

    public Spliterator<E> spliterator() {
        return iterable.spliterator();
    }
}
