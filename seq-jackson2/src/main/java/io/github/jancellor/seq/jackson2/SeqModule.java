package io.github.jancellor.seq.jackson2;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.CollectionType;
import io.github.jancellor.seq.Seq;

/**
 * Adds support for deserializing {@link Seq} values with Jackson 2, for both
 * JSON and XML.
 *
 * <p>Register with an {@link com.fasterxml.jackson.databind.ObjectMapper}:
 *
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper().registerModule(new SeqModule());
 * }</pre>
 *
 * <p>Alternatively, register through the mapper's
 * {@code findAndRegisterModules()} method.
 */
public final class SeqModule extends SimpleModule {
    private static final long serialVersionUID = 1L;

    /** Creates a module for deserializing {@code Seq} values. */
    public SeqModule() {
        super("Seq");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addDeserializers(new Deserializers.Base() {

            @Override
            public JsonDeserializer<?> findCollectionDeserializer(
                    CollectionType type, DeserializationConfig config,
                    BeanDescription beanDescription,
                    TypeDeserializer elementTypeDeserializer,
                    JsonDeserializer<?> elementDeserializer) {
                if (!type.hasRawClass(Seq.class)) {
                    return null;
                }
                return new SeqDeserializer(type, config, elementDeserializer,
                        elementTypeDeserializer);
            }
        });
    }
}
