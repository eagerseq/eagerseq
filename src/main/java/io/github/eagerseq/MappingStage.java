package io.github.eagerseq;

/**
 * A {@link Stage} that pushes exactly one element for each it receives
 * and therefore reports {@link #SIZED} and {@link #estimateSize()}
 * according to the upstream {@link Source}.
 */
abstract class MappingStage<U, E> extends Stage<U, E> {

    MappingStage(Source<? extends U> up) {
        super(up);
    }

    /**
     * Returns the one element to push on for {@code element}.
     */
    abstract E map(U element);

    public final boolean push(U element) {
        return down.push(map(element));
    }

    public int characteristics() {
        return super.characteristics() | (up.characteristics() & SIZED);
    }

    public long estimateSize() {
        return up.estimateSize();
    }
}
