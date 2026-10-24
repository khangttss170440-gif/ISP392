package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import model.ProductCategory;
import util.DBConnection;

public class AdminCategoryDAO {

    public List<ProductCategory> getAllCategories() throws SQLException {
        String sql =
                "SELECT CategoryID, CategoryName, Description, Status "
                + "FROM dbo.ProductCategory "
                + "ORDER BY CategoryName";

        List<ProductCategory> categories = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

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

    public int createCategory(
            String categoryName,
            String description) throws SQLException {

        String name = validateName(categoryName);
        String safeDescription = validateDescription(description);

        ensureNameAvailable(name, 0);

        String sql =
                "INSERT INTO dbo.ProductCategory "
                + "(CategoryName, Description, Status) "
                + "OUTPUT INSERTED.CategoryID "
                + "VALUES (?, ?, 'Active')";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);
            statement.setString(2, safeDescription);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("CategoryID");
                }
            }
        }

        throw new SQLException("CATEGORY_CREATE_FAILED");
    }

    public void updateCategory(
            int categoryID,
            String categoryName,
            String description,
            String status) throws SQLException {

        if (categoryID <= 0) {
            throw new IllegalArgumentException("Invalid category ID.");
        }

        String name = validateName(categoryName);
        String safeDescription = validateDescription(description);
        String safeStatus = validateStatus(status);

        ensureNameAvailable(name, categoryID);

        String sql =
                "UPDATE dbo.ProductCategory "
                + "SET CategoryName = ?, Description = ?, Status = ? "
                + "WHERE CategoryID = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);
            statement.setString(2, safeDescription);
            statement.setString(3, safeStatus);
            statement.setInt(4, categoryID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("CATEGORY_NOT_FOUND");
            }
        }
    }

    /** Saves the complete category form atomically so one invalid row cannot leave partial changes. */
    public void updateCategories(List<ProductCategory> categories) throws SQLException {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("Không có danh mục nào để lưu.");
        }

        Map<Integer, ProductCategory> submitted = new HashMap<>();
        Set<String> names = new HashSet<>();
        for (ProductCategory category : categories) {
            if (category == null || category.getCategoryID() <= 0) {
                throw new IllegalArgumentException("Mã danh mục không hợp lệ.");
            }
            String name = validateName(category.getCategoryName());
            validateDescription(category.getDescription());
            validateStatus(category.getStatus());
            if (!submitted.containsKey(category.getCategoryID())) {
                category.setCategoryName(name);
                submitted.put(category.getCategoryID(), category);
            } else {
                throw new IllegalArgumentException("Danh sách danh mục không hợp lệ.");
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("CATEGORY_NAME_EXISTS");
            }
        }

        String lockSql = "SELECT CategoryID, CategoryName FROM dbo.ProductCategory WITH (UPDLOCK, HOLDLOCK)";
        String updateSql = "UPDATE dbo.ProductCategory SET CategoryName=?, Description=?, Status=? WHERE CategoryID=?";
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<Integer, String> finalNames = new HashMap<>();
                try (PreparedStatement statement = connection.prepareStatement(lockSql);
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) finalNames.put(result.getInt("CategoryID"), result.getString("CategoryName"));
                }
                if (!finalNames.keySet().equals(submitted.keySet())) {
                    throw new SQLException("CATEGORY_NOT_FOUND");
                }
                for (ProductCategory category : submitted.values()) {
                    finalNames.put(category.getCategoryID(), category.getCategoryName());
                }
                Set<String> finalNameSet = new HashSet<>();
                for (String name : finalNames.values()) {
                    if (name == null || !finalNameSet.add(name.trim().toLowerCase(Locale.ROOT))) {
                        throw new IllegalArgumentException("CATEGORY_NAME_EXISTS");
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    for (ProductCategory category : categories) {
                        statement.setString(1, category.getCategoryName());
                        statement.setString(2, validateDescription(category.getDescription()));
                        statement.setString(3, validateStatus(category.getStatus()));
                        statement.setInt(4, category.getCategoryID());
                        statement.addBatch();
                    }
                    int[] results = statement.executeBatch();
                    for (int result : results) {
                        if (result == 0 || result == Statement.EXECUTE_FAILED) {
                            throw new SQLException("CATEGORY_NOT_FOUND");
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    public void updateCategoryStatus(
            int categoryID,
            String status) throws SQLException {

        if (categoryID <= 0) {
            throw new IllegalArgumentException("Invalid category ID.");
        }

        String safeStatus = validateStatus(status);

        String sql =
                "UPDATE dbo.ProductCategory "
                + "SET Status = ? "
                + "WHERE CategoryID = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, safeStatus);
            statement.setInt(2, categoryID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("CATEGORY_NOT_FOUND");
            }
        }
    }

    private void ensureNameAvailable(
            String categoryName,
            int exceptCategoryID) throws SQLException {

        String sql =
                "SELECT TOP (1) CategoryID "
                + "FROM dbo.ProductCategory "
                + "WHERE CategoryName = ? AND CategoryID <> ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, categoryName);
            statement.setInt(2, exceptCategoryID);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new IllegalArgumentException(
                            "CATEGORY_NAME_EXISTS"
                    );
                }
            }
        }
    }

    private String validateName(String categoryName) {
        String name = categoryName == null ? "" : categoryName.trim();

        if (name.isEmpty() || name.length() > 100) {
            throw new IllegalArgumentException(
                    "Tên danh mục phải có từ 1 đến 100 ký tự."
            );
        }

        return name;
    }

    private String validateDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }

        String value = description.trim();

        if (value.length() > 500) {
            throw new IllegalArgumentException(
                    "Mô tả danh mục tối đa 500 ký tự."
            );
        }

        return value;
    }

    private String validateStatus(String status) {
        if ("Active".equalsIgnoreCase(status)) {
            return "Active";
        }

        if ("Inactive".equalsIgnoreCase(status)) {
            return "Inactive";
        }

        throw new IllegalArgumentException("Trạng thái danh mục không hợp lệ.");
    }
}
