package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import model.RequestableOrderLine;
import util.DBConnection;

public class CustomerOrderRequestDAO {

    public ArrayList<RequestableOrderLine> getRequestableLines(
            int customerID,
            int orderID) throws SQLException {

        String sql =
                "SELECT od.OrderDetailID, p.ProductName, pv.SKU, "
                + "pv.Color, pv.Size, od.Quantity AS OrderedQuantity, "
                + "(od.Quantity - COALESCE(used.ReservedQuantity, 0)) "
                + "AS AvailableQuantity "
                + "FROM dbo.SalesOrder so "
                + "INNER JOIN dbo.OrderDetail od ON od.OrderID = so.OrderID "
                + "INNER JOIN dbo.ProductVariant pv "
                + "    ON pv.VariantID = od.VariantID "
                + "INNER JOIN dbo.Product p ON p.ProductID = pv.ProductID "
                + "OUTER APPLY ( "
                + "    SELECT SUM(rd.Quantity) AS ReservedQuantity "
                + "    FROM dbo.CustomerOrderRequestDetail rd "
                + "    INNER JOIN dbo.CustomerOrderRequest r "
                + "        ON r.RequestID = rd.RequestID "
                + "    WHERE rd.OrderDetailID = od.OrderDetailID "
                + "      AND r.RequestStatus IN "
                + "          ('Pending', 'Approved', 'Completed') "
                + ") used "
                + "WHERE so.CustomerID = ? "
                + "  AND so.OrderID = ? "
                + "  AND so.OrderStatus = 'Delivered' "
                + "  AND od.Quantity > COALESCE(used.ReservedQuantity, 0) "
                + "ORDER BY p.ProductName, pv.Color, pv.Size";

        ArrayList<RequestableOrderLine> lines = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, customerID);
            statement.setInt(2, orderID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    RequestableOrderLine line =
                            new RequestableOrderLine();

                    line.setOrderDetailID(
                            result.getInt("OrderDetailID")
                    );
                    line.setProductName(result.getString("ProductName"));
                    line.setSku(result.getString("SKU"));
                    line.setColor(result.getString("Color"));
                    line.setSize(result.getString("Size"));
                    line.setOrderedQuantity(
                            result.getInt("OrderedQuantity")
                    );
                    line.setAvailableQuantity(
                            result.getInt("AvailableQuantity")
                    );

                    lines.add(line);
                }
            }
        }

        return lines;
    }

    public int createRequest(
            int customerID,
            int orderID,
            String requestType,
            String reason,
            Map<Integer, Integer> requestedLines) throws SQLException {

        String type = requestType == null
                ? ""
                : requestType.trim().toUpperCase();

        if (!"CANCEL".equals(type)
                && !"RETURN".equals(type)
                && !"EXCHANGE".equals(type)) {
            throw new IllegalArgumentException("INVALID_REQUEST_TYPE");
        }

        if (customerID <= 0 || orderID <= 0) {
            throw new IllegalArgumentException("INVALID_ORDER");
        }

        if (reason == null || reason.trim().isEmpty()
                || reason.trim().length() > 1000) {
            throw new IllegalArgumentException("INVALID_REASON");
        }

        Map<Integer, Integer> lines = requestedLines == null
                ? new TreeMap<Integer, Integer>()
                : new TreeMap<Integer, Integer>(requestedLines);

        boolean needsLines = "RETURN".equals(type)
                || "EXCHANGE".equals(type);

        if (needsLines && lines.isEmpty()) {
            throw new IllegalArgumentException("REQUEST_LINES_REQUIRED");
        }

        if (!needsLines && !lines.isEmpty()) {
            throw new IllegalArgumentException("CANCEL_HAS_NO_LINES");
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String orderStatus = lockOrder(
                        connection,
                        customerID,
                        orderID
                );

                validateOrderStatus(type, orderStatus);

                if (hasPendingRequest(connection, orderID, type)) {
                    throw new SQLException("PENDING_REQUEST_EXISTS");
                }

                if (needsLines) {
                    for (Map.Entry<Integer, Integer> line : lines.entrySet()) {
                        validateRequestedQuantity(
                                connection,
                                customerID,
                                orderID,
                                line.getKey(),
                                line.getValue()
                        );
                    }
                }

                int requestID = insertRequest(
                        connection,
                        customerID,
                        orderID,
                        type,
                        reason.trim()
                );

                for (Map.Entry<Integer, Integer> line : lines.entrySet()) {
                    insertRequestLine(
                            connection,
                            requestID,
                            line.getKey(),
                            line.getValue()
                    );
                }

                connection.commit();
                return requestID;

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

    private String lockOrder(
            Connection connection,
            int customerID,
            int orderID) throws SQLException {

        String sql =
                "SELECT OrderStatus "
                + "FROM dbo.SalesOrder WITH (UPDLOCK, ROWLOCK) "
                + "WHERE OrderID = ? AND CustomerID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.setInt(2, customerID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("ORDER_NOT_FOUND_OR_NOT_OWNED");
                }

                return result.getString("OrderStatus");
            }
        }
    }

    private void validateOrderStatus(
            String requestType,
            String orderStatus) throws SQLException {

        if ("CANCEL".equals(requestType)) {
            if (!"Pending".equalsIgnoreCase(orderStatus)
                    && !"Confirmed".equalsIgnoreCase(orderStatus)) {
                throw new SQLException("ORDER_NOT_CANCELLABLE");
            }
            return;
        }

        if (!"Delivered".equalsIgnoreCase(orderStatus)) {
            throw new SQLException("ORDER_NOT_DELIVERED");
        }
    }

    private boolean hasPendingRequest(
            Connection connection,
            int orderID,
            String requestType) throws SQLException {

        String sql =
                "SELECT 1 "
                + "FROM dbo.CustomerOrderRequest WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE OrderID = ? "
                + "AND RequestType = ? "
                + "AND RequestStatus = 'Pending'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.setString(2, requestType);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void validateRequestedQuantity(
            Connection connection,
            int customerID,
            int orderID,
            int orderDetailID,
            int requestedQuantity) throws SQLException {

        if (orderDetailID <= 0 || requestedQuantity <= 0) {
            throw new IllegalArgumentException("INVALID_REQUEST_LINE");
        }

        String sql =
                "SELECT od.Quantity "
                + "- COALESCE(used.ReservedQuantity, 0) AS AvailableQuantity "
                + "FROM dbo.OrderDetail od WITH (UPDLOCK, ROWLOCK) "
                + "INNER JOIN dbo.SalesOrder so "
                + "    ON so.OrderID = od.OrderID "
                + "OUTER APPLY ( "
                + "    SELECT SUM(rd.Quantity) AS ReservedQuantity "
                + "    FROM dbo.CustomerOrderRequestDetail rd "
                + "    INNER JOIN dbo.CustomerOrderRequest r "
                + "        ON r.RequestID = rd.RequestID "
                + "    WHERE rd.OrderDetailID = od.OrderDetailID "
                + "      AND r.RequestStatus IN "
                + "          ('Pending', 'Approved', 'Completed') "
                + ") used "
                + "WHERE od.OrderDetailID = ? "
                + "  AND od.OrderID = ? "
                + "  AND so.CustomerID = ? "
                + "  AND so.OrderStatus = 'Delivered'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderDetailID);
            statement.setInt(2, orderID);
            statement.setInt(3, customerID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("INVALID_ORDER_LINE");
                }

                int available = result.getInt("AvailableQuantity");

                if (requestedQuantity > available) {
                    throw new SQLException("QUANTITY_EXCEEDS_AVAILABLE");
                }
            }
        }
    }

    private int insertRequest(
            Connection connection,
            int customerID,
            int orderID,
            String requestType,
            String reason) throws SQLException {

        String sql =
                "INSERT INTO dbo.CustomerOrderRequest "
                + "(OrderID, CustomerID, RequestType, Reason) "
                + "OUTPUT INSERTED.RequestID "
                + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.setInt(2, customerID);
            statement.setString(3, requestType);
            statement.setString(4, reason);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("RequestID");
                }
            }
        }

        throw new SQLException("REQUEST_CREATE_FAILED");
    }

    private void insertRequestLine(
            Connection connection,
            int requestID,
            int orderDetailID,
            int quantity) throws SQLException {

        String sql =
                "INSERT INTO dbo.CustomerOrderRequestDetail "
                + "(RequestID, OrderDetailID, Quantity) "
                + "VALUES (?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requestID);
            statement.setInt(2, orderDetailID);
            statement.setInt(3, quantity);
            statement.executeUpdate();
        }
    }
}