package model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Cart implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<Integer, CartItem> items = new LinkedHashMap<>();

    public void addItem(ProductVariant variant, int quantity) {
        CartItem item = items.get(variant.getVariantID());

        if (item == null) {
            items.put(
                    variant.getVariantID(),
                    new CartItem(variant, quantity)
            );
        } else {
            item.updateVariant(variant);
            item.setQuantity(item.getQuantity() + quantity);
        }
    }

    public void updateItem(ProductVariant variant, int quantity) {
        if (quantity <= 0) {
            removeItem(variant.getVariantID());
            return;
        }

        CartItem item = items.get(variant.getVariantID());

        if (item == null) {
            items.put(
                    variant.getVariantID(),
                    new CartItem(variant, quantity)
            );
        } else {
            item.updateVariant(variant);
            item.setQuantity(quantity);
        }
    }

    public void removeItem(int variantID) {
        items.remove(variantID);
    }

    public void clear() {
        items.clear();
    }

    public CartItem getItem(int variantID) {
        return items.get(variantID);
    }

    public List<CartItem> getItems() {
        return new ArrayList<>(items.values());
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public BigDecimal getSubtotal() {
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : items.values()) {
            total = total.add(item.getLineTotal());
        }

        return total;
    }

    public int getTotalQuantity() {
        int total = 0;

        for (CartItem item : items.values()) {
            total += item.getQuantity();
        }

        return total;
    }
}