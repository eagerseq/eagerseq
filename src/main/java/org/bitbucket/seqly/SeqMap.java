package org.bitbucket.seqly;

import java.util.AbstractMap;
import java.util.Set;

final class SeqMap<K, V> extends AbstractMap<K, V> {

    private final SeqSet<Entry<K, V>> set;

    // caller must ensure precondition of no duplicates
    @SuppressWarnings("unchecked")
    SeqMap(Object[] entries) {
        this.set = new SeqSet<>(entries);
    }

    public Set<Entry<K, V>> entrySet() {
        return set;
    }
}
