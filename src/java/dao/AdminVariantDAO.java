package dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import model.ProductVariant;
import util.DBConnection;

public class AdminVariantDAO {

    public List<ProductVariant> getVariants(int productID)
            throws SQLException {

        StringBuilder sql = new StringBuilder(
                "SELECT pv.VariantID, pv.ProductID, pv.SKU, pv.Color, "
                + "pv.Size, pv.Price, pv.Status, pv.Quantity, "
                + "p.ProductName, p.Brand "
                + "FROM dbo.ProductVariant pv "
                + "INNER JOIN dbo.Product p ON p.ProductID = pv.ProductID "
        );

        if (productID > 0) {
            sql.append("WHERE pv.ProductID = ? ");
        }

        sql.append("ORDER BY p.ProductName, pv.SKU");

        List<ProductVariant> variants = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {

            if (productID > 0) {
                statement.setInt(1, productID);
            }

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
                    variant.setProductName(
                            result.getString("ProductName")
                    );
                    variant.setBrand(result.getString("Brand"));

                    variants.add(variant);
                }
            }
        }

        return variants;
    }

    public int createVariant(ProductVariant variant) throws SQLException {
        validateVariant(variant);
        validateActiveProduct(variant.getProductID());
        ensureSkuAvailable(variant.getSku(), 0);

        String sql =
                "INSERT INTO dbo.ProductVariant "
                + "(ProductID, SKU, Color, Size, Price, Status, Quantity) "
                + "OUTPUT INSERTED.VariantID "
                + "VALUES (?, ?, ?, ?, ?, 'Active', ?)";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, variant.getProductID());
            statement.setString(2, variant.getSku());
            setNullableString(statement, 3, variant.getColor());
            setNullableString(statement, 4, variant.getSize());
            statement.setBigDecimal(5, variant.getPrice());
            statement.setInt(6, variant.getQuantity());

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("VariantID");
                }
            }
        }

        throw new SQLException("VARIANT_CREATE_FAILED");
    }

    public void updateVariant(ProductVariant variant) throws SQLException {
        if (variant == null || variant.getVariantID() <= 0) {
            throw new IllegalArgumentException("Mã biến thể không hợp lệ.");
        }

        validateVariant(variant);
        ensureSkuAvailable(variant.getSku(), variant.getVariantID());

        String sql =
                "UPDATE dbo.ProductVariant "
                + "SET SKU = ?, Color = ?, Size = ?, Price = ?, Status = ? "
                + "WHERE VariantID = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, variant.getSku());
            setNullableString(statement, 2, variant.getColor());
            setNullableString(statement, 3, variant.getSize());
            statement.setBigDecimal(4, variant.getPrice());
            statement.setString(5, variant.getStatus());
            statement.setInt(6, variant.getVariantID());

            if (statement.executeUpdate() != 1) {
                throw new SQLException("VARIANT_NOT_FOUND");
            }
        }
    }

    public void updateVariantStatus(
            int variantID,
            String status) throws SQLException {

        if (variantID <= 0) {
            throw new IllegalArgumentException("Mã biến thể không hợp lệ.");
        }

        String safeStatus = validateStatus(status);
        String sql =
                "UPDATE dbo.ProductVariant "
                + "SET Status = ? "
                + "WHERE VariantID = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, safeStatus);
            statement.setInt(2, variantID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("VARIANT_NOT_FOUND");
            }
        }
    }

    private void validateActiveProduct(int productID) throws SQLException {
        if (productID <= 0) {
            throw new IllegalArgumentException("Hãy chọn sản phẩm.");
        }

        String sql =
                "SELECT 1 FROM dbo.Product "
                + "WHERE ProductID = ? AND Status = 'Active'";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, productID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException(
                            "Sản phẩm không tồn tại hoặc đã bị tắt."
                    );
                }
            }
        }
    }

    private void ensureSkuAvailable(String sku, int exceptVariantID)
            throws SQLException {

        String sql =
                "SELECT TOP (1) VariantID "
                + "FROM dbo.ProductVariant "
                + "WHERE SKU = ? AND VariantID <> ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, sku);
            statement.setInt(2, exceptVariantID);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new IllegalArgumentException("SKU_ALREADY_EXISTS");
                }
            }
        }
    }

    private void validateVariant(ProductVariant variant) {
        if (variant == null) {
            throw new IllegalArgumentException("Thiếu thông tin biến thể.");
        }

        if (variant.getQuantity() < 0) {
            throw new IllegalArgumentException("Số lượng tồn kho phải từ 0 trở lên.");
        }

        String sku = trim(variant.getSku());

        if (sku.isEmpty() || sku.length() > 50) {
            throw new IllegalArgumentException(
                    "SKU phải có từ 1 đến 50 ký tự."
            );
        }

        if (length(variant.getColor()) > 50
                || length(variant.getSize()) > 20) {
            throw new IllegalArgumentException(
                    "Màu tối đa 50 ký tự, size tối đa 20 ký tự."
            );
        }

        BigDecimal price = variant.getPrice();

        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException(
                    "Giá phải là số không âm."
            );
        }

        try {
            price = price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Giá chỉ được có tối đa 2 chữ số thập phân."
            );
        }

        if (price.precision() > 18) {
            throw new IllegalArgumentException(
                    "Giá vượt quá giới hạn lưu trữ."
            );
        }

        variant.setSku(sku);
        variant.setColor(trimToNull(variant.getColor()));
        variant.setSize(trimToNull(variant.getSize()));
        variant.setPrice(price);
        variant.setStatus(validateStatus(variant.getStatus()));
    }

    private String validateStatus(String status) {
        if ("Active".equalsIgnoreCase(status)) {
            return "Active";
        }

        if ("Inactive".equalsIgnoreCase(status)) {
            return "Inactive";
        }

        throw new IllegalArgumentException(
                "Trạng thái biến thể không hợp lệ."
        );
    }

    private void setNullableString(
            PreparedStatement statement,
            int index,
            String value) throws SQLException {

        if (value == null) {
            statement.setNull(index, Types.NVARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int length(String value) {
        return value == null ? 0 : value.trim().length();
    }
}
