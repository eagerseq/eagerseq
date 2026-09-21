package io.github.jancellor.seq;

import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A source of unknown size whose primitive is {@code forEachWhile};
 * {@code tryAdvance} is derived from it by pushing into a downstream predicate
 * that takes one element and stops, and splitting is inherited from
 * {@link AbstractSpliterator}.
 */
abstract class AbstractSource<E>
        extends AbstractSpliterator<E> implements Source<E> {

    private final Once once = new Once();

    @SuppressWarnings("MagicConstant")
    AbstractSource(int characteristics) {
        super(Long.MAX_VALUE, characteristics & ~(SIZED | SUBSIZED));
    }

    public final boolean tryAdvance(Consumer<? super E> action) {
        once.action = requireNonNull(action);
        return !forEachWhile(once);
    }

    private final class Once implements Predicate<E> {
        Consumer<? super E> action;

        public boolean test(E e) {
            action.accept(e);
            return false;
        }
    }
}
