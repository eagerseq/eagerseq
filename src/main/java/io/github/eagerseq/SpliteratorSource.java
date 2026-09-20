package io.github.eagerseq;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A view of a foreign {@link Spliterator} as a source, whose traversal pulls
 * from it one element at a time.
 */
final class SpliteratorSource<E> implements Source<E> {

    private final Spliterator<? extends E> spliterator;
    private final Sources.Box<E> next = new Sources.Box<>();

    SpliteratorSource(Spliterator<? extends E> spliterator) {
        this.spliterator = requireNonNull(spliterator);
    }

    public boolean forEachWhile(Predicate<? super E> action) {
        requireNonNull(action);
        while (spliterator.tryAdvance(next)) {
            if (!action.test(next.value)) return false;
        }
        return true;
    }

    public void forEachRemaining(Consumer<? super E> action) {
        spliterator.forEachRemaining(action);
    }

    public boolean tryAdvance(Consumer<? super E> action) {
        return spliterator.tryAdvance(action);
    }

    public Spliterator<E> trySplit() {
        Spliterator<? extends E> prefix = spliterator.trySplit();
        return prefix == null ? null : new SpliteratorSource<>(prefix);
    }

    public long estimateSize() {
        return spliterator.estimateSize();
    }

    public int characteristics() {
        return spliterator.characteristics();
    }
}
