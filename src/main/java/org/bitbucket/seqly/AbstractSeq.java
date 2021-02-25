package org.bitbucket.seqly;

/**
 * Implements {@code equals}, {@code hashCode} and {@code toString} so that
 * subclasses only have to implement {@link Seq#spliterator()}.
 */
public abstract class AbstractSeq<E> implements Seq<E> {

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return Util.listHash(spliterator());
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object object) {
        if (object == this) return true;
        if (!(object instanceof Seq)) return false;
        Seq<?> that = (Seq<?>) object;
        return Util.listEquals(spliterator(), that.spliterator());
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        return Util.toString(spliterator(), ", ", "[", "]");
    }
}
