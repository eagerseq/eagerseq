package io.github.eagerseq;

final class SeqBuilder<E> extends ArrayBuilder<E> implements Seq.Builder<E> {

    public Seq<E> build() {
        return new ArraySeq<>(buildArray());
    }
}
