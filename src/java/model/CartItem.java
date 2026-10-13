package model;

import java.io.Serializable;
import java.math.BigDecimal;

public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private int variantID;
    private int productID;
    private String sku;
    private String productName;
    private String brand;
    private String color;
    private String size;
    private BigDecimal unitPrice;
    private int quantity;

    public CartItem(ProductVariant variant, int quantity) {
        updateVariant(variant);
        this.quantity = quantity;
    }

    public void updateVariant(ProductVariant variant) {
        this.variantID = variant.getVariantID();
        this.productID = variant.getProductID();
        this.sku = variant.getSku();
        this.productName = variant.getProductName();
        this.brand = variant.getBrand();
        this.color = variant.getColor();
        this.size = variant.getSize();
        this.unitPrice = variant.getPrice();
    }

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public int getVariantID() {
        return variantID;
    }

    public int getProductID() {
        return productID;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public String getBrand() {
        return brand;
    }

    public String getColor() {
        return color;
    }

    public String getSize() {
        return size;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}