package model;

import java.sql.Timestamp;

public class StocktakingRecord {

    private int stocktakingID;
    private int variantID;
    private String productName;
    private String sku;
    private String color;
    private String size;
    private String staffName;
    private Timestamp stocktakingDate;
    private String cycleType;
    private int systemQuantity;
    private int actualQuantity;
    private int differenceQuantity;
    private String status;
    private String note;

    public int getStocktakingID() {
        return stocktakingID;
    }

    public void setStocktakingID(int stocktakingID) {
        this.stocktakingID = stocktakingID;
    }

    public int getVariantID() {
        return variantID;
    }

    public void setVariantID(int variantID) {
        this.variantID = variantID;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public Timestamp getStocktakingDate() {
        return stocktakingDate;
    }

    public void setStocktakingDate(Timestamp stocktakingDate) {
        this.stocktakingDate = stocktakingDate;
    }

    public String getCycleType() {
        return cycleType;
    }

    public void setCycleType(String cycleType) {
        this.cycleType = cycleType;
    }

    public int getSystemQuantity() {
        return systemQuantity;
    }

    public void setSystemQuantity(int systemQuantity) {
        this.systemQuantity = systemQuantity;
    }

    public int getActualQuantity() {
        return actualQuantity;
    }

    public void setActualQuantity(int actualQuantity) {
        this.actualQuantity = actualQuantity;
    }

    public int getDifferenceQuantity() {
        return differenceQuantity;
    }

    public void setDifferenceQuantity(int differenceQuantity) {
        this.differenceQuantity = differenceQuantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}