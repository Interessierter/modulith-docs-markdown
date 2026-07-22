package io.github.haisi.modulith.markdown.sample.order;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record OrderPlaced(OrderId orderId) {
}
