package seqly;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Spliterator;

final class SeqSet<E> extends AbstractSet<E> {

    private final Seq<E> seq;

    SeqSet(Seq<E> seq) {
        this.seq = seq;
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
