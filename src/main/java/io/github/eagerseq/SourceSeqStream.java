package io.github.eagerseq;

final class SourceSeqStream<E> implements SeqStream<E> {

    private final Pipeline pipeline;
    private Source<E> source;

    SourceSeqStream(Source<? extends E> source) {
        this(source, new SeqStreamPipeline());
    }

    @SuppressWarnings("unchecked")
    SourceSeqStream(
            Source<? extends E> source, Pipeline pipeline) {
        this.pipeline = pipeline;
        this.source = (Source<E>) source;
    }

    public Pipeline pipeline() {
        return pipeline;
    }

    public Source<E> spliterator() {
        requireUsable();
        Source<E> result = source;
        source = null;
        return result;
    }

    public SeqStream<E> onClose(Runnable closeHandler) {
        requireUsable();
        pipeline.onClose(closeHandler);
        return this;
    }

    private void requireUsable() {
        if (source == null || pipeline.isClosed()) {
            throw new IllegalStateException(
                    "stream has already been operated upon or closed");
        }
    }
}
