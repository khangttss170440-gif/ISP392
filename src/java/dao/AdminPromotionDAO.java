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
import model.Promotion;
import util.DBConnection;

public class AdminPromotionDAO {

    private static final String COLUMNS =
            "PromotionID, PromotionCode, PromotionName, DiscountType, "
            + "DiscountValue, StartDate, EndDate, MinOrderValue, "
            + "MaxDiscount, Status";

    public List<Promotion> getAllPromotions() throws SQLException {
        String sql =
                "SELECT " + COLUMNS
                + " FROM dbo.Promotion "
                + "ORDER BY StartDate DESC, PromotionID DESC";

        List<Promotion> promotions = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                promotions.add(mapPromotion(result));
            }
        }

        return promotions;
    }

    public int createPromotion(Promotion promotion) throws SQLException {
        validate(promotion);
        ensureCodeAvailable(promotion.getPromotionCode(), 0);

        String sql =
                "INSERT INTO dbo.Promotion "
                + "(PromotionCode, PromotionName, DiscountType, "
                + "DiscountValue, StartDate, EndDate, MinOrderValue, "
                + "MaxDiscount, Status) "
                + "OUTPUT INSERTED.PromotionID "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Active')";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            bindPromotion(statement, promotion);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("PromotionID");
                }
            }
        }

        throw new SQLException("PROMOTION_CREATE_FAILED");
    }

    public void updatePromotion(Promotion promotion) throws SQLException {
        if (promotion == null || promotion.getPromotionID() <= 0) {
            throw new IllegalArgumentException("Mã khuyến mãi không hợp lệ.");
        }

        validate(promotion);
        ensureCodeAvailable(
                promotion.getPromotionCode(),
                promotion.getPromotionID()
        );

        String sql =
                "UPDATE dbo.Promotion "
                + "SET PromotionCode = ?, PromotionName = ?, "
                + "    DiscountType = ?, DiscountValue = ?, "
                + "    StartDate = ?, EndDate = ?, MinOrderValue = ?, "
                + "    MaxDiscount = ?, Status = ? "
                + "WHERE PromotionID = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            bindPromotion(statement, promotion);
            statement.setString(9, validateStatus(promotion.getStatus()));
            statement.setInt(10, promotion.getPromotionID());

            if (statement.executeUpdate() != 1) {
                throw new SQLException("PROMOTION_NOT_FOUND");
            }
        }
    }

    public void updatePromotionStatus(
            int promotionID,
            String status) throws SQLException {

        if (promotionID <= 0) {
            throw new IllegalArgumentException(
                    "Mã khuyến mãi không hợp lệ."
            );
        }

        String sql =
                "UPDATE dbo.Promotion SET Status = ? "
                + "WHERE PromotionID = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, validateStatus(status));
            statement.setInt(2, promotionID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("PROMOTION_NOT_FOUND");
            }
        }
    }

    private void ensureCodeAvailable(
            String code,
            int exceptPromotionID) throws SQLException {

        String sql =
                "SELECT TOP (1) PromotionID "
                + "FROM dbo.Promotion "
                + "WHERE PromotionCode = ? AND PromotionID <> ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, code);
            statement.setInt(2, exceptPromotionID);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new IllegalArgumentException(
                            "PROMOTION_CODE_EXISTS"
                    );
                }
            }
        }
    }

    private void validate(Promotion promotion) {
        if (promotion == null) {
            throw new IllegalArgumentException(
                    "Thiếu thông tin khuyến mãi."
            );
        }

        String code = trim(promotion.getPromotionCode())
                .toUpperCase();
        String name = trim(promotion.getPromotionName());

        if (!code.matches("[A-Z0-9_-]{3,50}")) {
            throw new IllegalArgumentException(
                    "Mã khuyến mãi phải có 3–50 ký tự chữ, số, _ hoặc -."
            );
        }

        if (name.isEmpty() || name.length() > 150) {
            throw new IllegalArgumentException(
                    "Tên khuyến mãi phải có từ 1 đến 150 ký tự."
            );
        }

        String type = normalizeDiscountType(
                promotion.getDiscountType()
        );

        BigDecimal value = promotion.getDiscountValue();

        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Giá trị giảm phải lớn hơn 0."
            );
        }

        try {
            value = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Giá trị giảm chỉ được có tối đa 2 số thập phân."
            );
        }

        if ("Percentage".equals(type)
                && value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(
                    "Phần trăm giảm không được vượt quá 100."
            );
        }

        if (value.precision() > 18) {
            throw new IllegalArgumentException(
                    "Giá trị giảm vượt giới hạn database."
            );
        }

        if (promotion.getStartDate() == null
                || promotion.getEndDate() == null
                || !promotion.getEndDate().after(promotion.getStartDate())) {
            throw new IllegalArgumentException(
                    "Thời gian kết thúc phải sau thời gian bắt đầu."
            );
        }

        validateOptionalAmount(
                promotion.getMinOrderValue(),
                "Đơn tối thiểu"
        );
        validateOptionalAmount(
                promotion.getMaxDiscount(),
                "Mức giảm tối đa"
        );

        promotion.setPromotionCode(code);
        promotion.setPromotionName(name);
        promotion.setDiscountType(type);
        promotion.setDiscountValue(value);
        promotion.setStatus(validateStatus(promotion.getStatus()));
    }

    private void validateOptionalAmount(
            BigDecimal value,
            String fieldName) {

        if (value == null) {
            return;
        }

        if (value.signum() < 0 || value.precision() > 18) {
            throw new IllegalArgumentException(
                    fieldName + " không hợp lệ."
            );
        }

        try {
            value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    fieldName + " chỉ được có tối đa 2 số thập phân."
            );
        }
    }

    private String normalizeDiscountType(String type) {
        if ("Percentage".equalsIgnoreCase(type)) {
            return "Percentage";
        }

        if ("Fixed".equalsIgnoreCase(type)) {
            return "Fixed";
        }

        throw new IllegalArgumentException(
                "Loại giảm giá chỉ nhận Percentage hoặc Fixed."
        );
    }

    private String validateStatus(String status) {
        if ("Active".equalsIgnoreCase(status)) {
            return "Active";
        }

        if ("Inactive".equalsIgnoreCase(status)) {
            return "Inactive";
        }

        throw new IllegalArgumentException(
                "Trạng thái khuyến mãi không hợp lệ."
        );
    }

    private void bindPromotion(
            PreparedStatement statement,
            Promotion promotion) throws SQLException {

        statement.setString(1, promotion.getPromotionCode());
        statement.setString(2, promotion.getPromotionName());
        statement.setString(3, promotion.getDiscountType());
        statement.setBigDecimal(4, promotion.getDiscountValue());
        statement.setTimestamp(5, promotion.getStartDate());
        statement.setTimestamp(6, promotion.getEndDate());
        setNullableDecimal(statement, 7, promotion.getMinOrderValue());
        setNullableDecimal(statement, 8, promotion.getMaxDiscount());
    }

    private void setNullableDecimal(
            PreparedStatement statement,
            int index,
            BigDecimal value) throws SQLException {

        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private Promotion mapPromotion(ResultSet result)
            throws SQLException {

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

        return promotion;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}