package io.github.eagerseq;

import java.util.Spliterator;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Spliterator} whose primitive traversal is a cancellable push:
 * {@link #forEachWhile} pushes elements into a {@link Sink} until it returns
 * {@code false} or the elements are exhausted. Like any spliterator, a
 * {@code Source} is advanced permanently by traversal.
 */
public interface Source<E> extends Spliterator<E> {

    /**
     * Returns {@code spliterator} itself if it is already a {@code Source},
     * or else a view of it whose traversal pulls from it one element at a
     * time.
     */
    static <E> Source<E> viewOf(Spliterator<? extends E> spliterator) {
        return Sources.toSource(spliterator);
    }

    /**
     * Pushes remaining elements into {@code sink} while it returns
     * {@code true}, advancing this source past every element pushed.
     *
     * @return {@code false} if and only if {@code sink} returned
     *         {@code false}
     */
    boolean forEachWhile(Sink<? super E> sink);

    default void forEachRemaining(Consumer<? super E> action) {
        requireNonNull(action);
        forEachWhile(e -> {
            action.accept(e);
            return true;
        });
    }

    /**
     * Pushes one element into a sink that takes it and stops.
     */
    default boolean tryAdvance(Consumer<? super E> action) {
        requireNonNull(action);
        return !forEachWhile(e -> {
            action.accept(e);
            return false;
        });
    }

    /**
     * Returns {@code null}: by default a source does not split, and so is
     * traversed sequentially.
     */
    default Spliterator<E> trySplit() {
        return null;
    }

    /**
     * Returns {@link Long#MAX_VALUE}, the estimate for a source of unknown
     * size.
     */
    default long estimateSize() {
        return Long.MAX_VALUE;
    }

    /**
     * Returns {@code 0}, promising nothing. Characteristics are promises a
     * traversal relies on, so a source that does not report one is merely
     * traversed less efficiently, while one that reports a characteristic it
     * does not have is traversed incorrectly.
     */
    default int characteristics() {
        return 0;
    }
}
