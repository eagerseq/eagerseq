package seqly;

import java.util.Arrays;
import java.util.Spliterator;

final class ArraySeq<E> extends AbstractSeq<E> implements Seq<E> {
    
    private final E[] array;

    @SuppressWarnings("unchecked")
    ArraySeq(Object[] array) {
        this.array = (E[]) array;
    }

    public Spliterator<E> spliterator() {
        return Util.spliterator(array);
    }

    static final class Builder<E> implements Seq.Builder<E> {
        private static Object[] EMPTY = new Object[0];
        @SuppressWarnings("unchecked")
        private E[] array = (E[]) EMPTY;
        private int size;
        public Builder<E> add(E element) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size * 2 + 1);
            }
            array[size++] = element;
            return this;
        }
        public ArraySeq<E> build() {
            return new ArraySeq<>(size == array.length
                    ? array : Arrays.copyOf(array, size));
        }
    }
}
