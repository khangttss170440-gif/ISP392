package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import model.CustomerAddress;
import util.DBConnection;

public class CustomerAddressDAO {

    public List<CustomerAddress> findByCustomer(int customerID)
            throws SQLException {

        String sql =
                "SELECT AddressID, CustomerID, RecipientName, Phone, "
                + "AddressLine, Ward, District, Province, IsDefault "
                + "FROM dbo.CustomerAddress "
                + "WHERE CustomerID = ? "
                + "ORDER BY IsDefault DESC, AddressID DESC";

        List<CustomerAddress> addresses = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, customerID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    addresses.add(mapAddress(result));
                }
            }
        }

        return addresses;
    }

    public CustomerAddress findById(int addressID, int customerID)
            throws SQLException {

        String sql =
                "SELECT AddressID, CustomerID, RecipientName, Phone, "
                + "AddressLine, Ward, District, Province, IsDefault "
                + "FROM dbo.CustomerAddress "
                + "WHERE AddressID = ? AND CustomerID = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, addressID);
            statement.setInt(2, customerID);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapAddress(result) : null;
            }
        }
    }

    public int save(CustomerAddress address, int customerID)
            throws SQLException {

        if (address == null || customerID <= 0) {
            throw new IllegalArgumentException("Invalid address or customer.");
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                lockCustomer(connection, customerID);

                if (address.isDefaultAddress()) {
                    clearDefaultAddress(connection, customerID);
                }

                int addressID;

                if (address.getAddressID() <= 0) {
                    addressID = insert(connection, address, customerID);
                } else {
                    update(connection, address, customerID);
                    addressID = address.getAddressID();
                }

                connection.commit();
                return addressID;

            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public boolean setDefault(int addressID, int customerID)
            throws SQLException {

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                lockCustomer(connection, customerID);
                clearDefaultAddress(connection, customerID);

                String sql =
                        "UPDATE dbo.CustomerAddress "
                        + "SET IsDefault = 1, UpdatedAt = GETDATE() "
                        + "WHERE AddressID = ? AND CustomerID = ?";

                int updated;

                try (PreparedStatement statement =
                             connection.prepareStatement(sql)) {
                    statement.setInt(1, addressID);
                    statement.setInt(2, customerID);
                    updated = statement.executeUpdate();
                }

                if (updated != 1) {
                    connection.rollback();
                    return false;
                }

                connection.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    public boolean delete(int addressID, int customerID)
            throws SQLException {

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                lockCustomer(connection, customerID);

                boolean wasDefault = false;
                String findSql =
                        "SELECT IsDefault "
                        + "FROM dbo.CustomerAddress WITH (UPDLOCK, ROWLOCK) "
                        + "WHERE AddressID = ? AND CustomerID = ?";

                try (PreparedStatement statement =
                             connection.prepareStatement(findSql)) {
                    statement.setInt(1, addressID);
                    statement.setInt(2, customerID);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }

                        wasDefault = result.getBoolean("IsDefault");
                    }
                }

                String deleteSql =
                        "DELETE FROM dbo.CustomerAddress "
                        + "WHERE AddressID = ? AND CustomerID = ?";

                try (PreparedStatement statement =
                             connection.prepareStatement(deleteSql)) {
                    statement.setInt(1, addressID);
                    statement.setInt(2, customerID);

                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }

                if (wasDefault) {
                    promoteLatestAddress(connection, customerID);
                }

                connection.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        }
    }

    private int insert(Connection connection,
                       CustomerAddress address,
                       int customerID)
            throws SQLException {

        String sql =
                "INSERT INTO dbo.CustomerAddress "
                + "(CustomerID, RecipientName, Phone, AddressLine, "
                + "Ward, District, Province, IsDefault) "
                + "OUTPUT INSERTED.AddressID "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerID);
            statement.setString(2, address.getRecipientName());
            statement.setString(3, address.getPhone());
            statement.setString(4, address.getAddressLine());
            setNullableString(statement, 5, address.getWard());
            setNullableString(statement, 6, address.getDistrict());
            statement.setString(7, address.getProvince());
            statement.setBoolean(8, address.isDefaultAddress());

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("AddressID");
                }
            }
        }

        throw new SQLException("ADDRESS_CREATE_FAILED");
    }

    private void update(Connection connection,
                        CustomerAddress address,
                        int customerID)
            throws SQLException {

        String sql =
                "UPDATE dbo.CustomerAddress "
                + "SET RecipientName = ?, Phone = ?, AddressLine = ?, "
                + "    Ward = ?, District = ?, Province = ?, IsDefault = ?, "
                + "    UpdatedAt = GETDATE() "
                + "WHERE AddressID = ? AND CustomerID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, address.getRecipientName());
            statement.setString(2, address.getPhone());
            statement.setString(3, address.getAddressLine());
            setNullableString(statement, 4, address.getWard());
            setNullableString(statement, 5, address.getDistrict());
            statement.setString(6, address.getProvince());
            statement.setBoolean(7, address.isDefaultAddress());
            statement.setInt(8, address.getAddressID());
            statement.setInt(9, customerID);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("ADDRESS_NOT_FOUND");
            }
        }
    }

    private void clearDefaultAddress(Connection connection, int customerID)
            throws SQLException {

        String sql =
                "UPDATE dbo.CustomerAddress "
                + "SET IsDefault = 0 "
                + "WHERE CustomerID = ? AND IsDefault = 1";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerID);
            statement.executeUpdate();
        }
    }

    private void promoteLatestAddress(Connection connection, int customerID)
            throws SQLException {

        String selectSql =
                "SELECT TOP 1 AddressID "
                + "FROM dbo.CustomerAddress "
                + "WHERE CustomerID = ? "
                + "ORDER BY AddressID DESC";

        Integer nextAddressID = null;

        try (PreparedStatement statement =
                     connection.prepareStatement(selectSql)) {
            statement.setInt(1, customerID);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    nextAddressID = result.getInt("AddressID");
                }
            }
        }

        if (nextAddressID != null) {
            String updateSql =
                    "UPDATE dbo.CustomerAddress "
                    + "SET IsDefault = 1, UpdatedAt = GETDATE() "
                    + "WHERE AddressID = ? AND CustomerID = ?";

            try (PreparedStatement statement =
                         connection.prepareStatement(updateSql)) {
                statement.setInt(1, nextAddressID);
                statement.setInt(2, customerID);
                statement.executeUpdate();
            }
        }
    }

    private void lockCustomer(Connection connection, int customerID)
            throws SQLException {

        String sql =
                "SELECT CustomerID "
                + "FROM dbo.Customer WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE CustomerID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("CUSTOMER_NOT_FOUND");
                }
            }
        }
    }

    private CustomerAddress mapAddress(ResultSet result)
            throws SQLException {

        CustomerAddress address = new CustomerAddress();

        address.setAddressID(result.getInt("AddressID"));
        address.setCustomerID(result.getInt("CustomerID"));
        address.setRecipientName(result.getString("RecipientName"));
        address.setPhone(result.getString("Phone"));
        address.setAddressLine(result.getString("AddressLine"));
        address.setWard(result.getString("Ward"));
        address.setDistrict(result.getString("District"));
        address.setProvince(result.getString("Province"));
        address.setDefaultAddress(result.getBoolean("IsDefault"));

        return address;
    }

    private void setNullableString(PreparedStatement statement,
                                   int index,
                                   String value)
            throws SQLException {

        if (value == null || value.trim().isEmpty()) {
            statement.setNull(index, Types.NVARCHAR);
        } else {
            statement.setString(index, value.trim());
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