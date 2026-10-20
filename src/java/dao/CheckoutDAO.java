package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import model.Cart;
import model.CartItem;
import model.Promotion;
import util.DBConnection;

public class CheckoutDAO {

    public int createCodOrder(
            int customerID,
            Cart cart,
            String shippingAddress,
            String notes) throws SQLException {
        return createCodOrder(customerID, cart, shippingAddress, notes, "");
    }

    public int createCodOrder(int customerID, Cart cart, String shippingAddress,
                              String notes, String promotionCode) throws SQLException {
        return createCodOrder(customerID, cart, shippingAddress, notes, promotionCode, service.ShippingService.NATIONAL_FEE);
    }

    public int createCodOrder(int customerID, Cart cart, String shippingAddress,
                              String notes, String promotionCode, BigDecimal shippingFee) throws SQLException {
        return createOrder(customerID, cart, shippingAddress, notes,
                promotionCode, shippingFee, "COD");
    }

    public int createOrder(int customerID, Cart cart, String shippingAddress,
                           String notes, String promotionCode,
                           BigDecimal shippingFee, String paymentMethod) throws SQLException {

        if (!"COD".equals(paymentMethod) && !"VNPAY".equals(paymentMethod)) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ.");
        }

        if (!service.ShippingService.isValidEstimatedFee(shippingFee, cart == null ? 1 : cart.getTotalQuantity())) {
            throw new IllegalArgumentException("Phí giao hàng không hợp lệ.");
        }

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

        try (Connection connection = getConnection()) {
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

                BigDecimal discount = BigDecimal.ZERO;
                if (promotionCode != null && !promotionCode.trim().isEmpty()) {
                    PromotionDAO promotions = new PromotionDAO();
                    Promotion promotion = promotions.findAvailableByCode(connection, promotionCode, true);
                    discount = promotion.calculateDiscount(subtotal);
                    promotions.consumeCoupon(connection, promotion.getPromotionID());
                }

                int orderID = insertOrder(
                        connection,
                        customerID,
                        notes,
                        discount,
                        shippingFee
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

                insertPayment(connection, orderID,
                        subtotal.subtract(discount).add(shippingFee), paymentMethod);
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

    public BigDecimal findPendingVnPayAmount(int orderID) throws SQLException {
        String sql = "SELECT Amount FROM dbo.Payment "
                + "WHERE OrderID = ? AND PaymentMethod = 'VNPAY' "
                + "AND PaymentStatus = 'Pending'";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("PENDING_VNPAY_PAYMENT_NOT_FOUND");
                return result.getBigDecimal("Amount");
            }
        }
    }

    /** Applies a verified VNPAY callback and records the transaction idempotently. */
    public boolean recordVnPayResult(int orderID, String transactionCode,
            String responseCode, String transactionStatus, BigDecimal amount,
            String responseMessage) throws SQLException {
        if (orderID <= 0 || amount == null || amount.signum() <= 0
                || transactionCode == null || transactionCode.trim().isEmpty()) {
            return false;
        }
        String paymentMethod;
        String paymentStatus;
        BigDecimal expectedAmount;
        int paymentID;
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                String select = "SELECT TOP (1) PaymentID, PaymentMethod, PaymentStatus, Amount "
                        + "FROM dbo.Payment WITH (UPDLOCK, ROWLOCK) WHERE OrderID = ? ORDER BY PaymentID DESC";
                try (PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setInt(1, orderID);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) { connection.rollback(); return false; }
                        paymentID = result.getInt("PaymentID");
                        paymentMethod = result.getString("PaymentMethod");
                        paymentStatus = result.getString("PaymentStatus");
                        expectedAmount = result.getBigDecimal("Amount");
                    }
                }
                if (!"VNPAY".equalsIgnoreCase(paymentMethod)
                        || expectedAmount == null
                        || expectedAmount.compareTo(amount) != 0) {
                    connection.rollback();
                    return false;
                }

                boolean successful = "00".equals(responseCode)
                        && "00".equals(transactionStatus);
                if ("Pending".equalsIgnoreCase(paymentStatus)) {
                    String update = "UPDATE dbo.Payment SET PaymentStatus = ? WHERE PaymentID = ? AND PaymentStatus = 'Pending'";
                    try (PreparedStatement statement = connection.prepareStatement(update)) {
                        statement.setString(1, successful ? "Paid" : "Failed");
                        statement.setInt(2, paymentID);
                        statement.executeUpdate();
                    }
                } else if (!((successful && "Paid".equalsIgnoreCase(paymentStatus))
                        || (!successful && "Failed".equalsIgnoreCase(paymentStatus)))) {
                    connection.rollback();
                    return false;
                }

                // Keep the transaction status within CK_PaymentTransaction_Status.
                String statusText = successful ? "Success" : "Failed";
                String statusDetails = "VNPAY response=" + responseCode
                        + "; transaction=" + transactionStatus;
                if (responseMessage != null && !responseMessage.trim().isEmpty()) {
                    statusDetails += "; " + responseMessage.trim();
                }
                String updateTransaction = "UPDATE dbo.PaymentTransaction SET "
                        + "TransactionStatus = ?, TransactionAmount = ?, TransactionDate = ?, "
                        + "Gateway = 'VNPAY', ResponseMessage = ? "
                        + "WHERE PaymentID = ? AND TransactionCode = ?";
                int updated;
                try (PreparedStatement statement = connection.prepareStatement(updateTransaction)) {
                    statement.setString(1, statusText);
                    statement.setBigDecimal(2, amount);
                    statement.setTimestamp(3, Timestamp.from(Instant.now()));
                    statement.setString(4, safeMessage(statusDetails));
                    statement.setInt(5, paymentID);
                    statement.setString(6, transactionCode);
                    updated = statement.executeUpdate();
                }
                if (updated == 0) {
                    String insertTransaction = "INSERT INTO dbo.PaymentTransaction "
                            + "(PaymentID, TransactionCode, TransactionStatus, TransactionAmount, TransactionDate, Gateway, ResponseMessage) "
                            + "VALUES (?, ?, ?, ?, ?, 'VNPAY', ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertTransaction)) {
                        statement.setInt(1, paymentID);
                        statement.setString(2, transactionCode);
                        statement.setString(3, statusText);
                        statement.setBigDecimal(4, amount);
                        statement.setTimestamp(5, Timestamp.from(Instant.now()));
                        statement.setString(6, safeMessage(statusDetails));
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
                throw e;
            }
        }
    }

    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
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
            String notes,
            BigDecimal discount, BigDecimal shippingFee) throws SQLException {

        String sql =
                "INSERT INTO dbo.SalesOrder "
                + "(CustomerID, DiscountAmount, ShippingFee, Notes) "
                + "OUTPUT INSERTED.OrderID "
                + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, customerID);
            statement.setBigDecimal(2, discount);
            statement.setBigDecimal(3, shippingFee);

            if (notes == null || notes.trim().isEmpty()) {
                statement.setNull(4, Types.NVARCHAR);
            } else {
                statement.setString(4, notes.trim());
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

    private void insertPayment(
            Connection connection,
            int orderID,
            BigDecimal amount,
            String paymentMethod) throws SQLException {

        String sql =
                "INSERT INTO dbo.Payment "
                + "(OrderID, PaymentMethod, PaymentStatus, Amount) "
                + "VALUES (?, ?, 'Pending', ?)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, orderID);
            statement.setString(2, paymentMethod);
            statement.setBigDecimal(3, amount);
            statement.executeUpdate();
        }
    }

    private String safeMessage(String message) {
        if (message == null) return "";
        String clean = message.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() > 500 ? clean.substring(0, 500) : clean;
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
