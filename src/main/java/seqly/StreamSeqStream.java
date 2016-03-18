package seqly;

import java.util.stream.Stream;

final class StreamSeqStream<E> implements SeqStream<E> {
    
    private final Stream<E> stream;

    @SuppressWarnings("unchecked")
    StreamSeqStream(Stream<? extends E> stream) {
        this.stream = (Stream<E>) stream;
    }

    public Stream<E> stream() {
        return stream;
    }
}
