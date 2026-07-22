package li.selman.modulithdocsmarkdown.sample.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.order")
public class OrderProperties {

    /**
     * Maximum number of items allowed on a single order.
     */
    private int maxItemsPerOrder = 10;

    public int getMaxItemsPerOrder() {
        return maxItemsPerOrder;
    }

    public void setMaxItemsPerOrder(int maxItemsPerOrder) {
        this.maxItemsPerOrder = maxItemsPerOrder;
    }
}
