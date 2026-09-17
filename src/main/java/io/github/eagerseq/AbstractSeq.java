package io.github.eagerseq;

/**
 * Implements the value semantics shared by the library's {@link Seq}s.
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
