package li.selman.modulithdocsmarkdown.sample.order;

import org.jmolecules.ddd.annotation.AggregateRoot;

@AggregateRoot
public class Order {

    private final OrderId id;
    private int itemCount;

    public Order(OrderId id, int itemCount) {
        this.id = id;
        this.itemCount = itemCount;
    }

    public OrderId getId() {
        return id;
    }

    public int getItemCount() {
        return itemCount;
    }
}
