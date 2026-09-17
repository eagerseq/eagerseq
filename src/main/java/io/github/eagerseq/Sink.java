package io.github.eagerseq;

/**
 * The receiving end of a push traversal: a {@link Source} pushes each element
 * into a {@code Sink}, which says whether to continue.
 */
@FunctionalInterface
public interface Sink<E> {

    /**
     * Receives {@code element}.
     *
     * @return {@code true} to continue, or {@code false} to cancel before
     *         the next element; the current element has already been taken
     */
    boolean push(E element);
}
