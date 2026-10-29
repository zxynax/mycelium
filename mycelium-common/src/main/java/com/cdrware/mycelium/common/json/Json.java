package com.cdrware.mycelium.common.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** Stable mapper for signing: sorted keys, no pretty print. */
    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static ObjectMapper canonical() {
        return CANONICAL;
    }
}
