package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.Product;
import model.ProductCategory;
import util.DBConnection;

public class AdminProductDAO {

    private static final String PRODUCT_COLUMNS
            = "p.ProductID, p.CategoryID, c.CategoryName, "
            + "p.ProductName, p.Description, p.Brand, "
            + "p.Material, p.Status";

    public List<Product> getAllProducts() throws SQLException {
        String sql
                = "SELECT " + PRODUCT_COLUMNS
                + " FROM dbo.Product p "
                + "INNER JOIN dbo.ProductCategory c "
                + "    ON c.CategoryID = p.CategoryID "
                + "ORDER BY p.ProductID DESC";

        List<Product> products = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                products.add(mapProduct(result));
            }
        }

        return products;
    }

    public List<ProductCategory> getActiveCategories()
            throws SQLException {

        String sql
                = "SELECT CategoryID, CategoryName, Description, Status "
                + "FROM dbo.ProductCategory "
                + "WHERE Status = 'Active' "
                + "ORDER BY CategoryName";

        List<ProductCategory> categories = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                ProductCategory category = new ProductCategory();

                category.setCategoryID(result.getInt("CategoryID"));
                category.setCategoryName(
                        result.getString("CategoryName")
                );
                category.setDescription(
                        result.getString("Description")
                );
                category.setStatus(result.getString("Status"));

                categories.add(category);
            }
        }

        return categories;
    }

    public Product getProductById(int productID) throws SQLException {
        String sql
                = "SELECT " + PRODUCT_COLUMNS
                + " FROM dbo.Product p "
                + "INNER JOIN dbo.ProductCategory c "
                + "    ON c.CategoryID = p.CategoryID "
                + "WHERE p.ProductID = ?";

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, productID);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return mapProduct(result);
                }
            }
        }

        return null;
    }

    public int createProduct(Product product, int staffID)
            throws SQLException {

        validateProduct(product);
        validateActiveCategory(product.getCategoryID());

        String sql
                = "INSERT INTO dbo.Product "
                + "(CategoryID, ProductName, Description, Brand, Material, "
                + "Status, CreatedByStaffID) "
                + "OUTPUT INSERTED.ProductID "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            bindProduct(statement, product);
            statement.setInt(7, staffID);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("ProductID");
                }
            }
        }

        throw new SQLException("PRODUCT_CREATE_FAILED");
    }

    public void updateProduct(Product product, int staffID)
            throws SQLException {

        if (product == null || product.getProductID() <= 0) {
            throw new IllegalArgumentException("Mã sản phẩm không hợp lệ.");
        }

        validateProduct(product);
        validateActiveCategory(product.getCategoryID());

        String sql
                = "UPDATE dbo.Product "
                + "SET CategoryID = ?, ProductName = ?, Description = ?, "
                + "    Brand = ?, Material = ?, Status = ?, "
                + "    UpdatedByStaffID = ?, UpdatedAt = SYSDATETIME() "
                + "WHERE ProductID = ?";

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            bindProduct(statement, product);
            statement.setInt(7, staffID);
            statement.setInt(8, product.getProductID());

            if (statement.executeUpdate() != 1) {
                throw new SQLException("PRODUCT_NOT_FOUND");
            }
        }
    }

    public void updateProductStatus(
            int productID,
            String status) throws SQLException {

        if (productID <= 0) {
            throw new IllegalArgumentException("Mã sản phẩm không hợp lệ.");
        }

        String safeStatus = validateStatus(status);

        String sql
                = "UPDATE dbo.Product "
                + "SET Status = ?, UpdatedAt = SYSDATETIME() "
                + "WHERE ProductID = ?";

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, safeStatus);
            statement.setInt(2, productID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("PRODUCT_NOT_FOUND");
            }
        }
    }

    private void validateActiveCategory(int categoryID)
            throws SQLException {

        String sql
                = "SELECT 1 FROM dbo.ProductCategory "
                + "WHERE CategoryID = ? AND Status = 'Active'";

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, categoryID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException(
                            "Danh mục không tồn tại hoặc đã bị tắt."
                    );
                }
            }
        }
    }

    private void validateProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Thiếu thông tin sản phẩm.");
        }

        String name = trim(product.getProductName());

        if (name.isEmpty() || name.length() > 200) {
            throw new IllegalArgumentException(
                    "Tên sản phẩm phải có từ 1 đến 200 ký tự."
            );
        }

        if (product.getCategoryID() <= 0) {
            throw new IllegalArgumentException("Hãy chọn danh mục.");
        }

        if (length(product.getDescription()) > 1000
                || length(product.getBrand()) > 100
                || length(product.getMaterial()) > 100) {
            throw new IllegalArgumentException(
                    "Mô tả tối đa 1000 ký tự; thương hiệu và chất liệu "
                    + "tối đa 100 ký tự."
            );
        }

        product.setProductName(name);
        product.setDescription(trimToNull(product.getDescription()));
        product.setBrand(trimToNull(product.getBrand()));
        product.setMaterial(trimToNull(product.getMaterial()));
        product.setStatus(validateStatus(product.getStatus()));
    }

    private void bindProduct(
            PreparedStatement statement,
            Product product) throws SQLException {

        statement.setInt(1, product.getCategoryID());
        statement.setString(2, product.getProductName());
        statement.setString(3, product.getDescription());
        statement.setString(4, product.getBrand());
        statement.setString(5, product.getMaterial());
        statement.setString(6, product.getStatus());
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

        return product;
    }

    private String validateStatus(String status) {
        if ("Active".equalsIgnoreCase(status)) {
            return "Active";
        }

        if ("Inactive".equalsIgnoreCase(status)) {
            return "Inactive";
        }

        throw new IllegalArgumentException(
                "Trạng thái sản phẩm không hợp lệ."
        );
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String valueTrimmed = trim(value);
        return valueTrimmed.isEmpty() ? null : valueTrimmed;
    }

    private int length(String value) {
        return value == null ? 0 : value.trim().length();
    }
}
