package model;

import java.math.BigDecimal;

/**
 * One row of a dashboard breakdown: a label (product, category, status,
 * payment method or SKU) with a count and an amount.
 */
public class ReportBreakdown {

    private String label;
    private String detail;
    private long count;
    private BigDecimal amount = BigDecimal.ZERO;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount == null ? BigDecimal.ZERO : amount;
    }
}
