package io.github.jancellor.seq;

import static java.util.Objects.requireNonNull;

/**
 * The state shared by every stage of one {@link SeqStream} pipeline.
 */
final class SeqStreamPipeline implements SeqStream.Pipeline {

    private final ArrayBuilder<Runnable> closeHandlers = new ArrayBuilder<>(
            Runnable[]::new);
    private boolean parallel;
    private boolean closed;

    public boolean isParallel() {
        return parallel;
    }

    public void setParallel(boolean parallel) {
        this.parallel = parallel;
    }

    public boolean isClosed() {
        return closed;
    }

    public void onClose(Runnable closeHandler) {
        if (closed) {
            throw new IllegalStateException("stream has already been closed");
        }
        closeHandlers.accept(requireNonNull(closeHandler));
    }

    public void close() {
        if (closed) return;
        closed = true;
        Runnable[] handlers = closeHandlers.buildArray();
        for (int i = 0; i < handlers.length; i++) {
            try {
                handlers[i].run();
            } catch (Throwable thrown) {
                for (int j = i + 1; j < handlers.length; j++) {
                    try {
                        handlers[j].run();
                    } catch (Throwable suppressed) {
                        try {
                            thrown.addSuppressed(suppressed);
                        } catch (Throwable ignored) {
                        }
                    }
                }
                throw thrown;
            }
        }
    }
}
