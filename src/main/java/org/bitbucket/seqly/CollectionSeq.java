package org.bitbucket.seqly;

import java.util.Collection;
import java.util.Spliterator;

import static java.util.Spliterator.ORDERED;

final class CollectionSeq<E> extends AbstractSeq<E> implements Seq<E> {

    private final Collection<E> collection;

    @SuppressWarnings("unchecked")
    CollectionSeq(Collection<? extends E> collection) {
        if (!collection.spliterator().hasCharacteristics(ORDERED)) {
            throw new IllegalArgumentException(
                    "collection spliterator was not ORDERED");
        }
        this.collection = (Collection<E>) collection;
    }

    public int size() {
        return collection.size();
    }

    public boolean isEmpty() {
        return collection.isEmpty();
    }

    public long count() {
        // Collection.size() clamps values above Integer.MAX_VALUE, while
        // count() returns an exact long, so retain the traversing default.
        return super.count();
    }

    public Spliterator<E> spliterator() {
        return collection.spliterator();
    }
}
