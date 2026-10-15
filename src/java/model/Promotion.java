package model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;

public class Promotion {

    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        if (subtotal == null || subtotal.signum() < 0) {
            throw new IllegalArgumentException("Tổng tiền không hợp lệ.");
        }
        if (minOrderValue != null && subtotal.compareTo(minOrderValue) < 0) {
            throw new IllegalArgumentException("Đơn hàng chưa đạt giá trị tối thiểu của mã khuyến mãi.");
        }
        if (discountValue == null || discountValue.signum() <= 0) {
            throw new IllegalArgumentException("Giá trị khuyến mãi không hợp lệ.");
        }
        BigDecimal discount;
        if ("Percentage".equals(discountType) && discountValue.compareTo(new BigDecimal("100")) <= 0) {
            discount = subtotal.multiply(discountValue).divide(new BigDecimal("100"));
        } else if ("Fixed".equals(discountType)) {
            discount = discountValue;
        } else {
            throw new IllegalArgumentException("Loại khuyến mãi không hợp lệ.");
        }
        if (maxDiscount != null) {
            discount = discount.min(maxDiscount);
        }
        return discount.max(BigDecimal.ZERO).min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    private int promotionID;
    private String promotionCode;
    private String promotionName;
    private String discountType;
    private BigDecimal discountValue;
    private Timestamp startDate;
    private Timestamp endDate;
    private BigDecimal minOrderValue;
    private BigDecimal maxDiscount;
    private String status;
    private int quantity;
    private int originalQuantity;

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getOriginalQuantity() { return originalQuantity; }
    public void setOriginalQuantity(int originalQuantity) { this.originalQuantity = originalQuantity; }

    public int getPromotionID() {
        return promotionID;
    }

    public void setPromotionID(int promotionID) {
        this.promotionID = promotionID;
    }

    public String getPromotionCode() {
        return promotionCode;
    }

    public void setPromotionCode(String promotionCode) {
        this.promotionCode = promotionCode;
    }

    public String getPromotionName() {
        return promotionName;
    }

    public void setPromotionName(String promotionName) {
        this.promotionName = promotionName;
    }

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public Timestamp getStartDate() {
        return startDate;
    }

    public void setStartDate(Timestamp startDate) {
        this.startDate = startDate;
    }

    public Timestamp getEndDate() {
        return endDate;
    }

    public void setEndDate(Timestamp endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getMinOrderValue() {
        return minOrderValue;
    }

    public void setMinOrderValue(BigDecimal minOrderValue) {
        this.minOrderValue = minOrderValue;
    }

    public BigDecimal getMaxDiscount() {
        return maxDiscount;
    }

    public void setMaxDiscount(BigDecimal maxDiscount) {
        this.maxDiscount = maxDiscount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
