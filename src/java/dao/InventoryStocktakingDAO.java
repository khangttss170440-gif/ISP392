package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import model.ProductVariant;
import model.StocktakingRecord;
import util.DBConnection;

public class InventoryStocktakingDAO {

    public List<ProductVariant> getActiveVariants() throws SQLException {
        String sql =
                "SELECT pv.VariantID, pv.ProductID, pv.SKU, pv.Color, "
                + "pv.Size, pv.Price, pv.Quantity, pv.Status, "
                + "p.ProductName, p.Brand "
                + "FROM dbo.ProductVariant pv "
                + "INNER JOIN dbo.Product p ON p.ProductID = pv.ProductID "
                + "WHERE pv.Status = 'Active' AND p.Status = 'Active' "
                + "ORDER BY p.ProductName, pv.Color, pv.Size";

        List<ProductVariant> variants = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                ProductVariant variant = new ProductVariant();

                variant.setVariantID(result.getInt("VariantID"));
                variant.setProductID(result.getInt("ProductID"));
                variant.setSku(result.getString("SKU"));
                variant.setColor(result.getString("Color"));
                variant.setSize(result.getString("Size"));
                variant.setPrice(result.getBigDecimal("Price"));
                variant.setQuantity(result.getInt("Quantity"));
                variant.setStatus(result.getString("Status"));
                variant.setProductName(result.getString("ProductName"));
                variant.setBrand(result.getString("Brand"));

                variants.add(variant);
            }
        }

        return variants;
    }

    public List<StocktakingRecord> getRecentHistory()
            throws SQLException {

        String sql =
                "SELECT TOP (" + util.AppConfig.integer("INVENTORY_HISTORY_LIMIT", 1, 100000) + ") st.StocktakingID, st.VariantID, "
                + "p.ProductName, pv.SKU, pv.Color, pv.Size, "
                + "s.FullName AS StaffName, st.StocktakingDate, "
                + "st.CycleType, st.SystemQuantity, st.ActualQuantity, "
                + "st.DifferenceQuantity, st.Status, st.Note "
                + "FROM dbo.Stocktaking st "
                + "INNER JOIN dbo.ProductVariant pv "
                + "    ON pv.VariantID = st.VariantID "
                + "INNER JOIN dbo.Product p "
                + "    ON p.ProductID = pv.ProductID "
                + "INNER JOIN dbo.Staff s ON s.StaffID = st.StaffID "
                + "ORDER BY st.StocktakingDate DESC, st.StocktakingID DESC";

        List<StocktakingRecord> records = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                StocktakingRecord record = new StocktakingRecord();

                record.setStocktakingID(
                        result.getInt("StocktakingID")
                );
                record.setVariantID(result.getInt("VariantID"));
                record.setProductName(result.getString("ProductName"));
                record.setSku(result.getString("SKU"));
                record.setColor(result.getString("Color"));
                record.setSize(result.getString("Size"));
                record.setStaffName(result.getString("StaffName"));
                record.setStocktakingDate(
                        result.getTimestamp("StocktakingDate")
                );
                record.setCycleType(result.getString("CycleType"));
                record.setSystemQuantity(
                        result.getInt("SystemQuantity")
                );
                record.setActualQuantity(
                        result.getInt("ActualQuantity")
                );
                record.setDifferenceQuantity(
                        result.getInt("DifferenceQuantity")
                );
                record.setStatus(result.getString("Status"));
                record.setNote(result.getString("Note"));

                records.add(record);
            }
        }

        return records;
    }

    public void submitStocktaking(
            int staffID,
            String cycleType,
            String note,
            Map<Integer, Integer> actualQuantities) throws SQLException {

        if (staffID <= 0) {
            throw new IllegalArgumentException("Invalid staff ID.");
        }

        String safeCycleType = cycleType == null
                ? ""
                : cycleType.trim();

        if (!"Weekly".equalsIgnoreCase(safeCycleType)
                && !"Monthly".equalsIgnoreCase(safeCycleType)) {
            throw new IllegalArgumentException(
                    "Loại kiểm kê chỉ gồm hàng tuần hoặc hàng tháng."
            );
        }

        String safeNote = note == null ? null : note.trim();

        if (safeNote != null && safeNote.isEmpty()) {
            safeNote = null;
        }

        if (safeNote != null && safeNote.length() > 500) {
            safeNote = safeNote.substring(0, 500);
        }

        if (actualQuantities == null || actualQuantities.isEmpty()) {
            throw new IllegalArgumentException(
                    "Hãy nhập số lượng thực tế cho ít nhất một biến thể."
            );
        }

        if (actualQuantities.size() > util.AppConfig.integer("INVENTORY_MAX_BATCH_SIZE", 1, 1000)) {
            throw new IllegalArgumentException(
                    "Mỗi lần kiểm kê tối đa " + util.AppConfig.integer("INVENTORY_MAX_BATCH_SIZE", 1, 1000) + " biến thể."
            );
        }

        Map<Integer, Integer> sortedQuantities =
                new TreeMap<>(actualQuantities);

        for (Map.Entry<Integer, Integer> entry
                : sortedQuantities.entrySet()) {

            if (entry.getKey() <= 0
                    || entry.getValue() == null
                    || entry.getValue() < 0
                    || entry.getValue() > util.AppConfig.integer("INVENTORY_MAX_QUANTITY", 1, Integer.MAX_VALUE)) {
                throw new IllegalArgumentException(
                        "Số lượng kiểm kê phải từ 0 đến " + util.AppConfig.integer("INVENTORY_MAX_QUANTITY", 1, Integer.MAX_VALUE) + "."
                );
            }
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                for (Map.Entry<Integer, Integer> entry
                        : sortedQuantities.entrySet()) {

                    int variantID = entry.getKey();
                    int actualQuantity = entry.getValue();

                    int systemQuantity = lockAndReadQuantity(
                            connection,
                            variantID
                    );

                    updateVariantQuantity(
                            connection,
                            variantID,
                            actualQuantity
                    );

                    insertStocktakingRecord(
                            connection,
                            variantID,
                            staffID,
                            safeCycleType,
                            systemQuantity,
                            actualQuantity,
                            safeNote
                    );
                }

                connection.commit();

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

    private int lockAndReadQuantity(
            Connection connection,
            int variantID) throws SQLException {

        String sql =
                "SELECT pv.Quantity "
                + "FROM dbo.ProductVariant pv WITH (UPDLOCK, ROWLOCK) "
                + "INNER JOIN dbo.Product p ON p.ProductID = pv.ProductID "
                + "WHERE pv.VariantID = ? "
                + "AND pv.Status = 'Active' "
                + "AND p.Status = 'Active'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, variantID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("ACTIVE_VARIANT_NOT_FOUND");
                }

                return result.getInt("Quantity");
            }
        }
    }

    private void updateVariantQuantity(
            Connection connection,
            int variantID,
            int actualQuantity) throws SQLException {

        String sql =
                "UPDATE dbo.ProductVariant "
                + "SET Quantity = ? "
                + "WHERE VariantID = ? AND Status = 'Active'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, actualQuantity);
            statement.setInt(2, variantID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("STOCK_UPDATE_FAILED");
            }
        }
    }

    private void insertStocktakingRecord(
            Connection connection,
            int variantID,
            int staffID,
            String cycleType,
            int systemQuantity,
            int actualQuantity,
            String note) throws SQLException {

        String sql =
                "INSERT INTO dbo.Stocktaking "
                + "(VariantID, StaffID, CycleType, SystemQuantity, "
                + "ActualQuantity, Status, Note) "
                + "VALUES (?, ?, ?, ?, ?, 'Completed', ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, variantID);
            statement.setInt(2, staffID);
            statement.setString(3, cycleType);
            statement.setInt(4, systemQuantity);
            statement.setInt(5, actualQuantity);
            statement.setString(6, note);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("STOCKTAKING_INSERT_FAILED");
            }
        }
    }
}
