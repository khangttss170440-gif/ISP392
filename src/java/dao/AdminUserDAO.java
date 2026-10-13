package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.UserAccount;
import util.DBConnection;

public class AdminUserDAO {

    public void createStaff(String fullName, String email, String phone,
                            String passwordHash, String role)
            throws SQLException {
        String safeRole = validateNewStaffRole(role);

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try {
                String existsSql =
                        "SELECT 1 FROM dbo.Customer WITH (UPDLOCK, HOLDLOCK) "
                        + "WHERE Email = ? UNION ALL "
                        + "SELECT 1 FROM dbo.Staff WITH (UPDLOCK, HOLDLOCK) "
                        + "WHERE Email = ?";
                try (PreparedStatement exists = connection.prepareStatement(existsSql)) {
                    exists.setString(1, email);
                    exists.setString(2, email);
                    try (ResultSet result = exists.executeQuery()) {
                        if (result.next()) {
                            throw new SQLException("EMAIL_ALREADY_EXISTS");
                        }
                    }
                }

                String insertSql =
                        "INSERT INTO dbo.Staff "
                        + "(FullName, Email, Phone, PasswordHash, Role, Status) "
                        + "VALUES (?, ?, ?, ?, ?, 'Active')";
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.setString(1, fullName);
                    insert.setString(2, email);
                    if (phone == null || phone.isEmpty()) {
                        insert.setNull(3, java.sql.Types.VARCHAR);
                    } else {
                        insert.setString(3, phone);
                    }
                    insert.setString(4, passwordHash);
                    insert.setString(5, safeRole);
                    insert.executeUpdate();
                }
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

    public List<UserAccount> getAccounts(
            String keyword,
            String accountType) throws SQLException {

        String safeKeyword = keyword == null ? "" : keyword.trim();
        String safeType = normalizeAccountType(accountType);
        String pattern = "%" + safeKeyword + "%";

        String sql =
                "SELECT TOP (500) AccountID, AccountType, FullName, "
                + "Email, Phone, Role, Status, CreatedAt "
                + "FROM ( "
                + "    SELECT CustomerID AS AccountID, "
                + "           'Customer' AS AccountType, "
                + "           FullName, Email, Phone, "
                + "           CAST(NULL AS VARCHAR(30)) AS Role, "
                + "           Status, CreatedAt "
                + "    FROM dbo.Customer "
                + "    UNION ALL "
                + "    SELECT StaffID AS AccountID, "
                + "           'Staff' AS AccountType, "
                + "           FullName, Email, Phone, Role, Status, CreatedAt "
                + "    FROM dbo.Staff "
                + ") accounts "
                + "WHERE (? = '' OR FullName LIKE ? "
                + "    OR Email LIKE ? OR Phone LIKE ?) "
                + "AND (? = 'ALL' OR AccountType = ?) "
                + "ORDER BY CreatedAt DESC, AccountType, AccountID DESC";

        List<UserAccount> accounts = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, safeKeyword);
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            statement.setString(4, pattern);
            statement.setString(5, safeType);
            statement.setString(6, safeType);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    UserAccount account = new UserAccount();

                    account.setAccountID(result.getInt("AccountID"));
                    account.setAccountType(
                            result.getString("AccountType")
                    );
                    account.setFullName(result.getString("FullName"));
                    account.setEmail(result.getString("Email"));
                    account.setPhone(result.getString("Phone"));
                    account.setRole(result.getString("Role"));
                    account.setStatus(result.getString("Status"));
                    account.setCreatedAt(
                            result.getTimestamp("CreatedAt")
                    );

                    accounts.add(account);
                }
            }
        }

        return accounts;
    }

    public void updateCustomerStatus(
            int customerID,
            String status) throws SQLException {

        if (customerID <= 0) {
            throw new IllegalArgumentException("Invalid customer ID.");
        }

        String safeStatus = validateStatus(status);
        String sql =
                "UPDATE dbo.Customer SET Status = ?, "
                + "UpdatedAt = SYSDATETIME() "
                + "WHERE CustomerID = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, safeStatus);
            statement.setInt(2, customerID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("ACCOUNT_NOT_FOUND");
            }
        }
    }

    public void updateStaffAccount(
            int staffID,
            int actingAdminID,
            String role,
            String status) throws SQLException {

        if (staffID <= 0 || actingAdminID <= 0) {
            throw new IllegalArgumentException("Invalid staff ID.");
        }

        String safeRole = validateRole(role);
        String safeStatus = validateStatus(status);

        if (staffID == actingAdminID) {
            throw new SQLException("CANNOT_EDIT_OWN_ADMIN_ACCOUNT");
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String[] currentAccount = lockStaff(
                        connection,
                        staffID
                );

                boolean currentlyActiveAdmin =
                        "Admin".equalsIgnoreCase(currentAccount[0])
                        && "Active".equalsIgnoreCase(currentAccount[1]);

                boolean willRemainActiveAdmin =
                        "Admin".equalsIgnoreCase(safeRole)
                        && "Active".equalsIgnoreCase(safeStatus);

                if (currentlyActiveAdmin && !willRemainActiveAdmin
                        && countActiveAdmins(connection) <= 1) {
                    throw new SQLException("LAST_ACTIVE_ADMIN");
                }

                String sql =
                        "UPDATE dbo.Staff "
                        + "SET Role = ?, Status = ?, "
                        + "    UpdatedAt = SYSDATETIME() "
                        + "WHERE StaffID = ?";

                try (PreparedStatement statement =
                             connection.prepareStatement(sql)) {

                    statement.setString(1, safeRole);
                    statement.setString(2, safeStatus);
                    statement.setInt(3, staffID);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("ACCOUNT_NOT_FOUND");
                    }
                }

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

    private String[] lockStaff(
            Connection connection,
            int staffID) throws SQLException {

        String sql =
                "SELECT Role, Status "
                + "FROM dbo.Staff WITH (UPDLOCK, ROWLOCK) "
                + "WHERE StaffID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, staffID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("ACCOUNT_NOT_FOUND");
                }

                return new String[] {
                    result.getString("Role"),
                    result.getString("Status")
                };
            }
        }
    }

    private int countActiveAdmins(Connection connection)
            throws SQLException {

        String sql =
                "SELECT COUNT(*) AS AdminCount "
                + "FROM dbo.Staff WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE Role = 'Admin' AND Status = 'Active'";

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            result.next();
            return result.getInt("AdminCount");
        }
    }

    private String normalizeAccountType(String accountType) {
        if (accountType == null || accountType.trim().isEmpty()
                || "ALL".equalsIgnoreCase(accountType)) {
            return "ALL";
        }

        if ("Customer".equalsIgnoreCase(accountType)) {
            return "Customer";
        }

        if ("Staff".equalsIgnoreCase(accountType)) {
            return "Staff";
        }

        throw new IllegalArgumentException("Invalid account type.");
    }

    private String validateStatus(String status) {
        if ("Active".equalsIgnoreCase(status)) {
            return "Active";
        }

        if ("Inactive".equalsIgnoreCase(status)) {
            return "Inactive";
        }

        throw new IllegalArgumentException("Trạng thái không hợp lệ.");
    }

    private String validateRole(String role) {
        if ("Sales".equalsIgnoreCase(role)) {
            return "Sales";
        }

        if ("Warehouse".equalsIgnoreCase(role)) {
            return "Warehouse";
        }

        if ("Admin".equalsIgnoreCase(role)) {
            return "Admin";
        }

        throw new IllegalArgumentException("Quyền Staff không hợp lệ.");
    }

    private String validateNewStaffRole(String role) {
        if ("Sales".equalsIgnoreCase(role)) return "Sales";
        if ("Warehouse".equalsIgnoreCase(role)) return "Warehouse";
        throw new IllegalArgumentException("Chỉ được tạo tài khoản Sales hoặc Warehouse.");
    }
}
