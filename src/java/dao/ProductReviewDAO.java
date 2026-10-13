package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import model.Product;
import model.ProductReview;
import util.DBConnection;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class ProductReviewDAO {

    public Product findReviewableProduct(
            int customerID,
            int orderID,
            int productID) throws SQLException {

        String sql
                = "SELECT TOP 1 p.ProductID, p.ProductName, "
                + "p.Description, p.Brand, p.Material, p.CategoryID "
                + "FROM dbo.SalesOrder o "
                + "INNER JOIN dbo.OrderDetail od ON od.OrderID = o.OrderID "
                + "INNER JOIN dbo.ProductVariant pv "
                + "ON pv.VariantID = od.VariantID "
                + "INNER JOIN dbo.Product p ON p.ProductID = pv.ProductID "
                + "WHERE o.OrderID = ? "
                + "AND o.CustomerID = ? "
                + "AND p.ProductID = ? "
                + "AND UPPER(o.OrderStatus) = 'DELIVERED'";

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, orderID);
            statement.setInt(2, customerID);
            statement.setInt(3, productID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                Product product = new Product();
                product.setProductID(result.getInt("ProductID"));
                product.setProductName(result.getString("ProductName"));
                product.setDescription(result.getString("Description"));
                product.setBrand(result.getString("Brand"));
                product.setMaterial(result.getString("Material"));
                product.setCategoryID(result.getInt("CategoryID"));
                return product;
            }
        }
    }

    public boolean hasReviewed(
            int customerID,
            int orderID,
            int productID) throws SQLException {

        String sql
                = "SELECT 1 FROM dbo.ProductReview "
                + "WHERE CustomerID = ? AND OrderID = ? AND ProductID = ?";

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, customerID);
            statement.setInt(2, orderID);
            statement.setInt(3, productID);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public Set<Integer> getReviewedProductIDs(int customerID, int orderID)
            throws SQLException {
        String sql =
                "SELECT ProductID FROM dbo.ProductReview "
                + "WHERE CustomerID = ? AND OrderID = ?";
        Set<Integer> productIDs = new HashSet<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerID);
            statement.setInt(2, orderID);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    productIDs.add(result.getInt("ProductID"));
                }
            }
        }
        return productIDs;
    }

    public boolean createReview(ProductReview review)
            throws SQLException {

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                if (!isReviewAllowed(
                        connection,
                        review.getCustomerID(),
                        review.getOrderID(),
                        review.getProductID())) {
                    connection.rollback();
                    return false;
                }

                String sql
                        = "INSERT INTO dbo.ProductReview "
                        + "(CustomerID, OrderID, ProductID, Rating, Comment) "
                        + "VALUES (?, ?, ?, ?, ?)";

                try (PreparedStatement statement
                        = connection.prepareStatement(sql)) {

                    statement.setInt(1, review.getCustomerID());
                    statement.setInt(2, review.getOrderID());
                    statement.setInt(3, review.getProductID());
                    statement.setInt(4, review.getRating());

                    if (review.getComment() == null
                            || review.getComment().trim().isEmpty()) {
                        statement.setNull(5, Types.NVARCHAR);
                    } else {
                        statement.setString(5, review.getComment().trim());
                    }

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("REVIEW_CREATE_FAILED");
                    }
                }

                connection.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    private boolean isReviewAllowed(
            Connection connection,
            int customerID,
            int orderID,
            int productID) throws SQLException {

        String sql
                = "SELECT 1 "
                + "FROM dbo.SalesOrder o WITH (UPDLOCK, HOLDLOCK) "
                + "INNER JOIN dbo.OrderDetail od ON od.OrderID = o.OrderID "
                + "INNER JOIN dbo.ProductVariant pv "
                + "ON pv.VariantID = od.VariantID "
                + "WHERE o.OrderID = ? "
                + "AND o.CustomerID = ? "
                + "AND pv.ProductID = ? "
                + "AND UPPER(o.OrderStatus) = 'DELIVERED' "
                + "AND NOT EXISTS ("
                + "    SELECT 1 FROM dbo.ProductReview r "
                + "    WHERE r.CustomerID = o.CustomerID "
                + "    AND r.OrderID = o.OrderID "
                + "    AND r.ProductID = pv.ProductID"
                + ")";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.setInt(2, customerID);
            statement.setInt(3, productID);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public List<ProductReview> getVisibleReviewsByProduct(int productID)
            throws SQLException {

        String sql
                = "SELECT r.ReviewID, r.CustomerID, r.OrderID, r.ProductID, "
                + "r.Rating, r.Comment, r.Status, r.CreatedAt, c.FullName "
                + "FROM dbo.ProductReview r "
                + "INNER JOIN dbo.Customer c ON c.CustomerID = r.CustomerID "
                + "WHERE r.ProductID = ? "
                + "AND r.Status = 'Visible' "
                + "AND c.Status = 'Active' "
                + "ORDER BY r.CreatedAt DESC, r.ReviewID DESC";

        List<ProductReview> reviews = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, productID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ProductReview review = new ProductReview();

                    review.setReviewID(result.getInt("ReviewID"));
                    review.setCustomerID(result.getInt("CustomerID"));
                    review.setOrderID(result.getInt("OrderID"));
                    review.setProductID(result.getInt("ProductID"));
                    review.setRating(result.getInt("Rating"));
                    review.setComment(result.getString("Comment"));
                    review.setStatus(result.getString("Status"));
                    review.setCreatedAt(result.getTimestamp("CreatedAt"));
                    review.setCustomerName(result.getString("FullName"));

                    reviews.add(review);
                }
            }
        }

        return reviews;
    }

    public BigDecimal getAverageRating(int productID)
            throws SQLException {

        String sql
                = "SELECT ISNULL(AVG(CAST(Rating AS decimal(4, 2))), 0) "
                + "AS AverageRating "
                + "FROM dbo.ProductReview "
                + "WHERE ProductID = ? AND Status = 'Visible'";

        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, productID);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    BigDecimal average = result.getBigDecimal("AverageRating");
                    return average == null ? BigDecimal.ZERO : average;
                }
            }
        }

        return BigDecimal.ZERO;
    }
}
