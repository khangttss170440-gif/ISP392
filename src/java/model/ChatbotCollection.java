package model;

import java.math.BigDecimal;
import java.util.List;

/** Public collection resolved against currently available product variants. */
public final class ChatbotCollection {
    public final int id;
    public final String title, description, style;
    public final List<ChatbotProduct> items;
    public final BigDecimal fromPrice;
    public ChatbotCollection(OutfitCollection source, List<ChatbotProduct> items) {
        this.id = source.getId(); this.title = source.getTitle();
        this.description = source.getDescription(); this.style = source.getStyle();
        this.items = List.copyOf(items);
        this.fromPrice = items.stream().map(ChatbotProduct::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
