package dao;

import java.sql.*;
import java.util.UUID;
import util.DBConnection;

/** SQL Server check using a temporary coupon in a transaction that is always rolled back. */
public class CouponQuantityIntegrationTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"--run-db-test".equals(args[0])) {
            throw new IllegalArgumentException("Pass --run-db-test to run against the configured database.");
        }
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String code = "TEST_" + UUID.randomUUID().toString().replace("-", "");
                int id;
                String insert = "INSERT INTO dbo.Promotion (PromotionCode, PromotionName, DiscountType, DiscountValue, StartDate, EndDate, Status, Quantity) "
                        + "OUTPUT INSERTED.PromotionID VALUES (?, N'Transaction test', 'Percentage', 10, DATEADD(day,-1,CURRENT_TIMESTAMP), DATEADD(day,1,CURRENT_TIMESTAMP), 'Active', 2)";
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, code);
                    try (ResultSet result = statement.executeQuery()) { result.next(); id = result.getInt(1); }
                }
                PromotionDAO promotions = new PromotionDAO();
                check(promotions.findAvailableByCode(connection, code, false).getQuantity() == 2, "Preview must not consume a coupon");
                Savepoint beforeOrder = connection.setSavepoint();
                promotions.findAvailableByCode(connection, code, true);
                promotions.consumeCoupon(connection, id);
                check(quantity(connection, id) == 1, "Consume one before Sales confirmation");
                connection.rollback(beforeOrder);
                check(quantity(connection, id) == 2, "Failed order rollback restores quantity");
                promotions.consumeCoupon(connection, id);
                promotions.consumeCoupon(connection, id);
                check(quantity(connection, id) == 0, "Consume last available coupon");
                try { promotions.consumeCoupon(connection, id); throw new AssertionError("Empty coupon should be rejected"); }
                catch (IllegalArgumentException expected) { }
                check(quantity(connection, id) == 0, "Quantity never becomes negative");
                try { promotions.findAvailableByCode(connection, code, false); throw new AssertionError("Empty coupon should not be available"); }
                catch (IllegalArgumentException expected) { }
                System.out.println("PASS SQL Server: preview does not consume, immediate consumption, rollback restoration, empty coupon rejection and nonnegative quantity. Test data rolled back.");
            } finally { connection.rollback(); }
        }
    }
    private static int quantity(Connection connection, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT Quantity FROM dbo.Promotion WHERE PromotionID=?")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
