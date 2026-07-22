package li.selman.modulithdocsmarkdown.sample.order;

import li.selman.modulithdocsmarkdown.sample.inventory.InventoryLookup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

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
