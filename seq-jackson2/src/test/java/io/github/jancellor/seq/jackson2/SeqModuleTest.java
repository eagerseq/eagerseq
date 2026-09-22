package io.github.jancellor.seq.jackson2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import io.github.jancellor.seq.Seq;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

public class SeqModuleTest {
    private static final TypeReference<Seq<String>> STRINGS = new TypeReference<Seq<String>>() {
    };

    @Test
    public void jsonUsesDefaultCollectionSerializationAndExplicitRegistration()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new SeqModule());

        assertThat(mapper.writeValueAsString(Seq.of("first", null, "last")),
                is("[\"first\",null,\"last\"]"));
        assertThat(mapper.readValue("[\"first\",null,\"last\"]", STRINGS),
                contains("first", null, "last"));
        assertThat(mapper.readValue("[]", STRINGS).isEmpty(), is(true));
        assertThat(mapper.readValue("null", STRINGS), nullValue());
    }

    @Test
    public void jsonPreservesPropertyElementTypeAndRejectsNonArrays()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new SeqModule());

        People people = mapper.readValue("{\"people\":[{\"name\":\"Ada\"}]}",
                People.class);
        assertThat(people.people.getFirst(), instanceOf(Person.class));
        assertThat(people.people.getFirst().name, is("Ada"));

        Seq<?> untyped = mapper.readValue("[{\"name\":\"Ada\"}]", Seq.class);
        assertThat(untyped.getFirst(), instanceOf(Map.class));
        assertThrows(JsonMappingException.class,
                () -> mapper.readValue("42", STRINGS));
        assertThrows(JsonMappingException.class,
                () -> mapper.readValue("{}", STRINGS));
    }

    @Test
    public void jsonModuleIsDiscoverable() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        assertThat(mapper.readValue("[\"discovered\"]", STRINGS),
                contains("discovered"));
    }

    @Test
    public void xmlUsesDefaultCollectionSerializationAndTypedDeserialization()
            throws Exception {
        XmlMapper mapper = XmlMapper.builder().addModule(new SeqModule())
                .build();

        String xml = mapper.writerFor(STRINGS)
                .writeValueAsString(Seq.of("first", null, "last"));
        assertThat(xml,
                is("<Seq><item>first</item><item/><item>last</item></Seq>"));
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> mapper.readValue("<Seq/>", STRINGS));
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> mapper.readValue("<List/>",
                        new TypeReference<java.util.List<String>>() {
                        }));
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> mapper.readValue("<Set/>",
                        new TypeReference<java.util.Set<String>>() {
                        }));

        assertThat(mapper.readValue(
                "<Seq xsi:nil=\"true\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>",
                STRINGS), nullValue());
    }

    @Test
    public void xmlPreservesPropertyElementTypeAndRejectsNonArrays()
            throws Exception {
        XmlMapper mapper = (XmlMapper) new XmlMapper()
                .registerModule(new SeqModule());

        People people = mapper.readValue(
                "<People><people><person><name>Ada</name></person></people></People>",
                People.class);
        assertThat(people.people.getFirst(), instanceOf(Person.class));
        assertThat(people.people.getFirst().name, is("Ada"));
        assertThrows(JsonMappingException.class,
                () -> mapper.readValue("<Seq>text</Seq>", STRINGS));
    }

    @Test
    public void xmlModuleIsDiscoverable() throws Exception {
        XmlMapper mapper = (XmlMapper) new XmlMapper().findAndRegisterModules();

        assertThat(
                mapper.readValue("<Seq><item>discovered</item></Seq>", STRINGS),
                contains("discovered"));
    }

    public static final class People {
        @JacksonXmlElementWrapper(localName = "people")
        @JacksonXmlProperty(localName = "person")
        public Seq<Person> people;
    }

    public static final class Person {
        public String name;
    }
}
