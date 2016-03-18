package seqly;

import java.util.Iterator;
import java.util.Objects;

import static java.util.stream.Collectors.joining;

public abstract class AbstractSeq<E> implements Seq<E> {

    public int hashCode() {
        Iterator<?> iterator = iterator();
        int hash = 1;
        while (iterator.hasNext()) {
            hash *= 31;
            hash += Objects.hashCode(iterator.next());
        }
        return hash;
    }

    public boolean equals(Object object) {
        if (object == this) return true;
        if (!(object instanceof Seq)) return false;
        Seq<?> that = (Seq<?>) object;
        Iterator<?> first = iterator();
        Iterator<?> second = that.iterator();
        while (true) {
            if (!first.hasNext() & !second.hasNext()) {
                return true;
            } else if (!first.hasNext() | !second.hasNext()) {
                return false;
            } else if (!Objects.equals(first.next(), second.next())) {
                return false;
            }
        }
    }

    public String toString() {
        return map(Objects::toString)
                .collect(joining(", ", "[", "]"));
    }
}
