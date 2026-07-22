package li.selman.modulithdocsmarkdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleFactsTest {

    private final ApplicationModules modules = SampleModel.get().verify();

    @Test
    void extractsFactsForOrderModule() {

        var order = modules.getModuleByName("order").orElseThrow();
        var facts = ModuleFacts.from(order, modules);

        assertThat(facts.identifier()).isEqualTo("order");
        assertThat(facts.displayName()).isEqualTo("Order");
        assertThat(facts.basePackage()).isEqualTo("li.selman.modulithdocsmarkdown.sample.order");
        assertThat(facts.springBeans()).isNotEmpty();
        assertThat(facts.beanReferences()).contains("InventoryLookup");
        assertThat(facts.aggregateRoots()).containsExactly("Order");
        assertThat(facts.valueTypes()).containsExactly("OrderId");
        assertThat(facts.publishedEvents()).containsExactly("OrderPlaced");
        assertThat(facts.eventsListenedTo()).containsExactly("StockLevelChanged");
        assertThat(facts.configProperties()).containsExactly("app.order.max-items-per-order");
    }

    @Test
    void extractsFactsForInventoryModule() {

        var inventory = modules.getModuleByName("inventory").orElseThrow();
        var facts = ModuleFacts.from(inventory, modules);

        assertThat(facts.identifier()).isEqualTo("inventory");
        assertThat(facts.springBeans()).contains("DefaultInventoryLookup");
        assertThat(facts.publishedEvents()).containsExactly("StockLevelChanged");
        assertThat(facts.eventsListenedTo()).isEmpty();
        assertThat(facts.aggregateRoots()).isEmpty();
    }

    @Test
    void allTokensUnionsEveryFactSetExceptDescription() {

        var order = modules.getModuleByName("order").orElseThrow();
        var facts = ModuleFacts.from(order, modules);

        assertThat(facts.allTokens())
                .contains(facts.basePackage())
                .containsAll(facts.springBeans())
                .containsAll(facts.beanReferences())
                .containsAll(facts.aggregateRoots())
                .containsAll(facts.valueTypes())
                .containsAll(facts.publishedEvents())
                .containsAll(facts.eventsListenedTo())
                .containsAll(facts.configProperties());
    }
}
