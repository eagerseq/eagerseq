package io.github.eagerseq;

/**
 * The extension point for custom {@link Seq} implementations. Implements
 * {@code equals}, {@code hashCode} and {@code toString} so that subclasses
 * only have to implement {@link Seq#spliterator()}.
 *
 * @param <E> the type of elements in this sequence
 */
public abstract class AbstractSeq<E> implements Seq<E> {

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return Split.listHash(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object object) {
        if (object == this) return true;
        if (!(object instanceof Seq)) return false;
        Seq<?> that = (Seq<?>) object;
        return Split.listEquals(spliterator(), that.spliterator());
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        return Split.toString(spliterator(), ", ", "[", "]");
    }
}
