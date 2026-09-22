package io.github.jancellor.seq.jackson2;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.ArrayType;
import com.fasterxml.jackson.databind.type.CollectionType;
import io.github.jancellor.seq.Seq;

import java.io.IOException;

final class SeqDeserializer
        extends StdDeserializer<Seq<?>> implements ContextualDeserializer {
    private static final long serialVersionUID = 1L;
    private final ArrayType arrayType;
    private final JsonDeserializer<?> delegate;

    SeqDeserializer(CollectionType type, DeserializationConfig config,
            JsonDeserializer<?> elementDeserializer,
            TypeDeserializer elementTypeDeserializer) {
        this(config.getTypeFactory().constructArrayType(type.getContentType())
                .withContentTypeHandler(elementTypeDeserializer)
                .withContentValueHandler(elementDeserializer), null);
    }

    private SeqDeserializer(ArrayType arrayType, JsonDeserializer<?> delegate) {
        super(Seq.class);
        this.arrayType = arrayType;
        this.delegate = delegate;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context,
            BeanProperty property) throws JsonMappingException {
        return new SeqDeserializer(arrayType,
                context.findContextualValueDeserializer(arrayType, property));
    }

    @Override
    public Seq<?> deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        Object[] values = (Object[]) delegate.deserialize(parser, context);
        // Wrap Jackson's completed array without copying; the result retains
        // array-backed indexing without an intermediate collection.
        return values == null ? null : Seq.viewOf(values);
    }

    @Override
    public Seq<?> getEmptyValue(DeserializationContext context)
            throws JsonMappingException {
        return Seq.viewOf((Object[]) delegate.getEmptyValue(context));
    }

    @Override
    public SettableBeanProperty findBackReference(String referenceName) {
        return delegate.findBackReference(referenceName);
    }
}
