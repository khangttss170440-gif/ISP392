package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import model.Cart;
import model.CartItem;
import util.DBConnection;

public class CheckoutDAO {

    public int createCodOrder(
            int customerID,
            Cart cart,
            String shippingAddress,
            String notes) throws SQLException {

        if (customerID <= 0 || cart == null || cart.isEmpty()) {
            throw new IllegalArgumentException(
                    "Customer and non-empty cart are required."
            );
        }

        if (shippingAddress == null
                || shippingAddress.trim().isEmpty()
                || shippingAddress.trim().length() > 500) {
            throw new IllegalArgumentException(
                    "A valid shipping address is required."
            );
        }

        List<CartItem> items = new ArrayList<>(cart.getItems());
        items.sort(Comparator.comparingInt(CartItem::getVariantID));

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                BigDecimal subtotal = BigDecimal.ZERO;
                List<BigDecimal> currentPrices = new ArrayList<>();

                // Lock each variant and read the current database price/stock.
                for (CartItem item : items) {
                    BigDecimal currentPrice = lockAndGetPrice(
                            connection,
                            item.getVariantID(),
                            item.getQuantity()
                    );

                    currentPrices.add(currentPrice);
                    subtotal = subtotal.add(
                            currentPrice.multiply(
                                    BigDecimal.valueOf(item.getQuantity())
                            )
                    );
                }

                int orderID = insertOrder(
                        connection,
                        customerID,
                        notes
                );

                for (int i = 0; i < items.size(); i++) {
                    CartItem item = items.get(i);
                    BigDecimal currentPrice = currentPrices.get(i);

                    insertOrderDetail(
                            connection,
                            orderID,
                            item,
                            currentPrice
                    );

                    subtractStock(
                            connection,
                            item.getVariantID(),
                            item.getQuantity()
                    );
                }

                insertCodPayment(connection, orderID, subtotal);
                insertShipment(
                        connection,
                        orderID,
                        shippingAddress.trim()
                );

                connection.commit();
                return orderID;

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

    private BigDecimal lockAndGetPrice(
            Connection connection,
            int variantID,
            int requestedQuantity) throws SQLException {

        String sql =
                "SELECT pv.Price, pv.Quantity "
                + "FROM dbo.ProductVariant pv WITH (UPDLOCK, ROWLOCK) "
                + "INNER JOIN dbo.Product p "
                + "ON p.ProductID = pv.ProductID "
                + "WHERE pv.VariantID = ? "
                + "AND pv.Status = 'Active' "
                + "AND p.Status = 'Active'";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, variantID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "A product in your cart is no longer available."
                    );
                }

                int availableQuantity = result.getInt("Quantity");

                if (requestedQuantity <= 0
                        || requestedQuantity > availableQuantity) {
                    throw new IllegalStateException(
                            "A product in your cart no longer has enough stock."
                    );
                }

                return result.getBigDecimal("Price");
            }
        }
    }

    private int insertOrder(
            Connection connection,
            int customerID,
            String notes) throws SQLException {

        String sql =
                "INSERT INTO dbo.SalesOrder "
                + "(CustomerID, DiscountAmount, ShippingFee, Notes) "
                + "OUTPUT INSERTED.OrderID "
                + "VALUES (?, 0, 0, ?)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, customerID);

            if (notes == null || notes.trim().isEmpty()) {
                statement.setNull(2, Types.NVARCHAR);
            } else {
                statement.setString(2, notes.trim());
            }

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("OrderID");
                }
            }
        }

        throw new SQLException("Could not create the sales order.");
    }

    private void insertOrderDetail(
            Connection connection,
            int orderID,
            CartItem item,
            BigDecimal currentPrice) throws SQLException {

        String sql =
                "INSERT INTO dbo.OrderDetail "
                + "(OrderID, VariantID, Quantity, UnitPrice) "
                + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, orderID);
            statement.setInt(2, item.getVariantID());
            statement.setInt(3, item.getQuantity());
            statement.setBigDecimal(4, currentPrice);
            statement.executeUpdate();
        }
    }

    private void subtractStock(
            Connection connection,
            int variantID,
            int quantity) throws SQLException {

        String sql =
                "UPDATE dbo.ProductVariant "
                + "SET Quantity = Quantity - ? "
                + "WHERE VariantID = ? "
                + "AND Status = 'Active' "
                + "AND Quantity >= ?";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, quantity);
            statement.setInt(2, variantID);
            statement.setInt(3, quantity);

            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Stock changed during checkout. Please try again."
                );
            }
        }
    }

    private void insertCodPayment(
            Connection connection,
            int orderID,
            BigDecimal amount) throws SQLException {

        String sql =
                "INSERT INTO dbo.Payment "
                + "(OrderID, PaymentMethod, PaymentStatus, Amount) "
                + "VALUES (?, 'COD', 'Pending', ?)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, orderID);
            statement.setBigDecimal(2, amount);
            statement.executeUpdate();
        }
    }

    private void insertShipment(
            Connection connection,
            int orderID,
            String shippingAddress) throws SQLException {

        String sql =
                "INSERT INTO dbo.Shipment (OrderID, ShippingAddress) "
                + "VALUES (?, ?)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, orderID);
            statement.setString(2, shippingAddress);
            statement.executeUpdate();
        }
    }
}