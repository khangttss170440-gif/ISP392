package dao;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import model.Cart;
import model.ProductVariant;

/** JDBC doubles verify the actual checkout path without creating real orders. */
public class CheckoutCouponTransactionTest {
    public static void main(String[] args) throws Exception {
        Database successful = new Database();
        checkout(successful, "TEST10");
        check(successful.committed && successful.quantity == 0, "Order commits exactly one coupon usage immediately");
        check(successful.discount.compareTo(new BigDecimal("10000")) == 0, "Discount saved on Pending order");
        check(successful.payment.compareTo(new BigDecimal("135000")) == 0, "COD payment includes the discounted amount plus 45000 shipping");
        check(successful.shippingFee.intValue() == 45000, "Order stores the nationwide shipping fee");
        Database nearby = new Database(); checkout(nearby, "TEST10", BigDecimal.ZERO);
        check(nearby.payment.intValue() == 90000 && nearby.shippingFee.signum() == 0, "Nearby order stores zero shipping and discounted payment");
        try { checkout(new Database(), "", new BigDecimal("1")); throw new AssertionError("Invalid policy fee accepted"); }
        catch (IllegalArgumentException expected) { }
        Database failed = new Database(); failed.failPayment = true;
        try { checkout(failed, "TEST10"); throw new AssertionError("Payment insertion should fail"); }
        catch (SQLException expected) { }
        check(failed.rolledBack && !failed.committed && failed.quantity == 1, "Failure after consumption restores coupon with order rollback");
        Database empty = new Database(); empty.quantity = 0;
        try { checkout(empty, "TEST10"); throw new AssertionError("Empty coupon should reject order"); }
        catch (IllegalArgumentException expected) { }
        check(empty.rolledBack && !empty.committed && empty.quantity == 0, "Empty coupon does not create an order");
        Database belowMinimum = new Database(); belowMinimum.minimum = new BigDecimal("200000");
        try { checkout(belowMinimum, "TEST10"); throw new AssertionError("Below-minimum order should fail"); }
        catch (IllegalArgumentException expected) { }
        check(belowMinimum.quantity == 1 && belowMinimum.rolledBack, "Invalid order does not consume coupon");
        Database noCoupon = new Database(); checkout(noCoupon, "");
        check(noCoupon.committed && noCoupon.quantity == 1 && noCoupon.discount.signum() == 0, "Order without coupon does not consume quantity");
        System.out.println("PASS checkout: immediate one-use consumption, discounted payment, rollback on failure, exhausted coupon, minimum order and no-coupon order.");
    }
    private static void checkout(Database database, String code) throws SQLException {
        checkout(database, code, service.ShippingService.NATIONAL_FEE);
    }
    private static void checkout(Database database, String code, BigDecimal shippingFee) throws SQLException {
        ProductVariant variant = new ProductVariant(); variant.setVariantID(1); variant.setProductID(1); variant.setPrice(new BigDecimal("100000"));
        Cart cart = new Cart(); cart.addItem(variant, 1);
        new CheckoutDAO() { @Override protected Connection getConnection() { return database.connection(); } }
                .createCodOrder(1, cart, "Test shipping address", "", code, shippingFee);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static class Database {
        int quantity = 1, initialQuantity;
        boolean autoCommit = true, committed, rolledBack, failPayment;
        BigDecimal discount, payment, minimum, shippingFee;
        Connection connection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (object, method, args) -> {
                switch (method.getName()) {
                    case "setAutoCommit": autoCommit = (Boolean) args[0]; initialQuantity = quantity; return null;
                    case "getAutoCommit": return autoCommit;
                    case "prepareStatement": return statement((String) args[0]);
                    case "commit": committed = true; return null;
                    case "rollback": rolledBack = true; quantity = initialQuantity; return null;
                    default: return null;
                }
            });
        }
        PreparedStatement statement(String sql) {
            Map<Integer, Object> parameters = new HashMap<>();
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (object, method, args) -> {
                if (method.getName().startsWith("set")) { parameters.put((Integer) args[0], args[1]); return null; }
                if ("executeQuery".equals(method.getName())) {
                    Map<String, Object> row = new HashMap<>();
                    if (sql.contains("FROM dbo.ProductVariant")) {
                        row.put("Price", new BigDecimal("100000")); row.put("Quantity", 10);
                    } else if (sql.contains("FROM dbo.Promotion")) {
                        check(sql.contains("UPDLOCK") && sql.contains("Quantity > 0"), "Promotion read locks available quantity");
                        if (quantity == 0) return result(null);
                        row.put("PromotionID", 7); row.put("PromotionCode", "TEST10"); row.put("DiscountType", "Percentage");
                        row.put("DiscountValue", new BigDecimal("10")); row.put("MinOrderValue", minimum); row.put("Status", "Active"); row.put("Quantity", quantity);
                    } else if (sql.startsWith("INSERT INTO dbo.SalesOrder")) {
                        discount = (BigDecimal) parameters.get(2); shippingFee = (BigDecimal) parameters.get(3); row.put("OrderID", 555);
                    } else throw new AssertionError("Unexpected query: " + sql);
                    return result(row);
                }
                if ("executeUpdate".equals(method.getName())) {
                    if (sql.startsWith("UPDATE dbo.Promotion")) {
                        check(sql.contains("Quantity = Quantity - 1") && sql.contains("Quantity > 0"), "Atomic nonnegative decrement");
                        if (quantity == 0) return 0; quantity--;
                    }
                    if (sql.startsWith("INSERT INTO dbo.Payment")) {
                        if (failPayment) throw new SQLException("Simulated payment failure");
                        payment = (BigDecimal) parameters.get(2);
                    }
                    return 1;
                }
                return null;
            });
        }
        ResultSet result(Map<String, Object> row) {
            boolean[] read = {false};
            return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (object, method, args) -> {
                if ("next".equals(method.getName())) { if (read[0] || row == null) return false; read[0] = true; return true; }
                if (method.getName().startsWith("get")) {
                    Object value = row.get(args[0]);
                    return value == null && "getInt".equals(method.getName()) ? 0 : value;
                }
                return null;
            });
        }
    }
}
