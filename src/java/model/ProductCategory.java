package model;

public class ProductCategory {

    private int categoryID;
    private String categoryName;
    private String description;
    private String status;
    private int activeProductCount;
    private int featuredProductID;

    public int getCategoryID() {
        return categoryID;
    }

    public void setCategoryID(int categoryID) {
        this.categoryID = categoryID;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getActiveProductCount() {
        return activeProductCount;
    }

    public void setActiveProductCount(int activeProductCount) {
        this.activeProductCount = activeProductCount;
    }

    public int getFeaturedProductID() {
        return featuredProductID;
    }

    public void setFeaturedProductID(int featuredProductID) {
        this.featuredProductID = featuredProductID;
    }
}
