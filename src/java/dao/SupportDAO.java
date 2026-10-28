package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import model.SupportMessage;
import model.SupportTicket;
import util.DBConnection;

public class SupportDAO {

    private static final String TICKET_SELECT =
            "SELECT t.TicketID, t.CustomerID, t.OrderID, t.Subject, "
            + "t.Status, t.CreatedAt, t.UpdatedAt, c.FullName AS CustomerName, "
            + "(SELECT TOP 1 m.MessageBody "
            + " FROM dbo.SupportMessage m "
            + " WHERE m.TicketID = t.TicketID "
            + " ORDER BY m.CreatedAt DESC, m.MessageID DESC) AS LastMessage "
            + "FROM dbo.SupportTicket t "
            + "INNER JOIN dbo.Customer c ON c.CustomerID = t.CustomerID ";

    public int createTicket(
            int customerID,
            Integer orderID,
            String subject,
            String message) throws SQLException {

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                if (orderID != null
                        && !customerOwnsOrder(connection, customerID, orderID)) {
                    connection.rollback();
                    return -1;
                }

                String insertTicket =
                        "INSERT INTO dbo.SupportTicket "
                        + "(CustomerID, OrderID, Subject, Status) "
                        + "OUTPUT INSERTED.TicketID "
                        + "VALUES (?, ?, ?, 'Open')";

                int ticketID;

                try (PreparedStatement statement =
                             connection.prepareStatement(insertTicket)) {
                    statement.setInt(1, customerID);

                    if (orderID == null) {
                        statement.setNull(2, Types.INTEGER);
                    } else {
                        statement.setInt(2, orderID);
                    }

                    statement.setString(3, subject);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("TICKET_CREATE_FAILED");
                        }
                        ticketID = result.getInt("TicketID");
                    }
                }

                insertMessage(
                        connection,
                        ticketID,
                        "Customer",
                        customerID,
                        null,
                        message
                );

                connection.commit();
                return ticketID;

            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public List<SupportTicket> getCustomerTickets(int customerID)
            throws SQLException {

        String sql = TICKET_SELECT
                + "WHERE t.CustomerID = ? "
                + "ORDER BY t.UpdatedAt DESC, t.CreatedAt DESC";

        List<SupportTicket> tickets = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    tickets.add(mapTicket(result));
                }
            }
        }

        return tickets;
    }

    public List<SupportTicket> getSalesTickets() throws SQLException {
        String sql = TICKET_SELECT
                + "WHERE t.Status <> 'Closed' "
                + "ORDER BY t.UpdatedAt DESC, t.CreatedAt DESC";

        List<SupportTicket> tickets = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                tickets.add(mapTicket(result));
            }
        }

        return tickets;
    }

    public SupportTicket findCustomerTicket(int ticketID, int customerID)
            throws SQLException {

        String sql = TICKET_SELECT
                + "WHERE t.TicketID = ? AND t.CustomerID = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketID);
            statement.setInt(2, customerID);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapTicket(result) : null;
            }
        }
    }

    public SupportTicket findSalesTicket(int ticketID)
            throws SQLException {

        String sql = TICKET_SELECT + "WHERE t.TicketID = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketID);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapTicket(result) : null;
            }
        }
    }

    public List<SupportMessage> getMessages(int ticketID)
            throws SQLException {

        String sql =
                "SELECT m.MessageID, m.TicketID, m.SenderType, "
                + "m.MessageBody, m.CreatedAt, "
                + "COALESCE(c.FullName, s.FullName) AS SenderName "
                + "FROM dbo.SupportMessage m "
                + "LEFT JOIN dbo.Customer c "
                + "  ON c.CustomerID = m.SenderCustomerID "
                + "LEFT JOIN dbo.Staff s "
                + "  ON s.StaffID = m.SenderStaffID "
                + "WHERE m.TicketID = ? "
                + "ORDER BY m.CreatedAt, m.MessageID";

        List<SupportMessage> messages = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    SupportMessage message = new SupportMessage();
                    message.setMessageID(result.getInt("MessageID"));
                    message.setTicketID(result.getInt("TicketID"));
                    message.setSenderType(result.getString("SenderType"));
                    message.setSenderName(result.getString("SenderName"));
                    message.setMessageBody(result.getString("MessageBody"));
                    message.setCreatedAt(result.getTimestamp("CreatedAt"));
                    messages.add(message);
                }
            }
        }

        return messages;
    }

    public boolean customerReply(
            int ticketID,
            int customerID,
            String message) throws SQLException {

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String status = lockTicketForCustomer(
                        connection,
                        ticketID,
                        customerID
                );

                if (status == null || "Closed".equalsIgnoreCase(status)) {
                    connection.rollback();
                    return false;
                }

                insertMessage(
                        connection,
                        ticketID,
                        "Customer",
                        customerID,
                        null,
                        message
                );

                String update =
                        "UPDATE dbo.SupportTicket "
                        + "SET Status = 'Open', UpdatedAt = GETDATE() "
                        + "WHERE TicketID = ? AND CustomerID = ?";

                try (PreparedStatement statement =
                             connection.prepareStatement(update)) {
                    statement.setInt(1, ticketID);
                    statement.setInt(2, customerID);
                    statement.executeUpdate();
                }

                connection.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public boolean salesReply(
            int ticketID,
            int staffID,
            String message,
            String newStatus) throws SQLException {

        if (!"InProgress".equals(newStatus)
                && !"Resolved".equals(newStatus)) {
            throw new IllegalArgumentException("Invalid ticket status.");
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String currentStatus = lockTicket(connection, ticketID);

                if (currentStatus == null
                        || "Closed".equalsIgnoreCase(currentStatus)) {
                    connection.rollback();
                    return false;
                }

                insertMessage(
                        connection,
                        ticketID,
                        "Staff",
                        null,
                        staffID,
                        message
                );

                SupportTicket customerTicket = getTicketCustomer(connection, ticketID);
                if (customerTicket != null) {
                    new CustomerNotificationDAO().create(connection,
                            customerTicket.getCustomerID(),
                            customerTicket.getOrderID(),
                            "Cửa hàng đã phản hồi yêu cầu hỗ trợ",
                            "Bạn có tin nhắn mới từ " + util.AppConfig.require("APP_NAME") + ": " + message);
                }

                String update =
                        "UPDATE dbo.SupportTicket "
                        + "SET Status = ?, UpdatedAt = GETDATE() "
                        + "WHERE TicketID = ?";

                try (PreparedStatement statement =
                             connection.prepareStatement(update)) {
                    statement.setString(1, newStatus);
                    statement.setInt(2, ticketID);
                    statement.executeUpdate();
                }

                connection.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    private boolean customerOwnsOrder(
            Connection connection,
            int customerID,
            int orderID) throws SQLException {

        String sql =
                "SELECT 1 FROM dbo.SalesOrder "
                + "WHERE OrderID = ? AND CustomerID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            statement.setInt(2, customerID);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private String lockTicketForCustomer(
            Connection connection,
            int ticketID,
            int customerID) throws SQLException {

        String sql =
                "SELECT Status FROM dbo.SupportTicket "
                + "WITH (UPDLOCK, ROWLOCK) "
                + "WHERE TicketID = ? AND CustomerID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketID);
            statement.setInt(2, customerID);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("Status") : null;
            }
        }
    }

    private String lockTicket(Connection connection, int ticketID)
            throws SQLException {

        String sql =
                "SELECT Status FROM dbo.SupportTicket "
                + "WITH (UPDLOCK, ROWLOCK) WHERE TicketID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketID);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("Status") : null;
            }
        }
    }

    private SupportTicket getTicketCustomer(Connection connection, int ticketID)
            throws SQLException {
        String sql = "SELECT CustomerID, OrderID FROM dbo.SupportTicket WHERE TicketID = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                SupportTicket ticket = new SupportTicket();
                ticket.setCustomerID(result.getInt("CustomerID"));
                int orderID = result.getInt("OrderID");
                if (!result.wasNull()) ticket.setOrderID(orderID);
                return ticket;
            }
        }
    }

    private void insertMessage(
            Connection connection,
            int ticketID,
            String senderType,
            Integer customerID,
            Integer staffID,
            String message) throws SQLException {

        String sql =
                "INSERT INTO dbo.SupportMessage "
                + "(TicketID, SenderType, SenderCustomerID, "
                + "SenderStaffID, MessageBody) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketID);
            statement.setString(2, senderType);

            if (customerID == null) {
                statement.setNull(3, Types.INTEGER);
            } else {
                statement.setInt(3, customerID);
            }

            if (staffID == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, staffID);
            }

            statement.setString(5, message);
            statement.executeUpdate();
        }
    }

    private SupportTicket mapTicket(ResultSet result)
            throws SQLException {

        SupportTicket ticket = new SupportTicket();

        ticket.setTicketID(result.getInt("TicketID"));
        ticket.setCustomerID(result.getInt("CustomerID"));

        int orderID = result.getInt("OrderID");
        if (!result.wasNull()) {
            ticket.setOrderID(orderID);
        }

        ticket.setSubject(result.getString("Subject"));
        ticket.setStatus(result.getString("Status"));
        ticket.setCustomerName(result.getString("CustomerName"));
        ticket.setLastMessage(result.getString("LastMessage"));
        ticket.setCreatedAt(result.getTimestamp("CreatedAt"));
        ticket.setUpdatedAt(result.getTimestamp("UpdatedAt"));

        return ticket;
    }

    private void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}
