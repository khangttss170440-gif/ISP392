package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.SalesOrder;
import util.DBConnection;

public class SalesOrderDAO {

    public List<SalesOrder> getPendingOrders() throws SQLException {
        String sql
                = "SELECT so.OrderID, "
                + "c.FullName AS CustomerName, "
                + "c.Phone AS CustomerPhone, "
                + "so.OrderDate, "
                + "so.OrderStatus, "
                + "so.DiscountAmount, "
                + "so.ShippingFee, "
                + "payment.PaymentMethod, "
                + "payment.PaymentStatus, "
                + "shipment.ShippingAddress, "
                + "COALESCE(details.Subtotal, 0) AS Subtotal, "
                + "COALESCE(details.ItemCount, 0) AS ItemCount, "
                + "(COALESCE(details.Subtotal, 0) "
                + " - so.DiscountAmount + so.ShippingFee) AS TotalAmount "
                + "FROM dbo.SalesOrder so "
                + "INNER JOIN dbo.Customer c "
                + "    ON c.CustomerID = so.CustomerID "
                + "OUTER APPLY ( "
                + "    SELECT SUM(od.Quantity * od.UnitPrice) AS Subtotal, "
                + "           SUM(od.Quantity) AS ItemCount "
                + "    FROM dbo.OrderDetail od "
                + "    WHERE od.OrderID = so.OrderID "
                + ") details "
                + "OUTER APPLY ( "
                + "    SELECT TOP (1) p.PaymentMethod, p.PaymentStatus "
                + "    FROM dbo.Payment p "
                + "    WHERE p.OrderID = so.OrderID "
                + "    ORDER BY p.PaymentID DESC "
                + ") payment "
                + "OUTER APPLY ( "
                + "    SELECT TOP (1) sh.ShippingAddress "
                + "    FROM dbo.Shipment sh "
                + "    WHERE sh.OrderID = so.OrderID "
                + "    ORDER BY sh.ShipmentID DESC "
                + ") shipment "
                + "WHERE so.OrderStatus = 'Pending' "
                + "AND ("
                + "    UPPER(LTRIM(RTRIM(payment.PaymentMethod))) = 'COD' "
                + "    OR (UPPER(LTRIM(RTRIM(payment.PaymentMethod))) = 'VNPAY' "
                + "        AND UPPER(LTRIM(RTRIM(payment.PaymentStatus))) = 'PAID')"
                + ") "
                + "ORDER BY so.OrderDate ASC, so.OrderID ASC";

        List<SalesOrder> orders = new ArrayList<>();

        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                SalesOrder order = new SalesOrder();

                order.setOrderID(result.getInt("OrderID"));
                order.setCustomerName(result.getString("CustomerName"));
                order.setCustomerPhone(result.getString("CustomerPhone"));
                order.setOrderDate(result.getTimestamp("OrderDate"));
                order.setOrderStatus(result.getString("OrderStatus"));
                order.setPaymentMethod(result.getString("PaymentMethod"));
                order.setPaymentStatus(result.getString("PaymentStatus"));
                order.setShippingAddress(
                        result.getString("ShippingAddress")
                );
                order.setSubtotal(result.getBigDecimal("Subtotal"));
                order.setTotalAmount(result.getBigDecimal("TotalAmount"));
                order.setItemCount(result.getInt("ItemCount"));

                orders.add(order);
            }
        }

        return orders;
    }

    public void processPendingOrder(
            int orderID,
            int staffID,
            String action,
            String cancellationReason) throws SQLException {

        if (orderID <= 0 || staffID <= 0) {
            throw new IllegalArgumentException(
                    "Order and staff IDs must be positive."
            );
        }

        String normalizedAction = action == null
                ? ""
                : action.trim().toLowerCase();

        if (!"confirm".equals(normalizedAction)
                && !"cancel".equals(normalizedAction)) {
            throw new IllegalArgumentException("Invalid order action.");
        }

        boolean cancel = "cancel".equals(normalizedAction);

        String reason = cancellationReason == null
                ? null
                : cancellationReason.trim();

        if (reason != null && reason.isEmpty()) {
            reason = null;
        }

        if (reason != null && reason.length() > 500) {
            reason = reason.substring(0, 500);
        }

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                OrderPayment payment = lockPendingOrder(
                        connection,
                        orderID
                );

                boolean eligibleForConfirmation
                        = "COD".equalsIgnoreCase(payment.method)
                        || ("VNPAY".equalsIgnoreCase(payment.method)
                        && "Paid".equalsIgnoreCase(payment.status));
                if (!cancel && !eligibleForConfirmation) {
                    throw new SQLException("PAYMENT_NOT_CONFIRMED");
                }

                if (cancel && "Paid".equalsIgnoreCase(payment.status)) {
                    throw new SQLException(
                            "PAID_ORDER_CANNOT_BE_CANCELLED"
                    );
                }

                String newStatus = cancel ? "Cancelled" : "Confirmed";

                updateOrderStatus(
                        connection,
                        orderID,
                        staffID,
                        newStatus,
                        cancel ? reason : null
                );

                if (cancel) {
                    restoreOrderStock(connection, orderID);
                    cancelPendingPayment(connection, orderID);
                    cancelPendingShipment(connection, orderID);
                    CustomerNotificationDAO notifications =
                            new CustomerNotificationDAO();
                    notifications.create(connection,
                            findCustomerID(connection, orderID), orderID,
                            "Đơn hàng #" + orderID + " đã bị hủy",
                            "Đơn hàng của bạn đã được cửa hàng hủy."
                                    + (reason == null ? ""
                                    : " Lý do: " + reason));
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

    private OrderPayment lockPendingOrder(
            Connection connection,
            int orderID) throws SQLException {

        String sql
                = "SELECT so.OrderStatus, payment.PaymentMethod, payment.PaymentStatus "
                + "FROM dbo.SalesOrder so WITH (UPDLOCK, ROWLOCK) "
                + "OUTER APPLY ( "
                + "    SELECT TOP (1) p.PaymentMethod, p.PaymentStatus "
                + "    FROM dbo.Payment p WITH (UPDLOCK, ROWLOCK) "
                + "    WHERE p.OrderID = so.OrderID "
                + "    ORDER BY p.PaymentID DESC "
                + ") payment "
                + "WHERE so.OrderID = ?";

        try (PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, orderID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("ORDER_NOT_FOUND");
                }

                String status = result.getString("OrderStatus");

                if (!"Pending".equalsIgnoreCase(status)) {
                    throw new SQLException("ORDER_NOT_PENDING");
                }

                return new OrderPayment(
                        result.getString("PaymentMethod"),
                        result.getString("PaymentStatus")
                );
            }
        }
    }

    private static final class OrderPayment {
        private final String method;
        private final String status;

        private OrderPayment(String method, String status) {
            this.method = method;
            this.status = status;
        }
    }

    private void updateOrderStatus(
            Connection connection,
            int orderID,
            int staffID,
            String status,
            String cancellationReason) throws SQLException {

        String sql
                = "UPDATE dbo.SalesOrder "
                + "SET OrderStatus = ?, "
                + "    StaffID = ?, "
                + "    UpdatedAt = SYSDATETIME(), "
                + "    Notes = CASE "
                + "        WHEN ? IS NULL THEN Notes "
                + "        ELSE LEFT("
                + "            CONVERT(nvarchar(max), "
                + "                COALESCE("
                + "                    Notes + NCHAR(13) + NCHAR(10), N''"
                + "                ) + N'Ly do huy: ' + ?"
                + "            ), 1000"
                + "        ) "
                + "    END "
                + "WHERE OrderID = ? AND OrderStatus = 'Pending'";

        try (PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setString(1, status);
            statement.setInt(2, staffID);
            statement.setString(3, cancellationReason);
            statement.setString(4, cancellationReason);
            statement.setInt(5, orderID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("ORDER_UPDATE_FAILED");
            }
        }
    }

    private void restoreOrderStock(
            Connection connection,
            int orderID) throws SQLException {

        String sql
                = "UPDATE pv "
                + "SET pv.Quantity = pv.Quantity + details.TotalQuantity "
                + "FROM dbo.ProductVariant pv "
                + "INNER JOIN ( "
                + "    SELECT VariantID, SUM(Quantity) AS TotalQuantity "
                + "    FROM dbo.OrderDetail "
                + "    WHERE OrderID = ? "
                + "    GROUP BY VariantID "
                + ") details ON details.VariantID = pv.VariantID";

        try (PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, orderID);
            statement.executeUpdate();
        }
    }

    private void cancelPendingPayment(
            Connection connection,
            int orderID) throws SQLException {

        String sql
                = "UPDATE dbo.Payment "
                + "SET PaymentStatus = 'Failed' "
                + "WHERE OrderID = ? AND PaymentStatus = 'Pending'";

        try (PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, orderID);
            statement.executeUpdate();
        }
    }

    private void cancelPendingShipment(
            Connection connection,
            int orderID) throws SQLException {

        String sql
                = "UPDATE dbo.Shipment "
                + "SET ShipmentStatus = 'Cancelled' "
                + "WHERE OrderID = ? AND ShipmentStatus = 'Pending'";

        try (PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, orderID);
            statement.executeUpdate();
        }
    }

    private int findCustomerID(Connection connection, int orderID)
            throws SQLException {
        String sql = "SELECT CustomerID FROM dbo.SalesOrder WHERE OrderID = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("ORDER_NOT_FOUND");
                return result.getInt("CustomerID");
            }
        }
    }

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }
}
