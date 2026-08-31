package org.bitbucket.seqly;

import java.util.AbstractCollection;
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
                parameters("AbstractSeq defaults", TestDefaultSeq::new),
                parameters("Seq#of(E...)", Seq::of),
                parameters("Seq#copyOf(E[])", Seq::copyOf),
                parameters("Seq#viewOf(E[])", Seq::viewOf),
                parameters("Seq#copyOf(Iterable<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copyOf(Arrays.asList(elements));
                    }
                }),
                parameters("Seq#viewOf(Collection<E>) [non-SIZED]",
                        new Factory() {
                            public <E> Seq<E> create(E[] elements) {
                                return Seq.viewOf(new AbstractCollection<E>() {
                                    public Iterator<E> iterator() {
                                        return Arrays.asList(elements)
                                                .iterator();
                                    }
                                    public int size() {
                                        return elements.length;
                                    }
                                    public Spliterator<E> spliterator() {
                                        return Spliterators
                                                .spliteratorUnknownSize(
                                                        iterator(), ORDERED);
                                    }
                                });
                            }
                        }),
                parameters("Seq#copyOf(Iterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copyOf(Arrays.asList(elements).iterator());
                    }
                }),
                parameters("Seq#copyOf(Spliterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq
                                .copyOf(Arrays.asList(elements).spliterator());
                    }
                }),
                parameters("Seq#copyOf(Stream<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return Seq.copyOf(Arrays.stream(elements));
                    }
                }),
                parameters("SeqStream#of(E...)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.of(elements).toSeq();
                    }
                }),
                parameters("SeqStream#viewOf(Iterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.viewOf(Arrays.asList(elements)
                                .iterator()).toSeq();
                    }
                }),
                parameters("SeqStream#viewOf(Spliterator<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.viewOf(Arrays.asList(elements)
                                .spliterator()).toSeq();
                    }
                }),
                parameters("SeqStream#viewOf(Stream<E>)", new Factory() {
                    public <E> Seq<E> create(E[] elements) {
                        return SeqStream.viewOf(Arrays.stream(elements))
                                .toSeq();
                    }
                }));
    }

    static Object[] parameters(String name, Factory factory) {
        return new Object[]{name, factory};
    }

    class TestDelegatingSeq<E>
            extends
                AbstractSeq<E>
            implements
                DelegatingSeq<E> {

        private final E[] elements;

        @SafeVarargs
        public TestDelegatingSeq(E... elements) {
            this.elements = elements;
        }

        public Spliterator<E> spliterator() {
            return Arrays.spliterator(elements);
        }
    }

    class TestDefaultSeq<E> extends AbstractSeq<E> {

        private final E[] elements;

        @SafeVarargs
        public TestDefaultSeq(E... elements) {
            this.elements = elements;
        }

        public Spliterator<E> spliterator() {
            return Arrays.spliterator(elements);
        }
    }
}
