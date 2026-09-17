package io.github.eagerseq;

import java.util.Spliterator;

final class SpliteratorSeqStream<E> implements SeqStream<E> {

    private final Pipeline pipeline;
    private Spliterator<E> spliterator;

    SpliteratorSeqStream(Spliterator<? extends E> spliterator) {
        this(spliterator, new SeqStreamPipeline());
    }

    @SuppressWarnings("unchecked")
    SpliteratorSeqStream(
            Spliterator<? extends E> spliterator, Pipeline pipeline) {
        this.pipeline = pipeline;
        this.spliterator = (Spliterator<E>) spliterator;
    }

    public Pipeline pipeline() {
        return pipeline;
    }

    public Spliterator<E> spliterator() {
        requireUsable();
        Spliterator<E> result = spliterator;
        spliterator = null;
        return result;
    }

    public SeqStream<E> onClose(Runnable closeHandler) {
        requireUsable();
        pipeline.onClose(closeHandler);
        return this;
    }

    private void requireUsable() {
        if (spliterator == null || pipeline.isClosed()) {
            throw new IllegalStateException(
                    "stream has already been operated upon or closed");
        }
    }
}
