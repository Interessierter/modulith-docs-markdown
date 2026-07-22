package li.selman.modulithdocsmarkdown.sample.inventory;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record StockLevelChanged(String sku) {}
