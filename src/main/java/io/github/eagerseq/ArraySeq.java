package io.github.eagerseq;

import java.util.Optional;

// Directly implement methods needing only a fixed number of array reads.
final class ArraySeq<E> extends AbstractSeq<E> implements Seq<E> {

    private final E[] array;

    // array may be a real E[]: read only, never write or escape as E[]
    @SuppressWarnings("unchecked")
    ArraySeq(Object[] array) {
        this.array = (E[]) array;
    }

    public int size() {
        return array.length;
    }

    public boolean isEmpty() {
        return array.length == 0;
    }

    public long count() {
        return array.length;
    }

    public E get(int index) {
        // explicit checks for exception symmetry with Sources.get
        Sources.requireNonNegativeIndex("index", index);
        if (index >= array.length) {
            throw Sources.indexOutOfBounds("index", index, array.length);
        }
        return array[index];
    }

    public E getFirst() {
        if (array.length == 0) throw Sources.emptySequence();
        return array[0];
    }

    public E getLast() {
        if (array.length == 0) throw Sources.emptySequence();
        return array[array.length - 1];
    }

    public E getOnly() {
        if (array.length != 1) throw Sources.notExactlyOne();
        return array[0];
    }

    public Optional<E> findFirst() {
        return array.length == 0 ? Optional.empty() : Optional.of(array[0]);
    }

    public Optional<E> findLast() {
        return array.length == 0
                ? Optional.empty()
                : Optional.of(array[array.length - 1]);
    }

    public Optional<E> findOnly() {
        return array.length == 1 ? Optional.of(array[0]) : Optional.empty();
    }

    public Optional<E> toOptional() {
        if (array.length == 0) return Optional.empty();
        if (array.length > 1) {
            throw Sources.moreThanOne(array[0], array[1]);
        }
        return Optional.of(array[0]);
    }

    public Source<E> spliterator() {
        return Sources.toSource(array);
    }
}
