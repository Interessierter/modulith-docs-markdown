package li.selman.modulithdocsmarkdown.sample.order;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record OrderPlaced(OrderId orderId) {}
