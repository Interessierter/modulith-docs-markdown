package io.github.haisi.modulith.markdown.sample.order;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.haisi.modulith.markdown.sample.inventory.StockLevelChanged;

@Component
class StockLevelChangedListener {

	@ApplicationModuleListener
	void on(StockLevelChanged event) {
	}
}
