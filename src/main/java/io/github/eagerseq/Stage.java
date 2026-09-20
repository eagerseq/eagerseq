package io.github.eagerseq;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A source of {@code E} fed by an upstream source of {@code U}. It faces
 * downstream as a {@code Source<E>} and upstream as a {@code Predicate<U>}.
 * The default {@code forEachWhile(action)} implementation stores the supplied
 * predicate in {@code this.action}, then calls {@code upstream.forEachWhile(this)}, which
 * delivers elements to {@code test(U)}. A simple stage implements only
 * {@code test(U)}. Stages that override {@code forEachWhile(action)} must first
 * bind {@code this.action = requireNonNull(action)}, even before an early
 * return, and call {@code upstream.forEachWhile(this)} when ready to traverse
 * upstream.
 *
 * <p>The stage itself is the reusable upstream callback, avoiding a capturing lambda on each
 * traversal call. As the action binding is a single field, a traversal
 * must not re-enter the same stage.
 */
abstract class Stage<U, E> extends AbstractSource<E> implements Predicate<U> {

    final Source<? extends U> upstream;
    Predicate<? super E> action;

    Stage(Source<? extends U> upstream) {
        this(upstream, Sources.ordered(upstream));
    }

    Stage(Source<? extends U> upstream, int characteristics) {
        super(characteristics);
        this.upstream = upstream;
    }

    public boolean forEachWhile(Predicate<? super E> action) {
        this.action = requireNonNull(action);
        return upstream.forEachWhile(this);
    }
}
