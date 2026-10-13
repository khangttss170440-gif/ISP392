package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import model.Customer;
import util.DBConnection;

public class CustomerDAO {

    public boolean emailExists(String email) throws SQLException {
        String sql =
                "SELECT 1 FROM dbo.Customer WHERE Email = ? "
                + "UNION ALL SELECT 1 FROM dbo.Staff WHERE Email = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, email);
            statement.setString(2, email);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public int register(Customer customer) throws SQLException {
        String sql =
                "INSERT INTO dbo.Customer "
                + "(FullName, Email, Phone, PasswordHash, DateOfBirth, "
                + "Gender) "
                + "OUTPUT INSERTED.CustomerID "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, customer.getFullName());
            statement.setString(2, customer.getEmail());

            if (customer.getPhone() == null) {
                statement.setNull(3, Types.VARCHAR);
            } else {
                statement.setString(3, customer.getPhone());
            }

            statement.setString(4, customer.getPasswordHash());

            if (customer.getDateOfBirth() == null) {
                statement.setNull(5, Types.DATE);
            } else {
                statement.setDate(5, customer.getDateOfBirth());
            }

            if (customer.getGender() == null) {
                statement.setNull(6, Types.VARCHAR);
            } else {
                statement.setString(6, customer.getGender());
            }

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("CustomerID");
                }
            }
        }

        return -1;
    }

    public Customer findActiveByEmail(String email) throws SQLException {
        String sql =
                "SELECT CustomerID, FullName, Email, Phone, PasswordHash, "
                + "DateOfBirth, Gender, Status "
                + "FROM dbo.Customer "
                + "WHERE Email = ? AND Status = 'Active'";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, email);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                Customer customer = new Customer();
                customer.setCustomerID(result.getInt("CustomerID"));
                customer.setFullName(result.getString("FullName"));
                customer.setEmail(result.getString("Email"));
                customer.setPhone(result.getString("Phone"));
                customer.setPasswordHash(result.getString("PasswordHash"));
                customer.setDateOfBirth(result.getDate("DateOfBirth"));
                customer.setGender(result.getString("Gender"));
                customer.setStatus(result.getString("Status"));
                return customer;
            }
        }
    }
}
