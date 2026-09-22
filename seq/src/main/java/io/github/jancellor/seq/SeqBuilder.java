package io.github.jancellor.seq;

final class SeqBuilder<E> extends ArrayBuilder<E> implements Seq.Builder<E> {

    public Seq<E> build() {
        return new ArraySeq<>(buildArray());
    }
}
