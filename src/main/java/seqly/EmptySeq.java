package seqly;

import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;

final class EmptySeq extends AbstractSeq<Object> implements Seq<Object> {

    static final EmptySeq INSTANCE = new EmptySeq();

    private EmptySeq() {
    }

    public int size() {
        return 0;
    }

    public boolean isEmpty() {
        return true;
    }

    public Object get(int index) {
        throw new NoSuchElementException();
    }

    public Spliterator<Object> spliterator() {
        return Spliterators.emptySpliterator();
    }
}
