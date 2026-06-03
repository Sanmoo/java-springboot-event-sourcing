package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitChanged;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorized;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseCaptured;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorizationReleased;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PaymentReceived;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventTypeMapper {

    private static final Map<String, Class<? extends CreditAccountEvent>> TYPE_TO_CLASS = Map.of(
            "CreditAccountOpened", CreditAccountOpened.class,
            "CreditLimitAssigned", CreditLimitAssigned.class,
            "CreditLimitChanged", CreditLimitChanged.class,
            "PurchaseAuthorized", PurchaseAuthorized.class,
            "PurchaseCaptured", PurchaseCaptured.class,
            "PurchaseAuthorizationReleased", PurchaseAuthorizationReleased.class,
            "PaymentReceived", PaymentReceived.class
    );

    private static final Map<Class<? extends CreditAccountEvent>, String> CLASS_TO_TYPE;

    static {
        var builder = new java.util.LinkedHashMap<Class<? extends CreditAccountEvent>, String>();
        TYPE_TO_CLASS.forEach((type, clazz) -> builder.put(clazz, type));
        CLASS_TO_TYPE = Map.copyOf(builder);
    }

    private final ObjectMapper objectMapper;

    public EventTypeMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String eventType(CreditAccountEvent event) {
        String type = CLASS_TO_TYPE.get(event.getClass());
        if (type == null) {
            throw new IllegalArgumentException("Unknown event type: " + event.getClass());
        }
        return type;
    }

    public Class<? extends CreditAccountEvent> classForType(String eventType) {
        Class<? extends CreditAccountEvent> clazz = TYPE_TO_CLASS.get(eventType);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown event type: " + eventType);
        }
        return clazz;
    }

    public String serialize(CreditAccountEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize event: " + event, e);
        }
    }

    public CreditAccountEvent deserialize(String eventType, String payload) {
        Class<? extends CreditAccountEvent> clazz = classForType(eventType);
        try {
            return objectMapper.readValue(payload, clazz);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to deserialize event type " + eventType + ": " + payload, e);
        }
    }

    public String serializeMetadata(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize metadata", e);
        }
    }

    public Map<String, String> deserializeMetadata(String metadataJson) {
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to deserialize metadata", e);
        }
    }
}
