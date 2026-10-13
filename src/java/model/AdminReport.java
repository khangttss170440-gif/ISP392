package model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class AdminReport {

    private long totalOrders;
    private long cancelledOrders;
    private BigDecimal revenue = BigDecimal.ZERO;
    private long inventoryUnits;
    private long activeVariants;
    private long lowStockVariants;

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public long getCancelledOrders() {
        return cancelledOrders;
    }

    public void setCancelledOrders(long cancelledOrders) {
        this.cancelledOrders = cancelledOrders;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue == null ? BigDecimal.ZERO : revenue;
    }

    public long getInventoryUnits() {
        return inventoryUnits;
    }

    public void setInventoryUnits(long inventoryUnits) {
        this.inventoryUnits = inventoryUnits;
    }

    public long getActiveVariants() {
        return activeVariants;
    }

    public void setActiveVariants(long activeVariants) {
        this.activeVariants = activeVariants;
    }

    public long getLowStockVariants() {
        return lowStockVariants;
    }

    public void setLowStockVariants(long lowStockVariants) {
        this.lowStockVariants = lowStockVariants;
    }

    public BigDecimal getCancellationRate() {
        if (totalOrders == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(cancelledOrders)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
    }
}