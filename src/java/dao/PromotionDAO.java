package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.Promotion;
import util.DBConnection;

public class PromotionDAO {

    private static final String COLUMNS = "PromotionID, PromotionCode, PromotionName, "
            + "DiscountType, DiscountValue, StartDate, EndDate, MinOrderValue, MaxDiscount, Status, Quantity";
    private static final String AVAILABLE = "Status = 'Active' "
            + "AND Quantity > 0 AND StartDate <= CURRENT_TIMESTAMP AND EndDate >= CURRENT_TIMESTAMP ";

    public List<Promotion> getAvailablePromotions() throws SQLException {
        List<Promotion> promotions = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM dbo.Promotion WHERE "
                + AVAILABLE + "ORDER BY EndDate, PromotionID DESC";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                promotions.add(mapPromotion(result));
            }
        }
        return promotions;
    }

    public Promotion findAvailableByCode(String code) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return findAvailableByCode(connection, code, false);
        }
    }

    // Keep the selected promotion stable until the checkout transaction commits.
    public Promotion findAvailableByCode(Connection connection, String code, boolean lock)
            throws SQLException {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]{3,50}")) {
            throw new IllegalArgumentException("Mã khuyến mãi không hợp lệ.");
        }
        String sql = "SELECT " + COLUMNS + " FROM dbo.Promotion "
                + (lock ? "WITH (UPDLOCK, HOLDLOCK, ROWLOCK) " : "")
                + "WHERE PromotionCode = ? AND " + AVAILABLE;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return mapPromotion(result);
                }
            }
        }
        throw new IllegalArgumentException("Mã khuyến mãi không tồn tại, đã hết lượt, đã tắt hoặc ngoài thời gian áp dụng.");
    }

    /** Must be called on the order transaction; rollback also restores the coupon. */
    public void consumeCoupon(Connection connection, int promotionId) throws SQLException {
        if (connection.getAutoCommit()) throw new IllegalStateException("Coupon consumption requires an order transaction.");
        String sql = "UPDATE dbo.Promotion SET Quantity = Quantity - 1 WHERE PromotionID = ? AND " + AVAILABLE;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, promotionId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Mã khuyến mãi đã hết lượt hoặc không còn hiệu lực. Vui lòng chọn mã khác.");
            }
        }
    }

    private Promotion mapPromotion(ResultSet result) throws SQLException {
        Promotion promotion = new Promotion();
        promotion.setPromotionID(result.getInt("PromotionID"));
        promotion.setPromotionCode(result.getString("PromotionCode"));
        promotion.setPromotionName(result.getString("PromotionName"));
        promotion.setDiscountType(result.getString("DiscountType"));
        promotion.setDiscountValue(result.getBigDecimal("DiscountValue"));
        promotion.setStartDate(result.getTimestamp("StartDate"));
        promotion.setEndDate(result.getTimestamp("EndDate"));
        promotion.setMinOrderValue(result.getBigDecimal("MinOrderValue"));
        promotion.setMaxDiscount(result.getBigDecimal("MaxDiscount"));
        promotion.setStatus(result.getString("Status"));
        promotion.setQuantity(result.getInt("Quantity"));
        return promotion;
    }
}
