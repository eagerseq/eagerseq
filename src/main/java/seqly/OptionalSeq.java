package seqly;

import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;

final class OptionalSeq<E> extends AbstractSeq<E> implements Seq<E> {

    private final Optional<E> optional;

    // The cast is safe approximately-because (spl)iterator() is readonly.
    @SuppressWarnings("unchecked")
    OptionalSeq(Optional<? extends E> optional) {
        this.optional = (Optional<E>) optional;
    }

    public Spliterator<E> spliterator() {
        return new AbstractSpliterator<E>(optional.isPresent() ? 1 : 0,
                Spliterator.SIZED | Spliterator.SUBSIZED
                        | Spliterator.ORDERED | Spliterator.IMMUTABLE) {
            private boolean done;
            public boolean tryAdvance(Consumer<? super E> action) {
                if (!done & optional.isPresent()) {
                    action.accept(optional.get());
                    done = true;
                    return true;
                }
                return false;
            }
        };
    }
}
