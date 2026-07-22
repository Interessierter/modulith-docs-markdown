package li.selman.modulithdocsmarkdown.sample.inventory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class DefaultInventoryLookup implements InventoryLookup {

    private final ApplicationEventPublisher events;

    DefaultInventoryLookup(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public boolean isAvailable(String sku) {
        return true;
    }

    void deplete(String sku) {
        events.publishEvent(new StockLevelChanged(sku));
    }
}
