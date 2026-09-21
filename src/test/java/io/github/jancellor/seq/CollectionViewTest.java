package io.github.jancellor.seq;

import org.junit.Test;

import java.util.stream.LongStream;

import static org.junit.Assert.assertEquals;

public class CollectionViewTest {
    @Test
    public void testCountDoesNotTruncateLargeCollectionViews() {
        for (long length : new long[]{0, 3, Integer.MAX_VALUE,
                (long) Integer.MAX_VALUE + 2}) {
            Seq<Long> original = new AbstractSeq<Long>() {
                public Source<Long> spliterator() {
                    return length == 0 ? Seq.<Long>of().spliterator()
                            : Source.viewOf(LongStream.range(0, length).boxed()
                                    .spliterator());
                }
            };
            Seq<Long> view = Seq.viewOf(original);
            assertEquals(Math.min(length, Integer.MAX_VALUE), view.size());
            assertEquals(length, original.count());
            assertEquals(length, view.count());
            assertEquals(length, view.stream().count());
        }
    }
}
