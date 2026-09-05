package io.github.eagerseq;

final class SeqStreamBuilder<E> extends ArrayBuilder<E>
        implements
            SeqStream.Builder<E> {

    public SeqStream<E> build() {
        return new SpliteratorSeqStream<>(Split.toSpliterator(buildArray()));
    }
}
