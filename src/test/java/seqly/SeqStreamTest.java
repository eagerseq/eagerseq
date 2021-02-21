package seqly;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.lang.reflect.Method;
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
}
