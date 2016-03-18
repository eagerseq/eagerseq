package seqly;

import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;

final class CollectionSeq<E> extends AbstractSeq<E> implements Seq<E> {

    private final Collection<E> collection;
    
    @SuppressWarnings("unchecked")
    CollectionSeq(Collection<? extends E> collection) {
        this.collection = (Collection<E>) collection;
    }

    public Iterator<E> iterator() {
        return collection.iterator();
    }
    
    public Spliterator<E> spliterator() {
        return collection.spliterator();
    }
}
