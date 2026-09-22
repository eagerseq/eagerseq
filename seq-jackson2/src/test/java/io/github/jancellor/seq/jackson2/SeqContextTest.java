package io.github.jancellor.seq.jackson2;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.StdConverter;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.github.jancellor.seq.Seq;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;

public class SeqContextTest {
    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new SeqModule()).build();
    @Test
    public void propertyContentDeserializer() throws Exception {
        assertThat(
                mapper.readValue("{\"values\":[\"a\"]}", Custom.class).values,
                contains("A"));
    }
    @Test
    public void xmlPropertyContentDeserializer() throws Exception {
        XmlMapper xml = XmlMapper.builder().addModule(new SeqModule()).build();
        assertThat(xml.readValue(
                "<Custom><values><values>a</values></values></Custom>",
                Custom.class).values, contains("A"));
    }
    @Test
    public void skipsNullContent() throws Exception {
        assertThat(mapper.readValue("{\"values\":[null,\"a\",null]}",
                Skip.class).values, contains("a"));
    }
    @Test
    public void rejectsNullContent() throws Exception {
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> mapper.readValue("{\"values\":[null]}", Fail.class));
    }
    @Test
    public void emptyNullContent() throws Exception {
        assertThat(mapper.readValue("{\"values\":[null,\"a\"]}",
                Empty.class).values, contains("", "a"));
    }
    @Test
    public void polymorphicProperty() throws Exception {
        Animals animals = mapper.readValue(
                "{\"values\":[{\"kind\":\"dog\",\"name\":\"Rex\"}]}",
                Animals.class);
        assertThat(animals.values.getFirst(), instanceOf(Dog.class));
        assertThat(animals.values.getFirst().name, is("Rex"));
    }
    @Test
    public void polymorphicRoot() throws Exception {
        Seq<Animal> animals = mapper.readValue(
                "[{\"kind\":\"dog\",\"name\":\"Rex\"}]",
                new TypeReference<Seq<Animal>>() {
                });
        assertThat(animals.getFirst(), instanceOf(Dog.class));
    }
    @Test
    public void nestedGenericSequences() throws Exception {
        Seq<Seq<Dog>> values = mapper.readValue(
                "[[{\"kind\":\"dog\",\"name\":\"Rex\"}]]",
                new TypeReference<Seq<Seq<Dog>>>() {
                });
        assertThat(values.getFirst().getFirst().name, is("Rex"));
    }
    @Test
    public void sequenceNullAndEmptyPropertyPolicies() throws Exception {
        assertThat(mapper.readValue("{\"values\":null}", Custom.class).values,
                nullValue());
        assertThat(mapper.readValue("{\"values\":[]}", Custom.class).values
                .isEmpty(), is(true));
    }

    @Test
    public void sequenceNullAsEmpty() throws Exception {
        assertThat(mapper.readValue("{\"values\":null}",
                EmptySequence.class).values.isEmpty(), is(true));
    }
    @Test
    public void contentConverter() throws Exception {
        assertThat(mapper.readValue("{\"values\":[12,34]}",
                Converted.class).values, contains("12", "34"));
    }
    @Test
    public void propertyPolymorphicMetadata() throws Exception {
        PropertyAnimals animals = mapper.readValue(
                "{\"values\":[{\"kind\":\"dog\",\"name\":\"Rex\"}]}",
                PropertyAnimals.class);
        assertThat(animals.values.getFirst(), instanceOf(PlainDog.class));
    }
    @Test
    public void registeredElementDeserializer() throws Exception {
        JsonMapper custom = JsonMapper.builder().addModule(new SeqModule())
                .addModule(
                        new SimpleModule()
                                .addDeserializer(String.class, new Upper()))
                .build();
        assertThat(
                custom.readValue("[\"a\"]", new TypeReference<Seq<String>>() {
                }), contains("A"));
    }
    @Test
    public void configuredSingleValueCoercion() throws Exception {
        JsonMapper custom = JsonMapper.builder().addModule(new SeqModule())
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .build();
        assertThat(custom.readValue("\"a\"", new TypeReference<Seq<String>>() {
        }), contains("a"));
    }
    @Test
    public void configuredEmptyStringCoercion() throws Exception {
        JsonMapper custom = JsonMapper.builder().addModule(new SeqModule())
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .build();
        assertThat(custom.readValue("\"\"", new TypeReference<Seq<String>>() {
        }), nullValue());
    }
    @Test
    public void unrelatedCollectionsStillWork() throws Exception {
        assertThat(mapper.readValue("[\"a\"]",
                new TypeReference<List<String>>() {
                }), contains("a"));
    }
    @Test
    public void malformedArrayFails() throws Exception {
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> mapper.readValue("[\"a\",",
                        new TypeReference<Seq<String>>() {
                        }));
    }
    @Test
    public void decodedSequenceRejectsMutation() throws Exception {
        Seq<String> values = mapper.readValue("[\"a\"]",
                new TypeReference<Seq<String>>() {
                });
        assertThrows(UnsupportedOperationException.class,
                () -> values.add("b"));
    }
    @Test
    public void xmlEmptyStringAndNilItemsMatchStandardCollections()
            throws Exception {
        XmlMapper xml = XmlMapper.builder().addModule(new SeqModule()).build();
        String input = "<values xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><item></item><item xsi:nil=\"true\"/><item>last</item></values>";
        Seq<String> values = xml.readValue(input,
                new TypeReference<Seq<String>>() {
                });
        List<String> list = xml.readValue(input,
                new TypeReference<List<String>>() {
                });
        String[] array = xml.readValue(input, String[].class);
        java.util.Set<String> set = xml.readValue(input,
                new TypeReference<java.util.Set<String>>() {
                });
        assertThat(list, contains("", null, "last"));
        assertThat(values, contains("", null, "last"));
        assertThat(values, is(Seq.copyOf(list)));
        assertThat(values, is(Seq.viewOf(array)));
        assertThat(set, is(new java.util.HashSet<>(list)));
    }

    @Test
    public void arrayDecodingDoesNotExposeAnAccumulatingCollection()
            throws Exception {
        InspectingElement decoder = new InspectingElement();
        JsonMapper custom = JsonMapper.builder().addModule(new SeqModule())
                .addModule(
                        new SimpleModule()
                                .addDeserializer(String.class, decoder))
                .build();
        Seq<String> values = custom.readValue("[\"a\"]",
                new TypeReference<Seq<String>>() {
                });
        assertThat(decoder.currentValue, nullValue());
        assertThat(values, contains("a"));
    }

    @Test
    public void laterDecodingDoesNotReuseTheReturnedArray() throws Exception {
        Seq<String> first = mapper.readValue("[\"first\",\"second\"]",
                new TypeReference<Seq<String>>() {
                });
        Seq<String> second = mapper.readValue("[\"other\"]",
                new TypeReference<Seq<String>>() {
                });
        assertThat(first, contains("first", "second"));
        assertThat(first.get(1), is("second"));
        assertThat(second, contains("other"));
    }

    @Test
    public void managedReferenceRestoresParentForEachChild() throws Exception {
        Parent parent = mapper.readValue(
                "{\"children\":[{\"name\":\"first\"},null,{\"name\":\"last\"}]}",
                Parent.class);
        assertThat(parent.children.size(), is(3));
        assertThat(parent.children.get(0).name, is("first"));
        assertThat(parent.children.get(0).parent, sameInstance(parent));
        assertThat(parent.children.get(1), nullValue());
        assertThat(parent.children.get(2).name, is("last"));
        assertThat(parent.children.get(2).parent, sameInstance(parent));
    }

    public static class Parent {
        @JsonManagedReference
        public Seq<Child> children;
    }

    public static class Child {
        @JsonBackReference
        public Parent parent;
        public String name;
    }

    public static class EmptySequence {
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public Seq<String> values;
    }

    public static class Converted {
        @JsonDeserialize(contentConverter = NumberToString.class)
        public Seq<String> values;
    }

    public static class NumberToString extends
            StdConverter<Integer, String> {

        @Override
        public String convert(Integer value) {
            return value.toString();
        }
    }

    public static abstract class PlainAnimal {
        public String name;
    }

    public static class PlainDog extends PlainAnimal {
    }

    public static class PropertyAnimals {
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
        @JsonSubTypes(@JsonSubTypes.Type(value = PlainDog.class, name = "dog"))
        public Seq<PlainAnimal> values;
    }

    public static class InspectingElement extends JsonDeserializer<String> {
        Object currentValue = new Object();

        @Override
        public String deserialize(JsonParser parser,
                DeserializationContext context) throws IOException {
            currentValue = parser.currentValue();
            return parser.getText();
        }
    }

    public static class Upper extends JsonDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser,
                DeserializationContext context) throws IOException {
            return parser.getText().toUpperCase(Locale.ROOT);
        }
    }

    public static class Custom {
        @JsonDeserialize(contentUsing = Upper.class)
        public Seq<String> values;
    }

    public static class Skip {
        @JsonSetter(contentNulls = Nulls.SKIP)
        public Seq<String> values;
    }

    public static class Fail {
        @JsonSetter(contentNulls = Nulls.FAIL)
        public Seq<String> values;
    }

    public static class Empty {
        @JsonSetter(contentNulls = Nulls.AS_EMPTY)
        public Seq<String> values;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes(@JsonSubTypes.Type(value = Dog.class, name = "dog"))
    public static abstract class Animal {
        public String name;
    }

    public static class Dog extends Animal {
    }

    public static class Animals {
        public Seq<Animal> values;
    }
}
