package org.bitbucket.seqly;

import static org.bitbucket.seqly.SeqTest.assertThrows;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.Test;

public class SeqStreamTest {

    @Test
    public void testAllStreamReturningMethodsReturnSeqStream() {
        for (Method method : SeqStream.class.getMethods()) {
            if (Stream.class.isAssignableFrom(method.getReturnType())
                    && !method.isSynthetic()
                    && !Seq.of("collect", "stream",
                    "takeWhile", "dropWhile") // compiling with -release 8 where these methods don't exist on stream
                    .contains(method.getName())) {
                assertThat(method.getName(), method.getReturnType(),
                        equalTo(SeqStream.class));
            }
        }
    }

    @Test
    public void testRange() {
        assertTrue(SeqStream.range(4, 7).listEquals(streamOf(4, 5, 6)));
        assertTrue(SeqStream.range(7, 4).isEmpty());
        assertTrue(SeqStream.range(-12, -10).listEquals(streamOf(-12, -11)));
    }

    @Test
    public void testConcat() {
        assertTrue(SeqStream.concat().isEmpty());
        assertTrue(
                SeqStream.concat(streamOf(0, 1))
                        .listEquals(streamOf(0, 1)));
        assertTrue(
                SeqStream.concat(streamOf(0, 1), streamOf(2, 3))
                        .listEquals(streamOf(0, 1, 2, 3)));
        assertTrue(
                SeqStream.concat(streamOf(0, 1), streamOf(2, 3), streamOf(4, 5))
                        .listEquals(streamOf(0, 1, 2, 3, 4, 5)));
        assertThrows(() -> SeqStream.concat(streamOf(), null).collect());
        assertThrows(() -> SeqStream.concat(null, streamOf()).collect());
    }

    @Test
    public void testFlatten() {
        assertTrue(SeqStream.flatten(streamOf()).isEmpty());
        assertTrue(
                SeqStream.flatten(streamOf(streamOf(0, 1)))
                        .listEquals(streamOf(0, 1)));
        assertTrue(
                SeqStream.flatten(streamOf(streamOf(0, 1), streamOf(2, 3)))
                        .listEquals(streamOf(0, 1, 2, 3)));
        assertTrue(
                SeqStream.flatten(streamOf(streamOf(0, 1), streamOf(2, 3), streamOf(4, 5)))
                        .listEquals(streamOf(0, 1, 2, 3, 4, 5)));
        assertThrows(() -> SeqStream.flatten(streamOf(streamOf(), null)).collect());
        assertThrows(() -> SeqStream.flatten(streamOf(null, streamOf())).collect());
    }

    @SafeVarargs
    private final <E> SeqStream<E> streamOf(E... elements) {
        return SeqStream.view(Arrays.stream(elements));
    }
}
