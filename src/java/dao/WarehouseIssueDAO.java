package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import model.WarehouseIssue;
import util.DBConnection;

public class WarehouseIssueDAO {

    public boolean createIssue(WarehouseIssue issue, int staffID)
            throws SQLException {

        if (issue == null || staffID <= 0
                || issue.getOrderID() <= 0
                || issue.getVariantID() <= 0
                || issue.getQuantity() <= 0) {
            throw new IllegalArgumentException("Invalid warehouse issue.");
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                int orderedQuantity = findOrderedQuantity(
                        connection,
                        issue.getOrderID(),
                        issue.getVariantID()
                );

                if (orderedQuantity <= 0
                        || issue.getQuantity() > orderedQuantity) {
                    connection.rollback();
                    return false;
                }

                String sql =
                        "INSERT INTO dbo.WarehouseIssue "
                        + "(OrderID, VariantID, ReportedByStaffID, Quantity, "
                        + "IssueType, Description) "
                        + "VALUES (?, ?, ?, ?, ?, ?)";

                try (PreparedStatement statement =
                             connection.prepareStatement(sql)) {
                    statement.setInt(1, issue.getOrderID());
                    statement.setInt(2, issue.getVariantID());
                    statement.setInt(3, staffID);
                    statement.setInt(4, issue.getQuantity());
                    statement.setString(5, issue.getIssueType());
                    statement.setString(6, issue.getDescription());

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("WAREHOUSE_ISSUE_CREATE_FAILED");
                    }
                }

                connection.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public List<WarehouseIssue> getOpenIssues() throws SQLException {
        String sql =
                "SELECT wi.IssueID, wi.OrderID, wi.VariantID, wi.Quantity, "
                + "wi.IssueType, wi.Description, wi.Status, wi.CreatedAt, "
                + "wi.ResolutionNote, wi.UpdatedAt, "
                + "p.ProductName, pv.SKU, s.FullName AS ReportedByName, "
                + "resolver.FullName AS ResolvedByName "
                + "FROM dbo.WarehouseIssue wi "
                + "INNER JOIN dbo.ProductVariant pv "
                + "        ON pv.VariantID = wi.VariantID "
                + "INNER JOIN dbo.Product p ON p.ProductID = pv.ProductID "
                + "INNER JOIN dbo.Staff s "
                + "        ON s.StaffID = wi.ReportedByStaffID "
                + "LEFT JOIN dbo.Staff resolver "
                + "       ON resolver.StaffID = wi.ResolvedByStaffID "
                + "WHERE wi.Status = 'Open' "
                + "ORDER BY wi.CreatedAt ASC, wi.IssueID ASC";

        List<WarehouseIssue> issues = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                WarehouseIssue issue = new WarehouseIssue();

                issue.setIssueID(result.getInt("IssueID"));
                issue.setOrderID(result.getInt("OrderID"));
                issue.setVariantID(result.getInt("VariantID"));
                issue.setQuantity(result.getInt("Quantity"));
                issue.setIssueType(result.getString("IssueType"));
                issue.setDescription(result.getString("Description"));
                issue.setStatus(result.getString("Status"));
                issue.setCreatedAt(result.getTimestamp("CreatedAt"));
                issue.setProductName(result.getString("ProductName"));
                issue.setSku(result.getString("SKU"));
                issue.setReportedByName(
                        result.getString("ReportedByName")
                );

                readResolutionDetails(result, issue);
                issues.add(issue);
            }
        }

        return issues;
    }

    public List<WarehouseIssue> getIssuesReportedBy(int staffID)
            throws SQLException {
        if (staffID <= 0) {
            throw new IllegalArgumentException("Invalid staff ID.");
        }

        String sql =
                "SELECT wi.IssueID, wi.OrderID, wi.VariantID, wi.Quantity, "
                + "wi.IssueType, wi.Description, wi.Status, wi.CreatedAt, "
                + "wi.ResolutionNote, wi.UpdatedAt, "
                + "p.ProductName, pv.SKU, reporter.FullName AS ReportedByName, "
                + "resolver.FullName AS ResolvedByName "
                + "FROM dbo.WarehouseIssue wi "
                + "INNER JOIN dbo.ProductVariant pv "
                + "        ON pv.VariantID = wi.VariantID "
                + "INNER JOIN dbo.Product p ON p.ProductID = pv.ProductID "
                + "INNER JOIN dbo.Staff reporter "
                + "        ON reporter.StaffID = wi.ReportedByStaffID "
                + "LEFT JOIN dbo.Staff resolver "
                + "       ON resolver.StaffID = wi.ResolvedByStaffID "
                + "WHERE wi.ReportedByStaffID = ? "
                + "ORDER BY wi.CreatedAt DESC, wi.IssueID DESC";

        List<WarehouseIssue> issues = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, staffID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    WarehouseIssue issue = new WarehouseIssue();
                    issue.setIssueID(result.getInt("IssueID"));
                    issue.setOrderID(result.getInt("OrderID"));
                    issue.setVariantID(result.getInt("VariantID"));
                    issue.setQuantity(result.getInt("Quantity"));
                    issue.setIssueType(result.getString("IssueType"));
                    issue.setDescription(result.getString("Description"));
                    issue.setStatus(result.getString("Status"));
                    issue.setCreatedAt(result.getTimestamp("CreatedAt"));
                    issue.setProductName(result.getString("ProductName"));
                    issue.setSku(result.getString("SKU"));
                    issue.setReportedByName(
                            result.getString("ReportedByName")
                    );
                    readResolutionDetails(result, issue);
                    issues.add(issue);
                }
            }
        }

        return issues;
    }

    private void readResolutionDetails(
            ResultSet result,
            WarehouseIssue issue) throws SQLException {
        issue.setResolutionNote(result.getString("ResolutionNote"));
        issue.setUpdatedAt(result.getTimestamp("UpdatedAt"));
        issue.setResolvedByName(result.getString("ResolvedByName"));
    }

    public boolean resolveIssue(
            int issueID,
            int staffID,
            String status,
            String resolutionNote) throws SQLException {

        if (!"Resolved".equals(status) && !"Rejected".equals(status)) {
            throw new IllegalArgumentException("Invalid issue status.");
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int orderID;
                int customerID;
                String lookup = "SELECT wi.OrderID, so.CustomerID "
                        + "FROM dbo.WarehouseIssue wi WITH (UPDLOCK, ROWLOCK) "
                        + "INNER JOIN dbo.SalesOrder so ON so.OrderID = wi.OrderID "
                        + "WHERE wi.IssueID = ? AND wi.Status = 'Open'";
                try (PreparedStatement statement = connection.prepareStatement(lookup)) {
                    statement.setInt(1, issueID);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }
                        orderID = result.getInt("OrderID");
                        customerID = result.getInt("CustomerID");
                    }
                }

                String sql = "UPDATE dbo.WarehouseIssue "
                        + "SET Status = ?, ResolvedByStaffID = ?, "
                        + "ResolutionNote = ?, UpdatedAt = GETDATE() "
                        + "WHERE IssueID = ? AND Status = 'Open'";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, status);
                    statement.setInt(2, staffID);
                    if (resolutionNote == null || resolutionNote.trim().isEmpty()) {
                        statement.setNull(3, Types.NVARCHAR);
                    } else {
                        statement.setString(3, resolutionNote.trim());
                    }
                    statement.setInt(4, issueID);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                String detail = resolutionNote == null || resolutionNote.trim().isEmpty()
                        ? "" : " Nội dung: " + resolutionNote.trim();
                new CustomerNotificationDAO().create(connection, customerID,
                        orderID, "Cập nhật sự cố đơn hàng #" + orderID,
                        "Cửa hàng đã " + ("Resolved".equals(status)
                                ? "xử lý" : "từ chối")
                                + " sự cố kho liên quan đến đơn hàng của bạn."
                                + detail);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public boolean cancelOrderForIssue(
            int issueID,
            int staffID,
            String customerMessage) throws SQLException {
        String message = customerMessage == null
                ? "" : customerMessage.trim();
        if (issueID <= 0 || staffID <= 0
                || message.isEmpty() || message.length() > 1000) {
            throw new IllegalArgumentException(
                    "Hãy nhập nội dung thông báo cho khách (tối đa 1000 ký tự)."
            );
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                IssueCancellationState state =
                        lockIssueForCancellation(connection, issueID);
                if (state == null
                        || !"Open".equalsIgnoreCase(state.issueStatus)) {
                    connection.rollback();
                    return false;
                }

                if (state.hasPaidPayment) {
                    throw new SQLException("PAID_ORDER_REQUIRES_REFUND");
                }
                if ("Shipped".equalsIgnoreCase(state.orderStatus)
                        || "Delivered".equalsIgnoreCase(state.orderStatus)
                        || "Shipped".equalsIgnoreCase(state.shipmentStatus)
                        || "Delivered".equalsIgnoreCase(state.shipmentStatus)) {
                    throw new SQLException("ORDER_ALREADY_SHIPPED");
                }
                if (!"Pending".equalsIgnoreCase(state.orderStatus)
                        && !"Confirmed".equalsIgnoreCase(state.orderStatus)
                        && !"Processing".equalsIgnoreCase(state.orderStatus)) {
                    throw new SQLException("ORDER_NOT_CANCELLABLE");
                }
                if (state.shipmentStatus != null
                        && !"Pending".equalsIgnoreCase(state.shipmentStatus)
                        && !"Preparing".equalsIgnoreCase(state.shipmentStatus)) {
                    throw new SQLException("ORDER_NOT_CANCELLABLE");
                }

                restoreOrderStock(connection, state.orderID);
                cancelOrder(connection, state.orderID, staffID, message);
                stopPendingPayment(connection, state.orderID);
                cancelUnshippedShipment(connection, state.orderID);
                createCustomerCancellationNotice(
                        connection,
                        state.customerID,
                        state.orderID,
                        staffID,
                        message
                );
                new CustomerNotificationDAO().create(connection,
                        state.customerID, state.orderID,
                        "Đơn hàng #" + state.orderID + " đã bị hủy",
                        "Đơn hàng của bạn đã được hủy do sự cố kho. " + message);
                markIssueResolvedByCancellation(
                        connection,
                        issueID,
                        staffID,
                        message
                );

                connection.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    private IssueCancellationState lockIssueForCancellation(
            Connection connection,
            int issueID) throws SQLException {
        String sql =
                "SELECT wi.OrderID, wi.Status AS IssueStatus, "
                + "so.CustomerID, so.OrderStatus, sh.ShipmentStatus, "
                + "payment.HasPaidPayment "
                + "FROM dbo.WarehouseIssue wi WITH (UPDLOCK, ROWLOCK) "
                + "INNER JOIN dbo.SalesOrder so WITH (UPDLOCK, ROWLOCK) "
                + "    ON so.OrderID = wi.OrderID "
                + "LEFT JOIN dbo.Shipment sh WITH (UPDLOCK, ROWLOCK) "
                + "    ON sh.OrderID = so.OrderID "
                + "OUTER APPLY (SELECT MAX(CASE "
                + "        WHEN p.PaymentStatus = 'Paid' THEN 1 ELSE 0 END) "
                + "        AS HasPaidPayment "
                + "    FROM dbo.Payment p WITH (UPDLOCK, ROWLOCK) "
                + "    WHERE p.OrderID = so.OrderID "
                + ") payment "
                + "WHERE wi.IssueID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, issueID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                IssueCancellationState state = new IssueCancellationState();
                state.orderID = result.getInt("OrderID");
                state.customerID = result.getInt("CustomerID");
                state.issueStatus = result.getString("IssueStatus");
                state.orderStatus = result.getString("OrderStatus");
                state.shipmentStatus = result.getString("ShipmentStatus");
                state.hasPaidPayment =
                        result.getInt("HasPaidPayment") == 1;
                return state;
            }
        }
    }

    private void restoreOrderStock(Connection connection, int orderID)
            throws SQLException {
        String sql =
                "UPDATE pv "
                + "SET pv.Quantity = pv.Quantity + details.TotalQuantity "
                + "FROM dbo.ProductVariant pv "
                + "INNER JOIN (SELECT VariantID, SUM(Quantity) AS TotalQuantity "
                + "    FROM dbo.OrderDetail WHERE OrderID = ? "
                + "    GROUP BY VariantID) details "
                + "ON details.VariantID = pv.VariantID";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.executeUpdate();
        }
    }

    private void cancelOrder(
            Connection connection,
            int orderID,
            int staffID,
            String reason) throws SQLException {
        String sql =
                "UPDATE dbo.SalesOrder "
                + "SET OrderStatus = 'Cancelled', StaffID = ?, "
                + "UpdatedAt = SYSDATETIME(), "
                + "Notes = LEFT(CONVERT(nvarchar(max), "
                + "COALESCE(Notes + NCHAR(13) + NCHAR(10), N'') "
                + "+ N'Hủy do sự cố kho: ' + ?), 2000) "
                + "WHERE OrderID = ? AND OrderStatus IN "
                + "('Pending', 'Confirmed', 'Processing')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, staffID);
            statement.setString(2, reason);
            statement.setInt(3, orderID);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("ORDER_NOT_CANCELLABLE");
            }
        }
    }

    private void stopPendingPayment(Connection connection, int orderID)
            throws SQLException {
        // The current database constraint has no Cancelled payment status.
        // Mark an uncharged pending attempt as Failed so it cannot remain payable.
        String sql =
                "UPDATE dbo.Payment SET PaymentStatus = 'Failed' "
                + "WHERE OrderID = ? AND PaymentStatus = 'Pending'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.executeUpdate();
        }
    }

    private void cancelUnshippedShipment(Connection connection, int orderID)
            throws SQLException {
        String sql =
                "UPDATE dbo.Shipment SET ShipmentStatus = 'Cancelled' "
                + "WHERE OrderID = ? AND ShipmentStatus IN ('Pending', 'Preparing')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.executeUpdate();
        }
    }

    private void createCustomerCancellationNotice(
            Connection connection,
            int customerID,
            int orderID,
            int staffID,
            String message) throws SQLException {
        String subject = "Thông báo hủy đơn hàng #" + orderID;
        String body = "Đơn hàng #" + orderID
                + " đã được cửa hàng hủy do sự cố kho."
                + System.lineSeparator() + System.lineSeparator()
                + "Thông tin từ nhân viên Sales:"
                + System.lineSeparator() + message
                + System.lineSeparator() + System.lineSeparator()
                + "Nếu bạn đã thanh toán trước, vui lòng liên hệ cửa hàng "
                + "để được hỗ trợ hoàn tiền.";
        String insertTicket =
                "INSERT INTO dbo.SupportTicket "
                + "(CustomerID, OrderID, Subject, Status) "
                + "OUTPUT INSERTED.TicketID "
                + "VALUES (?, ?, ?, 'Open')";
        int ticketID;
        try (PreparedStatement statement = connection.prepareStatement(insertTicket)) {
            statement.setInt(1, customerID);
            statement.setInt(2, orderID);
            statement.setString(3, subject);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("CUSTOMER_NOTICE_TICKET_FAILED");
                }
                ticketID = result.getInt("TicketID");
            }
        }

        String insertMessage =
                "INSERT INTO dbo.SupportMessage "
                + "(TicketID, SenderType, SenderStaffID, MessageBody) "
                + "VALUES (?, 'Staff', ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insertMessage)) {
            statement.setInt(1, ticketID);
            statement.setInt(2, staffID);
            statement.setString(3, body);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("CUSTOMER_NOTICE_MESSAGE_FAILED");
            }
        }
    }

    private void markIssueResolvedByCancellation(
            Connection connection,
            int issueID,
            int staffID,
            String note) throws SQLException {
        String sql =
                "UPDATE dbo.WarehouseIssue "
                + "SET Status = 'Resolved', ResolvedByStaffID = ?, "
                + "ResolutionNote = ?, UpdatedAt = SYSDATETIME() "
                + "WHERE IssueID = ? AND Status = 'Open'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, staffID);
            statement.setString(
                    2,
                    "Đã hủy đơn và gửi thông báo cho khách: " + note
            );
            statement.setInt(3, issueID);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("ISSUE_NOT_OPEN");
            }
        }
    }

    private static final class IssueCancellationState {
        private int orderID;
        private int customerID;
        private String issueStatus;
        private String orderStatus;
        private String shipmentStatus;
        private boolean hasPaidPayment;
    }

    private int findOrderedQuantity(
            Connection connection,
            int orderID,
            int variantID) throws SQLException {

        String sql =
                "SELECT od.Quantity "
                + "FROM dbo.SalesOrder o WITH (UPDLOCK, HOLDLOCK) "
                + "INNER JOIN dbo.OrderDetail od "
                + "        ON od.OrderID = o.OrderID "
                + "WHERE o.OrderID = ? "
                + "AND od.VariantID = ? "
                + "AND UPPER(o.OrderStatus) "
                + "    IN ('CONFIRMED', 'PROCESSING', 'PACKING')";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.setInt(2, variantID);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("Quantity") : 0;
            }
        }
    }

    private void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}
