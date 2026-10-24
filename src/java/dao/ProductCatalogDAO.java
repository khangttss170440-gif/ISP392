package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.Product;
import model.ProductCategory;
import model.ProductVariant;
import util.DBConnection;

public class ProductCatalogDAO {

    private static final String PRODUCT_SELECT
            = "SELECT p.ProductID, p.CategoryID, c.CategoryName, "
            + "p.ProductName, p.Description, p.Brand, p.Material, p.Status, "
            + "CASE WHEN EXISTS (SELECT 1 FROM dbo.ProductVariant pv "
            + "WHERE pv.ProductID = p.ProductID AND pv.Status = 'Active' "
            + "AND pv.Quantity > 0) THEN 1 ELSE 0 END AS InStock "
            + "FROM dbo.Product p "
            + "INNER JOIN dbo.ProductCategory c "
            + "ON c.CategoryID = p.CategoryID ";

    public List<ProductCategory> getActiveCategories()
            throws SQLException {

        String sql
                = "SELECT CategoryID, CategoryName, Description, Status "
                + "FROM dbo.ProductCategory "
                + "WHERE Status = 'Active' "
                + "ORDER BY CategoryName";

        List<ProductCategory> categories = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement
                = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                ProductCategory category = new ProductCategory();
                category.setCategoryID(result.getInt("CategoryID"));
                category.setCategoryName(result.getString("CategoryName"));
                category.setDescription(result.getString("Description"));
                category.setStatus(result.getString("Status"));
                categories.add(category);
            }
        }

        return categories;
    }

    /** Active categories that have active products, with a representative product for homepage imagery. */
    public List<ProductCategory> getHomepageCategories() throws SQLException {
        String sql = "SELECT c.CategoryID, c.CategoryName, c.Description, c.Status, "
                + "COUNT(p.ProductID) AS ActiveProductCount, MIN(p.ProductID) AS FeaturedProductID "
                + "FROM dbo.ProductCategory c "
                + "JOIN dbo.Product p ON p.CategoryID=c.CategoryID AND p.Status='Active' "
                + "WHERE c.Status='Active' "
                + "GROUP BY c.CategoryID, c.CategoryName, c.Description, c.Status "
                + "ORDER BY c.CategoryName";
        List<ProductCategory> categories = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                ProductCategory category = new ProductCategory();
                category.setCategoryID(result.getInt("CategoryID"));
                category.setCategoryName(result.getString("CategoryName"));
                category.setDescription(result.getString("Description"));
                category.setStatus(result.getString("Status"));
                category.setActiveProductCount(result.getInt("ActiveProductCount"));
                category.setFeaturedProductID(result.getInt("FeaturedProductID"));
                categories.add(category);
            }
        }
        return categories;
    }

    public List<Product> getActiveProducts(Integer categoryID)
            throws SQLException {

        StringBuilder sql = new StringBuilder(PRODUCT_SELECT);
        sql.append("WHERE p.Status = 'Active' ");
        sql.append("AND c.Status = 'Active' ");

        if (categoryID != null) {
            sql.append("AND p.CategoryID = ? ");
        }

        sql.append("ORDER BY p.ProductID DESC");

        List<Product> products = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement
                = connection.prepareStatement(sql.toString())) {

            if (categoryID != null) {
                statement.setInt(1, categoryID);
            }

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    products.add(mapProduct(result));
                }
            }
        }

        return products;
    }

    public Product getActiveProductById(int productID)
            throws SQLException {

        String sql = PRODUCT_SELECT
                + "WHERE p.ProductID = ? "
                + "AND p.Status = 'Active' "
                + "AND c.Status = 'Active'";

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, productID);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return mapProduct(result);
                }
            }
        }

        return null;
    }

    public List<ProductVariant> getActiveVariantsByProduct(int productID)
            throws SQLException {

        String sql
                = "SELECT VariantID, ProductID, SKU, Color, Size, "
                + "Price, Status, Quantity "
                + "FROM dbo.ProductVariant "
                + "WHERE ProductID = ? "
                + "AND Status = 'Active' "
                + "ORDER BY Color, Size";

        List<ProductVariant> variants = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, productID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ProductVariant variant = new ProductVariant();
                    variant.setVariantID(result.getInt("VariantID"));
                    variant.setProductID(result.getInt("ProductID"));
                    variant.setSku(result.getString("SKU"));
                    variant.setColor(result.getString("Color"));
                    variant.setSize(result.getString("Size"));
                    variant.setPrice(result.getBigDecimal("Price"));
                    variant.setStatus(result.getString("Status"));
                    variant.setQuantity(result.getInt("Quantity"));
                    variants.add(variant);
                }
            }
        }

        return variants;
    }

    private Product mapProduct(ResultSet result) throws SQLException {
        Product product = new Product();
        product.setProductID(result.getInt("ProductID"));
        product.setCategoryID(result.getInt("CategoryID"));
        product.setCategoryName(result.getString("CategoryName"));
        product.setProductName(result.getString("ProductName"));
        product.setDescription(result.getString("Description"));
        product.setBrand(result.getString("Brand"));
        product.setMaterial(result.getString("Material"));
        product.setStatus(result.getString("Status"));
        product.setInStock(result.getBoolean("InStock"));
        return product;
    }

    public ProductVariant getActiveVariantById(int variantID)
            throws SQLException {

        String sql
                = "SELECT pv.VariantID, pv.ProductID, pv.SKU, pv.Color, "
                + "pv.Size, pv.Price, pv.Status, pv.Quantity, "
                + "p.ProductName, p.Brand "
                + "FROM dbo.ProductVariant pv "
                + "INNER JOIN dbo.Product p ON p.ProductID = pv.ProductID "
                + "WHERE pv.VariantID = ? "
                + "AND pv.Status = 'Active' "
                + "AND p.Status = 'Active'";

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, variantID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                ProductVariant variant = new ProductVariant();
                variant.setVariantID(result.getInt("VariantID"));
                variant.setProductID(result.getInt("ProductID"));
                variant.setSku(result.getString("SKU"));
                variant.setColor(result.getString("Color"));
                variant.setSize(result.getString("Size"));
                variant.setPrice(result.getBigDecimal("Price"));
                variant.setStatus(result.getString("Status"));
                variant.setQuantity(result.getInt("Quantity"));
                variant.setProductName(result.getString("ProductName"));
                variant.setBrand(result.getString("Brand"));

                return variant;
            }
        }
    }
}
