package io.github.jancellor.seq.jackson3;

import io.github.jancellor.seq.Seq;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.CollectionType;

/**
 * Adds support for deserializing {@link Seq} values with Jackson 3, for both
 * JSON and XML.
 *
 * <p>Register with a {@link tools.jackson.databind.json.JsonMapper} builder:
 *
 * <pre>{@code
 * JsonMapper mapper = JsonMapper.builder().addModule(new SeqModule()).build();
 * }</pre>
 *
 * <p>Alternatively, register through the mapper builder's
 * {@code findAndAddModules()} method.
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
            public boolean hasDeserializerFor(DeserializationConfig config,
                    Class<?> type) {
                return type == Seq.class;
            }

            @Override
            public ValueDeserializer<?> findCollectionDeserializer(
                    CollectionType type, DeserializationConfig config,
                    BeanDescription.Supplier beanDescription,
                    TypeDeserializer elementTypeDeserializer,
                    ValueDeserializer<?> elementDeserializer) {
                if (!type.hasRawClass(Seq.class)) {
                    return null;
                }
                return new SeqDeserializer(type, config, elementDeserializer,
                        elementTypeDeserializer);
            }
        });
    }
}
