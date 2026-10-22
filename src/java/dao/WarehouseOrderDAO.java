package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.OrderLine;
import model.WarehouseOrder;
import util.DBConnection;

public class WarehouseOrderDAO {

    public List<WarehouseOrder> getWorkOrders() throws SQLException {
        String sql
                = "SELECT so.OrderID, c.FullName AS CustomerName, "
                + "c.Phone AS CustomerPhone, so.OrderDate, "
                + "so.OrderStatus, sh.ShippingAddress, "
                + "sh.ShipmentStatus, sh.TrackingNumber, sh.Carrier "
                + "FROM dbo.SalesOrder so "
                + "INNER JOIN dbo.Customer c "
                + "    ON c.CustomerID = so.CustomerID "
                + "LEFT JOIN dbo.Shipment sh "
                + "    ON sh.OrderID = so.OrderID "
                + "WHERE so.OrderStatus IN ('Confirmed', 'Shipped') "
                + "ORDER BY so.OrderDate ASC, so.OrderID ASC";

        List<WarehouseOrder> orders = new ArrayList<>();

        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                orders.add(mapOrder(result));
            }
        }

        return orders;
    }

    public List<WarehouseOrder> getReportableOrders() throws SQLException {
        String sql
                = "SELECT so.OrderID, c.FullName AS CustomerName, "
                + "c.Phone AS CustomerPhone, so.OrderDate, "
                + "so.OrderStatus, sh.ShippingAddress, "
                + "sh.ShipmentStatus, sh.TrackingNumber, sh.Carrier "
                + "FROM dbo.SalesOrder so "
                + "INNER JOIN dbo.Customer c "
                + "    ON c.CustomerID = so.CustomerID "
                + "LEFT JOIN dbo.Shipment sh "
                + "    ON sh.OrderID = so.OrderID "
                + "WHERE UPPER(so.OrderStatus) IN ('CONFIRMED', 'PROCESSING', 'PACKING') "
                + "ORDER BY so.OrderDate ASC, so.OrderID ASC";

        List<WarehouseOrder> orders = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                orders.add(mapOrder(result));
            }
        }

        return orders;
    }

    public WarehouseOrder getOrderForWarehouse(int orderID)
            throws SQLException {

        String sql
                = "SELECT TOP (1) so.OrderID, c.FullName AS CustomerName, "
                + "c.Phone AS CustomerPhone, so.OrderDate, "
                + "so.OrderStatus, sh.ShippingAddress, "
                + "sh.ShipmentStatus, sh.TrackingNumber, sh.Carrier "
                + "FROM dbo.SalesOrder so "
                + "INNER JOIN dbo.Customer c "
                + "    ON c.CustomerID = so.CustomerID "
                + "LEFT JOIN dbo.Shipment sh "
                + "    ON sh.OrderID = so.OrderID "
                + "WHERE so.OrderID = ? "
                + "AND so.OrderStatus IN ('Confirmed', 'Shipped', 'Delivered') "
                + "ORDER BY sh.ShipmentID DESC";

        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, orderID);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return mapOrder(result);
                }
            }
        }

        return null;
    }

    public List<OrderLine> getOrderLines(int orderID) throws SQLException {
        String sql
                = "SELECT od.VariantID, p.ProductName, pv.SKU, "
                + "pv.Color, pv.Size, od.Quantity, od.UnitPrice, "
                + "(od.Quantity * od.UnitPrice) AS LineTotal "
                + "FROM dbo.OrderDetail od "
                + "INNER JOIN dbo.ProductVariant pv "
                + "    ON pv.VariantID = od.VariantID "
                + "INNER JOIN dbo.Product p "
                + "    ON p.ProductID = pv.ProductID "
                + "WHERE od.OrderID = ? "
                + "ORDER BY p.ProductName, pv.Color, pv.Size";

        List<OrderLine> lines = new ArrayList<>();

        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, orderID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    OrderLine line = new OrderLine();

                    line.setVariantID(result.getInt("VariantID"));
                    line.setProductName(result.getString("ProductName"));
                    line.setSku(result.getString("SKU"));
                    line.setColor(result.getString("Color"));
                    line.setSize(result.getString("Size"));
                    line.setQuantity(result.getInt("Quantity"));
                    line.setUnitPrice(result.getBigDecimal("UnitPrice"));
                    line.setLineTotal(result.getBigDecimal("LineTotal"));

                    lines.add(line);
                }
            }
        }

        return lines;
    }

    public void processShipment(
            int orderID,
            String action,
            String carrier,
            String trackingNumber) throws SQLException {

        if (orderID <= 0) {
            throw new IllegalArgumentException("Order ID must be positive.");
        }

        String normalizedAction = action == null
                ? ""
                : action.trim().toLowerCase();

        if (!"ship".equals(normalizedAction)
                && !"deliver".equals(normalizedAction)) {
            throw new IllegalArgumentException("Invalid shipment action.");
        }

        String safeCarrier = trimToNull(carrier);
        String safeTrackingNumber = trimToNull(trackingNumber);

        if ("ship".equals(normalizedAction)) {
            if (safeCarrier == null) {
                throw new IllegalArgumentException(
                        "Hãy nhập hãng vận chuyển."
                );
            }

            if (safeCarrier.length() > 100) {
                throw new IllegalArgumentException(
                        "Tên hãng vận chuyển tối đa 100 ký tự."
                );
            }

            if (safeTrackingNumber != null
                    && safeTrackingNumber.length() > 100) {
                throw new IllegalArgumentException(
                        "Mã vận đơn tối đa 100 ký tự."
                );
            }
        }

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                String[] currentState = lockOrderAndShipment(
                        connection,
                        orderID
                );

                GhnDeliveryDAO.guardManual(connection, orderID);

                if ("ship".equals(normalizedAction)) {
                    shipOrder(
                            connection,
                            orderID,
                            currentState[0],
                            currentState[1],
                            safeCarrier,
                            safeTrackingNumber
                    );
                } else {
                    deliverOrder(
                            connection,
                            orderID,
                            currentState[0],
                            currentState[1]
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

    private String[] lockOrderAndShipment(
            Connection connection,
            int orderID) throws SQLException {

        String sql
                = "SELECT so.OrderStatus, sh.ShipmentStatus "
                + "FROM dbo.SalesOrder so WITH (UPDLOCK, ROWLOCK) "
                + "LEFT JOIN dbo.Shipment sh WITH (UPDLOCK, ROWLOCK) "
                + "    ON sh.OrderID = so.OrderID "
                + "WHERE so.OrderID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("ORDER_NOT_FOUND");
                }

                String shipmentStatus = result.getString("ShipmentStatus");

                if (shipmentStatus == null) {
                    throw new SQLException("SHIPMENT_NOT_FOUND");
                }

                return new String[]{
                    result.getString("OrderStatus"),
                    shipmentStatus
                };
            }
        }
    }

    private void shipOrder(
            Connection connection,
            int orderID,
            String orderStatus,
            String shipmentStatus,
            String carrier,
            String trackingNumber) throws SQLException {

        if (!"Confirmed".equalsIgnoreCase(orderStatus)) {
            throw new SQLException("ORDER_NOT_READY_TO_SHIP");
        }

        if (!"Pending".equalsIgnoreCase(shipmentStatus)) {
            throw new SQLException("SHIPMENT_NOT_PENDING");
        }

        String shipmentSql
                = "UPDATE dbo.Shipment "
                + "SET Carrier = ?, TrackingNumber = ?, "
                + "    ShipmentStatus = 'Shipped', "
                + "    ShippedDate = SYSDATETIME() "
                + "WHERE OrderID = ? AND ShipmentStatus = 'Pending'";

        try (PreparedStatement statement
                = connection.prepareStatement(shipmentSql)) {

            statement.setString(1, carrier);
            statement.setString(2, trackingNumber);
            statement.setInt(3, orderID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("SHIPMENT_UPDATE_FAILED");
            }
        }

        updateOrderStatus(
                connection,
                orderID,
                "Shipped",
                "Confirmed"
        );
    }

    private void deliverOrder(
            Connection connection,
            int orderID,
            String orderStatus,
            String shipmentStatus) throws SQLException {

        if (!"Shipped".equalsIgnoreCase(orderStatus)
                || !"Shipped".equalsIgnoreCase(shipmentStatus)) {
            throw new SQLException("ORDER_NOT_SHIPPED");
        }

        String shipmentSql
                = "UPDATE dbo.Shipment "
                + "SET ShipmentStatus = 'Delivered', "
                + "    DeliveredDate = SYSDATETIME() "
                + "WHERE OrderID = ? AND ShipmentStatus = 'Shipped'";

        try (PreparedStatement statement
                = connection.prepareStatement(shipmentSql)) {

            statement.setInt(1, orderID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("SHIPMENT_UPDATE_FAILED");
            }
        }

        completeCodPayment(connection, orderID);

        updateOrderStatus(
                connection,
                orderID,
                "Delivered",
                "Shipped"
        );
    }

    private void completeCodPayment(Connection connection, int orderID)
            throws SQLException {

        String sql
                = "UPDATE dbo.Payment "
                + "SET PaymentStatus = 'Paid', "
                + "    PaymentDate = COALESCE(PaymentDate, SYSDATETIME()) "
                + "WHERE OrderID = ? "
                + "AND UPPER(LTRIM(RTRIM(PaymentMethod))) IN ('COD', 'CASH ON DELIVERY') "
                + "AND UPPER(LTRIM(RTRIM(PaymentStatus))) = 'PENDING'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.executeUpdate();
        }
    }

    private void updateOrderStatus(
            Connection connection,
            int orderID,
            String newStatus,
            String expectedStatus) throws SQLException {

        String sql
                = "UPDATE dbo.SalesOrder "
                + "SET OrderStatus = ?, UpdatedAt = SYSDATETIME() "
                + "WHERE OrderID = ? AND OrderStatus = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newStatus);
            statement.setInt(2, orderID);
            statement.setString(3, expectedStatus);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("ORDER_STATUS_UPDATE_FAILED");
            }
        }
    }

    private WarehouseOrder mapOrder(ResultSet result)
            throws SQLException {

        WarehouseOrder order = new WarehouseOrder();

        order.setOrderID(result.getInt("OrderID"));
        order.setCustomerName(result.getString("CustomerName"));
        order.setCustomerPhone(result.getString("CustomerPhone"));
        order.setOrderDate(result.getTimestamp("OrderDate"));
        order.setOrderStatus(result.getString("OrderStatus"));
        order.setShippingAddress(result.getString("ShippingAddress"));
        order.setShipmentStatus(result.getString("ShipmentStatus"));
        order.setTrackingNumber(result.getString("TrackingNumber"));
        order.setCarrier(result.getString("Carrier"));

        return order;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }
}
