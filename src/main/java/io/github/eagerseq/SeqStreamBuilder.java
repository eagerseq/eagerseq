package io.github.eagerseq;

final class SeqStreamBuilder<E>
        extends ArrayBuilder<E> implements SeqStream.Builder<E> {

    public SeqStream<E> build() {
        return new SourceSeqStream<>(Sources.toSource(buildArray()));
    }
}
