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
                + "p.ProductName, pv.SKU, s.FullName AS ReportedByName "
                + "FROM dbo.WarehouseIssue wi "
                + "INNER JOIN dbo.ProductVariant pv "
                + "        ON pv.VariantID = wi.VariantID "
                + "INNER JOIN dbo.Product p ON p.ProductID = pv.ProductID "
                + "INNER JOIN dbo.Staff s "
                + "        ON s.StaffID = wi.ReportedByStaffID "
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

                issues.add(issue);
            }
        }

        return issues;
    }

    public boolean resolveIssue(
            int issueID,
            int staffID,
            String status,
            String resolutionNote) throws SQLException {

        if (!"Resolved".equals(status) && !"Rejected".equals(status)) {
            throw new IllegalArgumentException("Invalid issue status.");
        }

        String sql =
                "UPDATE dbo.WarehouseIssue "
                + "SET Status = ?, ResolvedByStaffID = ?, "
                + "    ResolutionNote = ?, UpdatedAt = GETDATE() "
                + "WHERE IssueID = ? AND Status = 'Open'";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, status);
            statement.setInt(2, staffID);

            if (resolutionNote == null || resolutionNote.trim().isEmpty()) {
                statement.setNull(3, Types.NVARCHAR);
            } else {
                statement.setString(3, resolutionNote.trim());
            }

            statement.setInt(4, issueID);
            return statement.executeUpdate() == 1;
        }
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