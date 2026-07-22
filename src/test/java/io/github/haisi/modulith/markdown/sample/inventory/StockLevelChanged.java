package io.github.haisi.modulith.markdown.sample.inventory;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record StockLevelChanged(String sku) {
}
