package io.github.haisi.modulith.markdown.sample.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import io.github.haisi.modulith.markdown.sample.inventory.InventoryLookup;

@Service
public class OrderService {

	private final InventoryLookup inventoryLookup;
	private final ApplicationEventPublisher events;

	public OrderService(InventoryLookup inventoryLookup, ApplicationEventPublisher events) {
		this.inventoryLookup = inventoryLookup;
		this.events = events;
	}

	public Order place(OrderId orderId, String sku, int itemCount) {

		if (!inventoryLookup.isAvailable(sku)) {
			throw new IllegalStateException("Not available: " + sku);
		}

		var order = new Order(orderId, itemCount);

		events.publishEvent(new OrderPlaced(orderId));

		return order;
	}
}
