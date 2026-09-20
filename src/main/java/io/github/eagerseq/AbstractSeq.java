package io.github.eagerseq;

/**
 * Implements the value semantics shared by {@link Seq}s.
 * Extend this class and implement {@link #spliterator()} for a custom sequence.
 */
public abstract class AbstractSeq<E> implements Seq<E> {

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return Sources.listHash(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object object) {
        if (object == this) return true;
        if (!(object instanceof Seq)) return false;
        Seq<?> that = (Seq<?>) object;
        return Sources.listEquals(spliterator(), that.spliterator());
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        return Sources.toString(spliterator(), ", ", "[", "]");
    }
}
