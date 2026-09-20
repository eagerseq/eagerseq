# EagerSeq

*EagerSeq* puts common operations directly on an ordered collection. `Seq`
extends Java's `Collection` with eager versions of stream methods like `map`,
`filter` and `sorted`. It also brings together operations such as `groupBy`,
`distinctBy`, `zip` and `windowFixed`, otherwise spread across the standard Java
library and third-party libraries.

```java
Seq<String> words = Seq.of("pear", "apple", "plum");
Seq<Integer> lengths = words.map(String::length);
```

With JDK streams, you first create a stream, then collect the result back into a
collection:

```java
List<String> words = List.of("pear", "apple", "plum");
List<Integer> lengths = words.stream()
        .map(String::length)
        .collect(toList());
```

Transforming one collection into another is common, and laziness isn't always
needed. `Seq` expresses these operations directly, without setting up and
collecting a stream. Each transformation completes immediately and returns a
sequence ready to use. When you want lazy composition, `stream()` provides it
while retaining the additional collection operations.

## Collection operations

Methods live on the collection, so you can discover and combine them without
switching between streams, collectors, and utility classes.

```java
// Transform and summarize
tracks.filter(track -> track.getDurationSeconds() < 180);
playlists.flatMap(Playlist::getTracks);
tracks.sumOfInt(Track::getDurationSeconds);
ratings.frequency(5);

// Organize by a property
photos.groupBy(Photo::getLocation);
tracks.distinctBy(Track::getArtist);
files.partitionBy(File::isDirectory);

// Select and reorder
matches.getOnly();
photos.slice(10, 20);
events.limitLast(10);
photos.reversed();
tracks.shuffled(new Random());
tracks.mapIndexed((i, track) -> (i + 1) + ". " + track.getTitle());

// Combine and compare
names.zip(scores, (name, score) -> name + ": " + score);
tags.intersection(selectedTags);
tracks.listEquals(otherPlaylist);
players.combinations(2);

// Batch and accumulate
files.windowFixed(100);
readings.windowSliding(3);
roundScores.scan(() -> 0, Integer::sum); // Total score after each round
```

## Creating and using sequences

Create sequences from values, existing data, or a live view of an array or
ordered collection:

```java
Seq.of("pear", "apple", "plum");
Seq.copyOf(list);
Seq.copyOf(array);
Seq.copyOf(iterator);
Seq.copyOf(stream);
Seq.copyOf(optional);
Seq.viewOf(list);
Seq.viewOf(array);
Seq.range(0, 10);
Seq.repeat("—", 5);
```

A `Seq` is a `Collection`. Convert it to other types when needed:

```java
words.toList();
words.toSet();
words.toArray(String[]::new);
users.toMap(User::getId);
matches.toOptional();
words.toString(", ", "[", "]");
```

Collection mutators such as `add` and `remove` throw
`UnsupportedOperationException`. `copyOf` and transformations such as `map`,
`filter`, and `reversed` produce shallow snapshots. The lists, sets, and maps
returned by conversions are unmodifiable; sets and maps preserve encounter
order.

Sequence equality compares elements in order and only considers other `Seq`
instances equal. Use `listEquals`, `setEquals`, or `multisetEquals` to compare
with other iterables under the corresponding equality rule. Ordinary
materialized sequences support constant-time `get` and `size`; collection views
delegate size queries and use linear-time indexing.

## Lazy composition when needed

`stream()` returns a `SeqStream`, which extends JDK `Stream` with the library's
additional operations. Use `toSeq()` to materialize the result:

```java
Seq<String> numbered = words.stream()
        .distinctBy(String::length)
        .reversed()
        .mapIndexed((index, word) -> (index + 1) + ": " + word)
        .toSeq();
// [1: apple, 2: pear]
```

A `Seq` is reusable; like any JDK `Stream`, a `SeqStream` is single-use.

`SeqStream` evaluates its own operations sequentially, even in parallel mode.
Use `toStream()` for JDK parallel evaluation.

## Installation

EagerSeq works with Java 8 and newer and has no runtime dependencies.

### Maven

```xml
<dependency>
    <groupId>io.github.eagerseq</groupId>
    <artifactId>eagerseq</artifactId>
    <version>x.y.z</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.eagerseq:eagerseq:x.y.z'
```

## Further reading

- API documentation in the source:
  [Seq](src/main/java/io/github/eagerseq/Seq.java) and
  [SeqStream](src/main/java/io/github/eagerseq/SeqStream.java).
- [Contributor guide](CONTRIBUTING.md): architecture, custom implementations,
  and development workflow.
