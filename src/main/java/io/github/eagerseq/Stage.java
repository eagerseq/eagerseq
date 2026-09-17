package io.github.eagerseq;

import static java.util.Objects.requireNonNull;

/**
 * A source of {@code E} fed by an upstream source of {@code U}. It faces
 * downstream as a {@code Source<E>} and upstream as the {@code Sink<U>} the
 * upstream pushes into: a traversal binds {@code down} and pushes
 * {@code this} into {@code up}, so a subclass only writes {@code push(U)}.
 * As that binding is a single field, a traversal must not re-enter the same
 * stage.
 */
abstract class Stage<U, E> extends AbstractSource<E> implements Sink<U> {

    final Source<? extends U> up;
    Sink<? super E> down;

    Stage(Source<? extends U> up) {
        this(up, Sources.ordered(up));
    }

    Stage(Source<? extends U> up, int characteristics) {
        super(characteristics);
        this.up = up;
    }

    public boolean forEachWhile(Sink<? super E> sink) {
        down = requireNonNull(sink);
        return up.forEachWhile(this);
    }
}
