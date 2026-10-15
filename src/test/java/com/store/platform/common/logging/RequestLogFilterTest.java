package com.store.platform.common.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestLogFilterTest {

    @Test
    void redactMasksSecretsButKeepsOtherFields() {
        String json = "{\"email\":\"a@b.c\",\"password\":\"p\\\"w\",\"currentPassword\":\"x\","
                + "\"token\":\"jwt\",\"tokenCost\":3,\"name\":\"Ana\"}";
        assertEquals("{\"email\":\"a@b.c\",\"password\":\"***\",\"currentPassword\":\"***\","
                + "\"token\":\"***\",\"tokenCost\":3,\"name\":\"Ana\"}", RequestLogFilter.redact(json));
    }
}
