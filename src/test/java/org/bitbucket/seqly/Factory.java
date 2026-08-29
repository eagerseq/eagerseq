package org.bitbucket.seqly;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;

import static java.util.Spliterator.ORDERED;

/**
 * Creates a {@code Seq} from an array, once for each way the library offers
 * of building one. Shared by the tests that run against every implementation.
 */
interface Factory {

    <E> Seq<E> create(E[] elements);

    /** Every factory, as JUnit {@code Parameterized} arguments. */
    static Iterable<Object[]> all() {
        return Seq.of(
                parameters("DelegatingSeq", TestDelegatingSeq::new),
                parameters("Seq#of(E...)", Seq::of),
                parameters("Seq#copy(E[])", Seq::copy),
                parameters("Seq#view(E[])", Seq::view),
                parameters("Seq#copy(Iterable<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Arrays.asList(elements));
                    }
                }),
                parameters("Seq#view(Iterable<E>) [non-SIZED]", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.view(new Iterable<E>() {
                            public Iterator<E> iterator() {
                                return Arrays.asList(elements).iterator();
                            }
                            public Spliterator<E> spliterator() {
                                return Spliterators.spliteratorUnknownSize(
                                        iterator(), ORDERED);
                            }
                        });
                    }
                }),
                parameters("Seq#copy(Iterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Arrays.asList(elements).iterator());
                    }
                }),
                parameters("Seq#copy(Spliterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Arrays.asList(elements).spliterator());
                    }
                }),
                parameters("Seq#copy(Stream<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copy(Arrays.stream(elements));
                    }
                }),
                parameters("SeqStream#of(E...)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.of(elements).toSeq();
                    }
                }),
                parameters("SeqStream#view(Iterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.view(Arrays.asList(elements)
                                .iterator()).toSeq();
                    }
                }),
                parameters("SeqStream#view(Spliterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.view(Arrays.asList(elements)
                                .spliterator()).toSeq();
                    }
                }),
                parameters("SeqStream#view(Stream<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.view(Arrays.stream(elements))
                                .toSeq();
                    }
                }));
    }

    static Object[] parameters(String name, Factory factory) {
        return new Object[]{name, factory};
    }

    class TestDelegatingSeq<E>
            extends AbstractSeq<E> implements DelegatingSeq<E> {

        private final E[] elements;

        @SafeVarargs
        public TestDelegatingSeq(E... elements) {
            this.elements = elements;
        }

        public Spliterator<E> spliterator() {
            return Arrays.spliterator(elements);
        }
    }
}
