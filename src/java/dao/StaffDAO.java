package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import model.Staff;
import util.DBConnection;

public class StaffDAO {

    public Staff findActiveByEmail(String email) throws SQLException {
        String sql =
                "SELECT StaffID, FullName, Email, Phone, PasswordHash, "
                + "Role, Status "
                + "FROM dbo.Staff "
                + "WHERE Email = ? AND Status = 'Active'";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, email);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                Staff staff = new Staff();
                staff.setStaffID(result.getInt("StaffID"));
                staff.setFullName(result.getString("FullName"));
                staff.setEmail(result.getString("Email"));
                staff.setPhone(result.getString("Phone"));
                staff.setPasswordHash(result.getString("PasswordHash"));
                staff.setRole(result.getString("Role"));
                staff.setStatus(result.getString("Status"));
                return staff;
            }
        }
    }
}
