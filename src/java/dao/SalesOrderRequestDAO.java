package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.OrderRequest;
import model.OrderRequestLine;
import util.DBConnection;

public class SalesOrderRequestDAO {

    public List<OrderRequest> getPendingRequests() throws SQLException {
        String sql =
                "SELECT r.RequestID, r.OrderID, r.CustomerID, "
                + "c.FullName AS CustomerName, c.Phone AS CustomerPhone, "
                + "r.RequestType, r.Reason, r.RequestStatus, "
                + "r.RequestedAt, so.OrderStatus, r.StaffResponse "
                + "FROM dbo.CustomerOrderRequest r "
                + "INNER JOIN dbo.Customer c ON c.CustomerID = r.CustomerID "
                + "INNER JOIN dbo.SalesOrder so ON so.OrderID = r.OrderID "
                + "WHERE r.RequestStatus = 'Pending' "
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

            for (OrderRequest orderRequest : requests) {
                orderRequest.setLines(
                        getRequestLines(connection, orderRequest.getRequestID())
                );
            }
        }

        return requests;
    }

    public void processRequest(
            int requestID,
            int staffID,
            String action,
            String staffResponse) throws SQLException {

        if (requestID <= 0 || staffID <= 0) {
            throw new IllegalArgumentException("Invalid request or staff ID.");
        }

        String normalizedAction = action == null
                ? ""
                : action.trim().toLowerCase();

        if (!"approve".equals(normalizedAction)
                && !"reject".equals(normalizedAction)) {
            throw new IllegalArgumentException("Invalid request action.");
        }

        String responseText = staffResponse == null
                ? null
                : staffResponse.trim();

        if (responseText != null && responseText.isEmpty()) {
            responseText = null;
        }

        if (responseText != null && responseText.length() > 1000) {
            responseText = responseText.substring(0, 1000);
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                RequestState state = lockRequest(connection, requestID);

                if (!"Pending".equalsIgnoreCase(state.requestStatus)) {
                    throw new SQLException("REQUEST_NOT_PENDING");
                }

                if ("approve".equals(normalizedAction)) {
                    approveRequest(connection, state, staffID);
                }

                updateRequest(
                        connection,
                        requestID,
                        staffID,
                        "approve".equals(normalizedAction)
                                ? "Approved"
                                : "Rejected",
                        responseText
                );

                String requestLabel = "CANCEL".equalsIgnoreCase(state.requestType)
                        ? "hủy đơn" : "RETURN".equalsIgnoreCase(state.requestType)
                        ? "trả hàng" : "đổi hàng";
                String decision = "approve".equals(normalizedAction)
                        ? "đã chấp nhận" : "chưa thể chấp nhận";
                new CustomerNotificationDAO().create(connection,
                        state.customerID, state.orderID,
                        "Cập nhật yêu cầu " + requestLabel + " cho đơn #" + state.orderID,
                        "Cửa hàng " + decision + " yêu cầu " + requestLabel + "."
                                + (responseText == null ? ""
                                : " Phản hồi: " + responseText));

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

    private RequestState lockRequest(
            Connection connection,
            int requestID) throws SQLException {

        String sql =
                "SELECT r.RequestStatus, r.RequestType, r.OrderID, r.CustomerID, "
                + "so.OrderStatus, payment.PaymentStatus "
                + "FROM dbo.CustomerOrderRequest r WITH (UPDLOCK, ROWLOCK) "
                + "INNER JOIN dbo.SalesOrder so WITH (UPDLOCK, ROWLOCK) "
                + "    ON so.OrderID = r.OrderID "
                + "OUTER APPLY ( "
                + "    SELECT TOP (1) p.PaymentStatus "
                + "    FROM dbo.Payment p WITH (UPDLOCK, ROWLOCK) "
                + "    WHERE p.OrderID = so.OrderID "
                + "    ORDER BY p.PaymentID DESC "
                + ") payment "
                + "WHERE r.RequestID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requestID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("REQUEST_NOT_FOUND");
                }

                RequestState state = new RequestState();
                state.requestStatus = result.getString("RequestStatus");
                state.requestType = result.getString("RequestType");
                state.orderID = result.getInt("OrderID");
                state.customerID = result.getInt("CustomerID");
                state.orderStatus = result.getString("OrderStatus");
                state.paymentStatus = result.getString("PaymentStatus");

                return state;
            }
        }
    }

    private void approveRequest(
            Connection connection,
            RequestState state,
            int staffID) throws SQLException {

        if ("CANCEL".equalsIgnoreCase(state.requestType)) {
            approveCancellation(connection, state, staffID);
            return;
        }

        if ("RETURN".equalsIgnoreCase(state.requestType)
                || "EXCHANGE".equalsIgnoreCase(state.requestType)) {

            if (!"Delivered".equalsIgnoreCase(state.orderStatus)) {
                throw new SQLException("RETURN_ORDER_NOT_DELIVERED");
            }

            // Kho sẽ tiếp nhận và cập nhật tồn sau khi nhận hàng thực tế.
            return;
        }

        throw new SQLException("INVALID_REQUEST_TYPE");
    }

    private void approveCancellation(
            Connection connection,
            RequestState state,
            int staffID) throws SQLException {

        if (!"Pending".equalsIgnoreCase(state.orderStatus)
                && !"Confirmed".equalsIgnoreCase(state.orderStatus)) {
            throw new SQLException("ORDER_NOT_CANCELLABLE");
        }

        if ("Paid".equalsIgnoreCase(state.paymentStatus)) {
            throw new SQLException("PAID_ORDER_REQUIRES_REFUND");
        }

        restoreOrderStock(connection, state.orderID);
        cancelPendingPayment(connection, state.orderID);
        cancelPendingShipment(connection, state.orderID);
        cancelOrder(connection, state.orderID, staffID);
    }

    private void restoreOrderStock(
            Connection connection,
            int orderID) throws SQLException {

        String sql =
                "UPDATE pv "
                + "SET pv.Quantity = pv.Quantity + details.TotalQuantity "
                + "FROM dbo.ProductVariant pv "
                + "INNER JOIN ( "
                + "    SELECT VariantID, SUM(Quantity) AS TotalQuantity "
                + "    FROM dbo.OrderDetail "
                + "    WHERE OrderID = ? "
                + "    GROUP BY VariantID "
                + ") details ON details.VariantID = pv.VariantID";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.executeUpdate();
        }
    }

    private void cancelPendingPayment(
            Connection connection,
            int orderID) throws SQLException {

        String sql =
                "UPDATE dbo.Payment "
                + "SET PaymentStatus = 'Cancelled' "
                + "WHERE OrderID = ? AND PaymentStatus = 'Pending'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.executeUpdate();
        }
    }

    private void cancelPendingShipment(
            Connection connection,
            int orderID) throws SQLException {

        String sql =
                "UPDATE dbo.Shipment "
                + "SET ShipmentStatus = 'Cancelled' "
                + "WHERE OrderID = ? AND ShipmentStatus = 'Pending'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.executeUpdate();
        }
    }

    private void cancelOrder(
            Connection connection,
            int orderID,
            int staffID) throws SQLException {

        String sql =
                "UPDATE dbo.SalesOrder "
                + "SET OrderStatus = 'Cancelled', StaffID = ?, "
                + "    UpdatedAt = SYSDATETIME() "
                + "WHERE OrderID = ? "
                + "AND OrderStatus IN ('Pending', 'Confirmed')";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, staffID);
            statement.setInt(2, orderID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("ORDER_STATUS_UPDATE_FAILED");
            }
        }
    }

    private void updateRequest(
            Connection connection,
            int requestID,
            int staffID,
            String status,
            String staffResponse) throws SQLException {

        String sql =
                "UPDATE dbo.CustomerOrderRequest "
                + "SET RequestStatus = ?, ProcessedByStaffID = ?, "
                + "    ProcessedAt = SYSDATETIME(), StaffResponse = ? "
                + "WHERE RequestID = ? AND RequestStatus = 'Pending'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, staffID);
            statement.setString(3, staffResponse);
            statement.setInt(4, requestID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("REQUEST_UPDATE_FAILED");
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

    private static class RequestState {
        private int orderID;
        private int customerID;
        private String requestStatus;
        private String requestType;
        private String orderStatus;
        private String paymentStatus;
    }
}
