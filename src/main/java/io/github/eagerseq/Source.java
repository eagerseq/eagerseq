package io.github.eagerseq;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Spliterator} whose primitive traversal is a cancellable push:
 * {@link #forEachWhile} pushes elements into a {@link Predicate} until it returns
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
     * Calls the given {@code action} predicate for each remaining element in this
     * {@code Source} while {@code action} returns {@code true}.
     * Returns {@code true} if {@code action} returned {@code true}
     * for all remaining elements.
     * Returns {@code false} if {@code action} returned {@code false},
     * stopping traversal, even if no elements remain.
     */
    boolean forEachWhile(Predicate<? super E> action);

    default void forEachRemaining(Consumer<? super E> action) {
        requireNonNull(action);
        forEachWhile(e -> {
            action.accept(e);
            return true;
        });
    }

    /**
     * Pushes one element into a downstream predicate that takes it and stops.
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
