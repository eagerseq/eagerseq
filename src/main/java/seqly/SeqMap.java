package seqly;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;

final class SeqMap<K, V> extends AbstractMap<K, V> {

    private final Set<Entry<K, V>> entrySet;

    SeqMap(
            Seq<?> seq,
            Function<Object, ? extends K> keyMapper,
            Function<Object, ? extends V> valueMapper) {
        this.entrySet = new AbstractSet<Entry<K, V>>() {
            public int size() {
                return seq.size();
            }
            public Iterator<Entry<K, V>> iterator() {
                return Spliterators.iterator(spliterator());
            }
            public Spliterator<Entry<K, V>> spliterator() {
                return new Spliterators.AbstractSpliterator<Entry<K, V>>(Long.MAX_VALUE, 0) {
                    private Spliterator<?> s = seq.spliterator();
                    public boolean tryAdvance(Consumer<? super Entry<K, V>> action) {
                        return s.tryAdvance(e -> action.accept(
                                new AbstractMap.SimpleEntry<>(
                                        keyMapper.apply(e),
                                        valueMapper.apply(e))));
                    }
                };
            }
        };
    }

    public Set<Entry<K, V>> entrySet() {
        return entrySet;
    }
}
