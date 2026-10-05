package com.store.platform.order;

import java.math.BigDecimal;

/** Immutable snapshot of an extra charged on an order item (survives catalog edits/deletes). */
public record ItemExtraSnapshot(String name, BigDecimal price, int quantity) {
}
