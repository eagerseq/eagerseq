package io.github.jancellor.seq;

final class SeqStreamBuilder<E>
        extends ArrayBuilder<E> implements SeqStream.Builder<E> {

    public SeqStream<E> build() {
        return new SourceSeqStream<>(Sources.toSource(buildArray()));
    }
}
