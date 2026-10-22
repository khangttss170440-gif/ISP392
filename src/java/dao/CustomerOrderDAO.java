package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.CustomerOrder;
import model.OrderLine;
import util.DBConnection;

public class CustomerOrderDAO {

    public List<CustomerOrder> getOrdersByCustomer(int customerID)
            throws SQLException {

        if (customerID <= 0) {
            throw new IllegalArgumentException("Customer ID must be positive.");
        }

        String sql = orderSelect()
                + " WHERE so.CustomerID = ? "
                + "ORDER BY so.OrderDate DESC, so.OrderID DESC";

        List<CustomerOrder> orders = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, customerID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    orders.add(mapOrder(result));
                }
            }
        }

        return orders;
    }

    public CustomerOrder getOrderByCustomer(
            int customerID,
            int orderID) throws SQLException {

        if (customerID <= 0 || orderID <= 0) {
            throw new IllegalArgumentException("Invalid customer or order ID.");
        }

        String sql = orderSelect()
                + " WHERE so.CustomerID = ? AND so.OrderID = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, customerID);
            statement.setInt(2, orderID);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return mapOrder(result);
                }
            }
        }

        return null;
    }

    public List<OrderLine> getOrderLinesByCustomer(
            int customerID,
            int orderID) throws SQLException {

        String sql =
                "SELECT od.VariantID, p.ProductID, p.ProductName, pv.SKU, "
                + "pv.Color, pv.Size, od.Quantity, od.UnitPrice, "
                + "(od.Quantity * od.UnitPrice) AS LineTotal "
                + "FROM dbo.OrderDetail od "
                + "INNER JOIN dbo.ProductVariant pv "
                + "    ON pv.VariantID = od.VariantID "
                + "INNER JOIN dbo.Product p "
                + "    ON p.ProductID = pv.ProductID "
                + "INNER JOIN dbo.SalesOrder so "
                + "    ON so.OrderID = od.OrderID "
                + "WHERE so.CustomerID = ? AND so.OrderID = ? "
                + "ORDER BY p.ProductName, pv.Color, pv.Size";

        List<OrderLine> lines = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, customerID);
            statement.setInt(2, orderID);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    OrderLine line = new OrderLine();

                    line.setVariantID(result.getInt("VariantID"));
                    line.setProductID(result.getInt("ProductID"));
                    line.setProductName(result.getString("ProductName"));
                    line.setSku(result.getString("SKU"));
                    line.setColor(result.getString("Color"));
                    line.setSize(result.getString("Size"));
                    line.setQuantity(result.getInt("Quantity"));
                    line.setUnitPrice(result.getBigDecimal("UnitPrice"));
                    line.setLineTotal(result.getBigDecimal("LineTotal"));

                    lines.add(line);
                }
            }
        }

        return lines;
    }

    private String orderSelect() {
        return "SELECT so.OrderID, so.OrderDate, so.OrderStatus, "
                + "so.DiscountAmount, so.ShippingFee, "
                + "COALESCE(details.Subtotal, 0) AS Subtotal, "
                + "(COALESCE(details.Subtotal, 0) "
                + " - so.DiscountAmount + so.ShippingFee) AS TotalAmount, "
                + "payment.PaymentMethod, payment.PaymentStatus, "
                + "shipment.ShipmentStatus, shipment.TrackingNumber, "
                + "shipment.Carrier, shipment.ShippingAddress, ghn.CarrierStatus, ghn.LastSyncedAt "
                + "FROM dbo.SalesOrder so "
                + "LEFT JOIN dbo.GhnDelivery ghn ON ghn.OrderID=so.OrderID "
                + "OUTER APPLY ( "
                + "    SELECT SUM(od.Quantity * od.UnitPrice) AS Subtotal "
                + "    FROM dbo.OrderDetail od "
                + "    WHERE od.OrderID = so.OrderID "
                + ") details "
                + "OUTER APPLY ( "
                + "    SELECT TOP (1) p.PaymentMethod, p.PaymentStatus "
                + "    FROM dbo.Payment p "
                + "    WHERE p.OrderID = so.OrderID "
                + "    ORDER BY p.PaymentID DESC "
                + ") payment "
                + "OUTER APPLY ( "
                + "    SELECT TOP (1) sh.ShipmentStatus, "
                + "           sh.TrackingNumber, sh.Carrier, "
                + "           sh.ShippingAddress "
                + "    FROM dbo.Shipment sh "
                + "    WHERE sh.OrderID = so.OrderID "
                + "    ORDER BY sh.ShipmentID DESC "
                + ") shipment";
    }

    private CustomerOrder mapOrder(ResultSet result) throws SQLException {
        CustomerOrder order = new CustomerOrder();

        order.setOrderID(result.getInt("OrderID"));
        order.setOrderDate(result.getTimestamp("OrderDate"));
        order.setOrderStatus(result.getString("OrderStatus"));
        order.setSubtotal(result.getBigDecimal("Subtotal"));
        order.setDiscountAmount(result.getBigDecimal("DiscountAmount"));
        order.setShippingFee(result.getBigDecimal("ShippingFee"));
        order.setTotalAmount(result.getBigDecimal("TotalAmount"));
        order.setPaymentMethod(result.getString("PaymentMethod"));
        order.setPaymentStatus(result.getString("PaymentStatus"));
        order.setShipmentStatus(result.getString("ShipmentStatus"));
        order.setTrackingNumber(result.getString("TrackingNumber"));
        order.setCarrier(result.getString("Carrier"));
        order.setShippingAddress(result.getString("ShippingAddress"));
        order.setCarrierStatus(result.getString("CarrierStatus"));
        order.setLastCarrierSync(result.getTimestamp("LastSyncedAt"));

        return order;
    }

    public List<CustomerOrder> getTrackingOrders(int customerID,List<Integer> orderIDs) throws SQLException {
        if(customerID<=0 || orderIDs==null || orderIDs.isEmpty() || orderIDs.size()>100)
            throw new IllegalArgumentException("Invalid tracking selection.");
        String placeholders=String.join(",",java.util.Collections.nCopies(orderIDs.size(),"?"));
        String sql=orderSelect()+" WHERE so.CustomerID=? AND so.OrderID IN ("+placeholders+")";
        List<CustomerOrder> orders=new ArrayList<>();
        try(Connection connection=DBConnection.getConnection();PreparedStatement s=connection.prepareStatement(sql)) {
            s.setQueryTimeout(5);s.setInt(1,customerID);
            for(int i=0;i<orderIDs.size();i++) s.setInt(i+2,orderIDs.get(i));
            try(ResultSet r=s.executeQuery()){while(r.next()) orders.add(mapOrder(r));}
        }
        return orders;
    }
}
