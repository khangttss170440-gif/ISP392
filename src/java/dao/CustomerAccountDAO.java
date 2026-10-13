package dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import util.DBConnection;

public class CustomerAccountDAO {

    public boolean updateProfile(
            int customerID,
            String fullName,
            String phone,
            Date dateOfBirth,
            String gender) throws SQLException {

        String sql =
                "UPDATE dbo.Customer "
                + "SET FullName = ?, Phone = ?, DateOfBirth = ?, "
                + "    Gender = ?, UpdatedAt = GETDATE() "
                + "WHERE CustomerID = ? AND Status = 'Active'";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, fullName);

            if (phone == null || phone.trim().isEmpty()) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, phone);
            }

            if (dateOfBirth == null) {
                statement.setNull(3, Types.DATE);
            } else {
                statement.setDate(3, dateOfBirth);
            }

            if (gender == null || gender.trim().isEmpty()) {
                statement.setNull(4, Types.VARCHAR);
            } else {
                statement.setString(4, gender);
            }

            statement.setInt(5, customerID);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean changePassword(
            int customerID,
            String currentPasswordHash,
            String newPasswordHash) throws SQLException {

        String sql =
                "UPDATE dbo.Customer "
                + "SET PasswordHash = ?, UpdatedAt = GETDATE() "
                + "WHERE CustomerID = ? "
                + "  AND PasswordHash = ? "
                + "  AND Status = 'Active'";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, newPasswordHash);
            statement.setInt(2, customerID);
            statement.setString(3, currentPasswordHash);

            return statement.executeUpdate() == 1;
        }
    }
}