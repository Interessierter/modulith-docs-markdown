package li.selman.modulithdocsmarkdown.sample.order;

import li.selman.modulithdocsmarkdown.sample.inventory.StockLevelChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class StockLevelChangedListener {

    @ApplicationModuleListener
    void on(StockLevelChanged event) {}
}
