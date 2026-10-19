package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import model.CustomerNotification;
import util.DBConnection;

public class CustomerNotificationDAO {

    public void create(Connection connection, int customerID, Integer orderID,
            String title, String message) throws SQLException {
        String sql = "INSERT INTO dbo.CustomerNotification "
                + "(CustomerID, OrderID, Title, Message) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerID);
            if (orderID == null) statement.setNull(2, Types.INTEGER);
            else statement.setInt(2, orderID);
            statement.setString(3, title);
            statement.setString(4, message);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("CUSTOMER_NOTIFICATION_CREATE_FAILED");
            }
        }
    }

    public CustomerNotification getLatestUnread(int customerID) throws SQLException {
        String sql = "SELECT TOP (1) NotificationID, CustomerID, OrderID, "
                + "Title, Message, CreatedAt FROM dbo.CustomerNotification "
                + "WHERE CustomerID = ? AND ReadAt IS NULL "
                + "ORDER BY CreatedAt DESC, NotificationID DESC";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                CustomerNotification notification = new CustomerNotification();
                notification.setNotificationID(result.getInt("NotificationID"));
                notification.setCustomerID(result.getInt("CustomerID"));
                int orderID = result.getInt("OrderID");
                if (!result.wasNull()) notification.setOrderID(orderID);
                notification.setTitle(result.getString("Title"));
                notification.setMessage(result.getString("Message"));
                notification.setCreatedAt(result.getTimestamp("CreatedAt"));
                return notification;
            }
        }
    }

    public boolean markRead(int notificationID, int customerID) throws SQLException {
        String sql = "UPDATE dbo.CustomerNotification SET ReadAt = SYSDATETIME() "
                + "WHERE NotificationID = ? AND CustomerID = ? AND ReadAt IS NULL";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, notificationID);
            statement.setInt(2, customerID);
            return statement.executeUpdate() == 1;
        }
    }
}
