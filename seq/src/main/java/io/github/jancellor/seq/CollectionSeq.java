package io.github.jancellor.seq;

import java.util.Collection;

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

    // Inherit count(): collection.size() may clamp at Integer.MAX_VALUE,
    // while its spliterator can report the full long size.
    public int size() {
        return collection.size();
    }

    public boolean isEmpty() {
        return collection.isEmpty();
    }

    public Source<E> spliterator() {
        return Sources.toSource(collection);
    }
}
