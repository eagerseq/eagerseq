package org.bitbucket.seqly;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

public class ApiShapeTest {

    @Test
    public void testAllSeqMethodsHaveTests() {
        Set<String> testMethods = testMethods();
        Set<String> untestedMethods = new TreeSet<>();
        for (Method method : Seq.class.getMethods()) {
            if (!testMethods.contains(method.getName().toLowerCase())) {
                untestedMethods.add(method.getName());
            }
        }
        assertThat("Seq methods without tests", untestedMethods, empty());
    }

    @Test
    public void testAllSeqStreamMethodsHaveTests() {
        Set<String> testMethods = testMethods();
        Set<String> untestedMethods = new TreeSet<>();
        for (Method method : SeqStream.class.getDeclaredMethods()) {
            if (!method.isSynthetic()
                    && !testMethods.contains(method.getName().toLowerCase())) {
                untestedMethods.add(method.getName());
            }
        }
        assertThat("SeqStream methods without tests", untestedMethods, empty());
    }

    private static Set<String> testMethods() {
        Set<String> testMethods = new HashSet<>();
        for (Class<?> testClass : Arrays.asList(
                SeqTest.class, SeqStreamTest.class)) {
            for (Method method : testClass.getDeclaredMethods()) {
                if (method.getAnnotation(Test.class) != null) {
                    testMethods.add(method.getName().toLowerCase()
                            .replaceFirst("test", ""));
                }
            }
        }
        return testMethods;
    }

    @Test
    public void testAllSeqReturningMethodsReturnViews() {
        for (Method method : Seq.class.getMethods()) {
            if (Seq.class.isAssignableFrom(method.getReturnType())
                    && !method.isSynthetic()
                    && !method.getName().equals("stream")
                    && !method.getName().equals("parallelStream")) {
                assertThat(method.getName(), method.getReturnType(),
                        equalTo(Seq.class));
            }
        }
    }

    @Test
    public void testAllStreamReturningMethodsReturnSeqStream() {
        for (Method method : SeqStream.class.getMethods()) {
            if (Stream.class.isAssignableFrom(method.getReturnType())
                    && !method.isSynthetic()
                    && !Seq.of("takeWhile", "dropWhile", "mapMulti")
                            // compiling with -release 8, so these are declared by
                            // SeqStream without overriding the Stream method of the
                            // same name, which is therefore also reported here
                            .contains(method.getName())
                    // Gatherer does not exist before Java 24, so gather
                    // cannot be declared at all while targeting Java 8
                    && !method.getName().equals("gather")) {
                assertThat(method.getName(), method.getReturnType(),
                        equalTo(SeqStream.class));
            }
        }
    }

    /**
     * Detects methods added to {@code Stream} by later Java versions that
     * have not yet been considered for {@code Seq}. When this fails, either
     * add the method to {@code Seq} or add it below with a reason.
     */
    @Test
    public void testStreamMethodsAbsentFromSeqAreOnlyTheKnownExceptions() {
        Set<String> declaredBySeq = new HashSet<>();
        for (Method method : Seq.class.getDeclaredMethods()) {
            declaredBySeq.add(method.getName());
        }
        Set<String> absent = new TreeSet<>();
        for (Method method : Stream.class.getDeclaredMethods()) {
            if (!method.isSynthetic()
                    && !Modifier.isStatic(method.getModifiers())
                    && !declaredBySeq.contains(method.getName())) {
                absent.add(method.getName());
            }
        }
        absent.removeAll(Arrays.asList(
                // Seq is an object collection and defines no primitive
                // stream methods at all, including mapToInt and flatMapToInt
                "mapMultiToInt", "mapMultiToLong", "mapMultiToDouble",
                "mapToInt", "mapToLong", "mapToDouble",
                "flatMapToInt", "flatMapToLong", "flatMapToDouble",
                // Gatherer does not exist before Java 24
                "gather",
                // exists only to let a parallel pipeline ignore encounter
                // order; a Seq is eager and sequential, so it would be a
                // synonym for findFirst promising less
                "findAny"));
        assertThat("Stream methods absent from Seq without a stated reason",
                absent, empty());
    }

    /**
     * Detects methods added to {@code Seq} that have no {@code SeqStream}
     * equivalent. When this fails, either add the method to
     * {@code SeqStream} or add it below with a reason.
     */
    @Test
    public void testSeqMethodsAbsentFromSeqStreamAreOnlyTheKnownExceptions() {
        Set<String> declaredBySeqStream = new HashSet<>();
        for (Method method : SeqStream.class.getMethods()) {
            declaredBySeqStream.add(method.getName());
        }
        Set<String> absent = new TreeSet<>();
        for (Method method : Seq.class.getMethods()) {
            if (!method.isSynthetic()
                    && !Modifier.isStatic(method.getModifiers())
                    && !declaredBySeqStream.contains(method.getName())) {
                absent.add(method.getName());
            }
        }
        absent.removeAll(Arrays.asList(
                // Collection mutators, which Seq inherits only to throw
                "add", "addAll", "clear", "remove", "removeAll", "removeIf",
                "retainAll",
                // a SeqStream is consumed by any traversal, so it cannot
                // define equality by its elements
                "equals", "hashCode",
                // a SeqStream is already a stream
                "stream", "parallelStream"));
        assertThat("Seq methods absent from SeqStream without a stated reason",
                absent, empty());
    }
}
