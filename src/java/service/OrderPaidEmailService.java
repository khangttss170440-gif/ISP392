package service;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import util.DBConnection;
import util.GmailSmtpService;

/** Sends the customer receipt and store notification after a payment transition. */
public final class OrderPaidEmailService {
    private static final Logger LOGGER = Logger.getLogger(OrderPaidEmailService.class.getName());

    private OrderPaidEmailService() { }

    public static void notifyPaidOrder(int orderID) {
        Thread mailTask = new Thread(() -> dispatch(orderID), "order-paid-email-" + orderID);
        mailTask.setDaemon(true);
        mailTask.start();
    }

    private static void dispatch(int orderID) {
        try {
            OrderReceipt receipt = loadReceipt(orderID);
            if (receipt == null) return;

            String customerSubject = "Xác nhận thanh toán đơn hàng #" + orderID;
            String customerBody = receiptBody(receipt, false);
            send(receipt.customerEmail, customerSubject, customerBody, orderID);

            String storeEmail = util.AppConfig.get("STORE_ORDER_NOTIFICATION_EMAIL");
            String storeSubject = "Đơn hàng #" + orderID + " đã thanh toán trên website";
            send(storeEmail, storeSubject, receiptBody(receipt, true), orderID);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load paid order email details for order " + orderID, e);
        }
    }

    private static OrderReceipt loadReceipt(int orderID) throws SQLException {
        String orderSql = "SELECT c.FullName, c.Email, p.PaymentMethod, p.Amount "
                + "FROM dbo.SalesOrder so "
                + "JOIN dbo.Customer c ON c.CustomerID=so.CustomerID "
                + "JOIN dbo.Payment p ON p.OrderID=so.OrderID "
                + "WHERE so.OrderID=? AND p.PaymentStatus='Paid'";
        OrderReceipt receipt = null;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(orderSql)) {
            statement.setInt(1, orderID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                receipt = new OrderReceipt(orderID, result.getString("FullName"),
                        result.getString("Email"), result.getString("PaymentMethod"),
                        result.getBigDecimal("Amount"));
            }
            String itemsSql = "SELECT pr.ProductName, pv.Color, pv.Size, od.Quantity, od.UnitPrice "
                    + "FROM dbo.OrderDetail od "
                    + "JOIN dbo.ProductVariant pv ON pv.VariantID=od.VariantID "
                    + "JOIN dbo.Product pr ON pr.ProductID=pv.ProductID "
                    + "WHERE od.OrderID=? ORDER BY pr.ProductName, pv.Color, pv.Size";
            try (PreparedStatement items = connection.prepareStatement(itemsSql)) {
                items.setInt(1, orderID);
                try (ResultSet result = items.executeQuery()) {
                    while (result.next()) {
                        receipt.items.add(new ReceiptLine(result.getString("ProductName"),
                                result.getString("Color"), result.getString("Size"),
                                result.getInt("Quantity"), result.getBigDecimal("UnitPrice")));
                    }
                }
            }
        }
        return receipt;
    }

    private static String receiptBody(OrderReceipt receipt, boolean forStore) {
        StringBuilder body = new StringBuilder();
        if (forStore) {
            body.append("Website ghi nhận một đơn hàng đã thanh toán thành công.\n\n")
                    .append("Khách hàng: ").append(value(receipt.customerName)).append('\n')
                    .append("Email khách: ").append(value(receipt.customerEmail)).append('\n');
        } else {
            body.append("Xin chào ").append(value(receipt.customerName)).append(",\n\n")
                    .append("Chúng tôi đã nhận thanh toán thành công cho đơn hàng của bạn.\n");
        }
        body.append("Mã đơn hàng: #").append(receipt.orderID).append('\n')
                .append("Phương thức thanh toán: ").append(value(receipt.paymentMethod)).append('\n')
                .append("Sản phẩm trong đơn:\n");
        for (ReceiptLine item : receipt.items) {
            body.append("- ").append(value(item.name));
            if (!empty(item.color) || !empty(item.size)) {
                body.append(" (").append(value(item.color));
                if (!empty(item.color) && !empty(item.size)) body.append(" / ");
                body.append(value(item.size)).append(')');
            }
            body.append(" x ").append(item.quantity)
                    .append(" — ").append(money(item.unitPrice))
                    .append("/sản phẩm\n");
        }
        body.append("Tổng thanh toán: ").append(money(receipt.amount)).append("\n\n");
        if (forStore) body.append("Đây là email thông báo tự động từ website.");
        else body.append("Cảm ơn bạn đã mua sắm.");
        return body.toString();
    }

    private static void send(String recipient, String subject, String body, int orderID) {
        if (empty(recipient)) {
            LOGGER.warning("Order email recipient is not configured for order " + orderID);
            return;
        }
        try {
            GmailSmtpService.sendOrderPaidEmail(recipient.trim(), subject, body);
        } catch (IOException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Could not send paid order email for order " + orderID, e);
        }
    }

    private static String money(BigDecimal amount) {
        return util.AppConfig.money(amount == null ? BigDecimal.ZERO : amount);
    }

    private static String value(String text) { return text == null ? "" : text; }
    private static boolean empty(String text) { return text == null || text.trim().isEmpty(); }

    private static final class OrderReceipt {
        private final int orderID;
        private final String customerName;
        private final String customerEmail;
        private final String paymentMethod;
        private final BigDecimal amount;
        private final List<ReceiptLine> items = new ArrayList<>();

        private OrderReceipt(int orderID, String customerName, String customerEmail,
                String paymentMethod, BigDecimal amount) {
            this.orderID = orderID;
            this.customerName = customerName;
            this.customerEmail = customerEmail;
            this.paymentMethod = paymentMethod;
            this.amount = amount;
        }
    }

    private static final class ReceiptLine {
        private final String name;
        private final String color;
        private final String size;
        private final int quantity;
        private final BigDecimal unitPrice;

        private ReceiptLine(String name, String color, String size, int quantity, BigDecimal unitPrice) {
            this.name = name;
            this.color = color;
            this.size = size;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }
}
