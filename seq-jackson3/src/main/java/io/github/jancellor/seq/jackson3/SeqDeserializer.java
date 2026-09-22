package io.github.jancellor.seq.jackson3;

import io.github.jancellor.seq.Seq;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.type.ArrayType;
import tools.jackson.databind.type.CollectionType;

final class SeqDeserializer extends StdDeserializer<Seq<?>> {
    private final ArrayType arrayType;
    private final ValueDeserializer<?> delegate;

    SeqDeserializer(CollectionType type, DeserializationConfig config,
            ValueDeserializer<?> elementDeserializer,
            TypeDeserializer elementTypeDeserializer) {
        this(config.getTypeFactory().constructArrayType(type.getContentType())
                .withContentTypeHandler(elementTypeDeserializer)
                .withContentValueHandler(elementDeserializer), null);
    }

    private SeqDeserializer(ArrayType arrayType,
            ValueDeserializer<?> delegate) {
        super(Seq.class);
        this.arrayType = arrayType;
        this.delegate = delegate;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext context,
            BeanProperty property) {
        return new SeqDeserializer(arrayType,
                context.findContextualValueDeserializer(arrayType, property));
    }

    @Override
    public Seq<?> deserialize(JsonParser parser, DeserializationContext context)
            throws JacksonException {
        Object[] values = (Object[]) delegate.deserialize(parser, context);
        // Wrap Jackson's completed array without copying; the result retains
        // array-backed indexing without an intermediate collection.
        return values == null ? null : Seq.viewOf(values);
    }

    @Override
    public Seq<?> getEmptyValue(DeserializationContext context) {
        return Seq.viewOf((Object[]) delegate.getEmptyValue(context));
    }

    @Override
    public SettableBeanProperty findBackReference(String referenceName) {
        return delegate.findBackReference(referenceName);
    }
}
