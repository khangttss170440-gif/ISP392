package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.OrderRequest;
import model.OrderRequestLine;
import util.DBConnection;

public class WarehouseReturnDAO {

    public List<OrderRequest> getApprovedReturns() throws SQLException {
        String sql =
                "SELECT r.RequestID, r.OrderID, r.CustomerID, "
                + "c.FullName AS CustomerName, c.Phone AS CustomerPhone, "
                + "r.RequestType, r.Reason, r.RequestStatus, "
                + "r.RequestedAt, so.OrderStatus, r.StaffResponse "
                + "FROM dbo.CustomerOrderRequest r "
                + "INNER JOIN dbo.Customer c ON c.CustomerID = r.CustomerID "
                + "INNER JOIN dbo.SalesOrder so ON so.OrderID = r.OrderID "
                + "WHERE r.RequestType = 'RETURN' "
                + "AND r.RequestStatus = 'Approved' "
                + "ORDER BY r.RequestedAt DESC, r.RequestID DESC";

        List<OrderRequest> requests = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection()) {
            try (PreparedStatement statement =
                         connection.prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    requests.add(mapRequest(result));
                }
            }

            for (OrderRequest request : requests) {
                request.setLines(
                        getRequestLines(connection, request.getRequestID())
                );
            }
        }

        return requests;
    }

    public void receiveReturn(
            int requestID,
            int staffID,
            Map<Integer, Integer> receivedQuantities,
            Map<Integer, Integer> acceptedQuantities,
            String warehouseNote) throws SQLException {

        if (requestID <= 0 || staffID <= 0) {
            throw new IllegalArgumentException("Invalid request or staff ID.");
        }

        String note = warehouseNote == null
                ? null
                : warehouseNote.trim();

        if (note != null && note.isEmpty()) {
            note = null;
        }

        if (note != null && note.length() > 1000) {
            note = note.substring(0, 1000);
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                lockApprovedReturn(connection, requestID);

                Map<Integer, Integer> requested =
                        getRequestedQuantities(connection, requestID);

                validateQuantities(
                        requested,
                        receivedQuantities,
                        acceptedQuantities
                );

                saveReceivedQuantities(
                        connection,
                        requestID,
                        requested,
                        receivedQuantities,
                        acceptedQuantities
                );

                addAcceptedItemsToStock(connection, requestID);

                completeRequest(
                        connection,
                        requestID,
                        staffID,
                        note
                );

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

    private void lockApprovedReturn(
            Connection connection,
            int requestID) throws SQLException {

        String sql =
                "SELECT RequestStatus, RequestType "
                + "FROM dbo.CustomerOrderRequest WITH (UPDLOCK, ROWLOCK) "
                + "WHERE RequestID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requestID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("REQUEST_NOT_FOUND");
                }

                if (!"RETURN".equalsIgnoreCase(
                        result.getString("RequestType"))) {
                    throw new SQLException("REQUEST_IS_NOT_RETURN");
                }

                if (!"Approved".equalsIgnoreCase(
                        result.getString("RequestStatus"))) {
                    throw new SQLException("RETURN_NOT_APPROVED");
                }
            }
        }
    }

    private Map<Integer, Integer> getRequestedQuantities(
            Connection connection,
            int requestID) throws SQLException {

        String sql =
                "SELECT OrderDetailID, Quantity "
                + "FROM dbo.CustomerOrderRequestDetail "
                + "WITH (UPDLOCK, ROWLOCK) "
                + "WHERE RequestID = ?";

        Map<Integer, Integer> requested = new HashMap<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requestID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    requested.put(
                            result.getInt("OrderDetailID"),
                            result.getInt("Quantity")
                    );
                }
            }
        }

        if (requested.isEmpty()) {
            throw new SQLException("RETURN_HAS_NO_LINES");
        }

        return requested;
    }

    private void validateQuantities(
            Map<Integer, Integer> requested,
            Map<Integer, Integer> received,
            Map<Integer, Integer> accepted) throws SQLException {

        if (received == null || accepted == null
                || !requested.keySet().equals(received.keySet())
                || !requested.keySet().equals(accepted.keySet())) {
            throw new IllegalArgumentException("RETURN_LINES_MISMATCH");
        }

        for (Map.Entry<Integer, Integer> entry : requested.entrySet()) {
            int orderDetailID = entry.getKey();
            int requestedQuantity = entry.getValue();

            Integer receivedQuantity = received.get(orderDetailID);
            Integer acceptedQuantity = accepted.get(orderDetailID);

            if (receivedQuantity == null || acceptedQuantity == null
                    || receivedQuantity < 0
                    || receivedQuantity > requestedQuantity
                    || acceptedQuantity < 0
                    || acceptedQuantity > receivedQuantity) {
                throw new IllegalArgumentException(
                        "INVALID_RETURN_QUANTITY"
                );
            }
        }
    }

    private void saveReceivedQuantities(
            Connection connection,
            int requestID,
            Map<Integer, Integer> requested,
            Map<Integer, Integer> received,
            Map<Integer, Integer> accepted) throws SQLException {

        String sql =
                "UPDATE dbo.CustomerOrderRequestDetail "
                + "SET ReceivedQuantity = ?, AcceptedQuantity = ?, "
                + "    WarehouseNote = ? "
                + "WHERE RequestID = ? AND OrderDetailID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Integer orderDetailID : requested.keySet()) {
                statement.setInt(1, received.get(orderDetailID));
                statement.setInt(2, accepted.get(orderDetailID));
                statement.setString(3, null);
                statement.setInt(4, requestID);
                statement.setInt(5, orderDetailID);
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private void addAcceptedItemsToStock(
            Connection connection,
            int requestID) throws SQLException {

        String sql =
                "UPDATE pv "
                + "SET pv.Quantity = pv.Quantity + rd.AcceptedQuantity "
                + "FROM dbo.ProductVariant pv "
                + "INNER JOIN dbo.OrderDetail od "
                + "    ON od.VariantID = pv.VariantID "
                + "INNER JOIN dbo.CustomerOrderRequestDetail rd "
                + "    ON rd.OrderDetailID = od.OrderDetailID "
                + "WHERE rd.RequestID = ? "
                + "AND rd.AcceptedQuantity > 0";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requestID);
            statement.executeUpdate();
        }
    }

    private void completeRequest(
            Connection connection,
            int requestID,
            int staffID,
            String warehouseNote) throws SQLException {

        String sql =
                "UPDATE dbo.CustomerOrderRequest "
                + "SET RequestStatus = 'Completed', "
                + "    ReceivedByStaffID = ?, "
                + "    ReceivedAt = SYSDATETIME(), "
                + "    WarehouseNote = ? "
                + "WHERE RequestID = ? "
                + "AND RequestType = 'RETURN' "
                + "AND RequestStatus = 'Approved'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, staffID);
            statement.setString(2, warehouseNote);
            statement.setInt(3, requestID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("RETURN_COMPLETE_FAILED");
            }
        }
    }

    private List<OrderRequestLine> getRequestLines(
            Connection connection,
            int requestID) throws SQLException {

        String sql =
                "SELECT rd.OrderDetailID, p.ProductName, pv.SKU, "
                + "pv.Color, pv.Size, rd.Quantity "
                + "FROM dbo.CustomerOrderRequestDetail rd "
                + "INNER JOIN dbo.OrderDetail od "
                + "    ON od.OrderDetailID = rd.OrderDetailID "
                + "INNER JOIN dbo.ProductVariant pv "
                + "    ON pv.VariantID = od.VariantID "
                + "INNER JOIN dbo.Product p "
                + "    ON p.ProductID = pv.ProductID "
                + "WHERE rd.RequestID = ? "
                + "ORDER BY p.ProductName, pv.Color, pv.Size";

        List<OrderRequestLine> lines = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requestID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    OrderRequestLine line = new OrderRequestLine();

                    line.setOrderDetailID(
                            result.getInt("OrderDetailID")
                    );
                    line.setProductName(result.getString("ProductName"));
                    line.setSku(result.getString("SKU"));
                    line.setColor(result.getString("Color"));
                    line.setSize(result.getString("Size"));
                    line.setQuantity(result.getInt("Quantity"));

                    lines.add(line);
                }
            }
        }

        return lines;
    }

    private OrderRequest mapRequest(ResultSet result)
            throws SQLException {

        OrderRequest request = new OrderRequest();

        request.setRequestID(result.getInt("RequestID"));
        request.setOrderID(result.getInt("OrderID"));
        request.setCustomerID(result.getInt("CustomerID"));
        request.setCustomerName(result.getString("CustomerName"));
        request.setCustomerPhone(result.getString("CustomerPhone"));
        request.setRequestType(result.getString("RequestType"));
        request.setReason(result.getString("Reason"));
        request.setRequestStatus(result.getString("RequestStatus"));
        request.setOrderStatus(result.getString("OrderStatus"));
        request.setRequestedAt(result.getTimestamp("RequestedAt"));
        request.setStaffResponse(result.getString("StaffResponse"));

        return request;
    }
}
