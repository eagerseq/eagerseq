package seqly;

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
        return Util.listEquals(spliterator(), ((Seq<?>) object).spliterator());
    }

    /**
     * Equivalent to {@code asList().toString()}.
     */
    public String toString() {
        return toString(", ", "[", "]");
    }
}
