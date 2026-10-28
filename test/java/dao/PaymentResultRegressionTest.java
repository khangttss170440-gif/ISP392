package dao;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/** No database or gateway calls. Verifies status transitions and notification deduplication. */
public final class PaymentResultRegressionTest {
    private static String status = "Pending";
    private static int paymentUpdates;

    public static void main(String[] args) throws Exception {
        CheckoutDAO dao = new CheckoutDAO() {
            @Override protected Connection getConnection() { return connection(); }
        };
        boolean[] newlyPaid = new boolean[1];
        require(dao.recordVnPayResult(1, "TX1", "00", "00", new BigDecimal("100"), "", newlyPaid));
        require("Paid".equals(status) && newlyPaid[0] && paymentUpdates == 1);
        require(dao.recordVnPayResult(1, "TX1", "00", "00", new BigDecimal("100"), "", newlyPaid));
        require(!newlyPaid[0] && paymentUpdates == 1);
        status = "Pending";
        require(dao.recordVnPayResult(1, "TX2", "24", "02", new BigDecimal("100"), "", newlyPaid));
        require("Failed".equals(status) && !newlyPaid[0] && paymentUpdates == 2);
        require(!dao.recordVnPayResult(1, "TX3", "00", "00", new BigDecimal("999"), "", newlyPaid));
        require(paymentUpdates == 2 && !newlyPaid[0]);
        System.out.println("Payment status/idempotency regression checks passed: 8");
    }

    private static Connection connection() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) return statement((String) args[0]);
                    return defaultValue(method.getReturnType());
                });
    }
    private static PreparedStatement statement(String sql) {
        Map<Integer,Object> values = new HashMap<>();
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("set")) { values.put((Integer) args[0], args[1]); return null; }
                    if (method.getName().equals("executeQuery")) return row();
                    if (method.getName().equals("executeUpdate")) {
                        if (sql.startsWith("UPDATE dbo.Payment SET")) { status = (String) values.get(1); paymentUpdates++; }
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                });
    }
    private static ResultSet row() {
        boolean[] next = {true};
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next": boolean value = next[0]; next[0] = false; return value;
                        case "getInt": return 1;
                        case "getString": return "PaymentMethod".equals(args[0]) ? "VNPAY" : status;
                        case "getBigDecimal": return new BigDecimal("100");
                        default: return defaultValue(method.getReturnType());
                    }
                });
    }
    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
    private static void require(boolean condition) { if (!condition) throw new AssertionError("Payment regression"); }
}
