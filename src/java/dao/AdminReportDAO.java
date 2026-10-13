package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import model.AdminReport;
import model.DailySalesReport;
import util.DBConnection;

public class AdminReportDAO {

    private static final String ORDER_AMOUNT_JOINS =
            " LEFT JOIN ("
            + "     SELECT OrderID, "
            + "            SUM(CAST(Quantity AS decimal(19, 2)) * UnitPrice) AS Subtotal "
            + "     FROM dbo.OrderDetail "
            + "     GROUP BY OrderID"
            + " ) detail ON detail.OrderID = orders.OrderID "
            + " OUTER APPLY ("
            + "     SELECT TOP 1 PaymentMethod, PaymentStatus "
            + "     FROM dbo.Payment "
            + "     WHERE OrderID = orders.OrderID "
            + "     ORDER BY PaymentID DESC"
            + " ) payment ";

    private static final String REVENUE_EXPRESSION =
            " CASE "
            + "   WHEN UPPER(ISNULL(payment.PaymentStatus, '')) "
            + "        IN ('PAID', 'SUCCESS', 'COMPLETED') "
            + "     OR (UPPER(ISNULL(payment.PaymentMethod, '')) "
            + "        IN ('COD', 'CASH ON DELIVERY') "
            + "        AND UPPER(ISNULL(orders.OrderStatus, '')) "
            + "        IN ('DELIVERED', 'COMPLETED')) "
            + "   THEN CASE "
            + "          WHEN detail.Subtotal IS NULL THEN CAST(0 AS decimal(19, 2)) "
            + "          ELSE detail.Subtotal "
            + "               - ISNULL(orders.DiscountAmount, 0) "
            + "               + ISNULL(orders.ShippingFee, 0) "
            + "        END "
            + "   ELSE CAST(0 AS decimal(19, 2)) "
            + " END ";

    public AdminReport getSummary(LocalDate from, LocalDate to)
            throws SQLException {

        AdminReport report = new AdminReport();

        String sql =
                "SELECT COUNT_BIG(*) AS TotalOrders, "
                + "       SUM(CASE "
                + "             WHEN UPPER(ISNULL(orders.OrderStatus, '')) "
                + "                  IN ('CANCELLED', 'CANCELED') "
                + "             THEN 1 ELSE 0 "
                + "           END) AS CancelledOrders, "
                + "       SUM(" + REVENUE_EXPRESSION + ") AS Revenue "
                + "FROM dbo.SalesOrder orders "
                + ORDER_AMOUNT_JOINS
                + "WHERE orders.OrderDate >= ? "
                + "  AND orders.OrderDate < DATEADD(day, 1, ?)";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setDate(1, Date.valueOf(from));
            statement.setDate(2, Date.valueOf(to));

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    report.setTotalOrders(result.getLong("TotalOrders"));
                    report.setCancelledOrders(
                            result.getLong("CancelledOrders")
                    );

                    BigDecimal revenue = result.getBigDecimal("Revenue");
                    report.setRevenue(revenue);
                }
            }
        }

        loadInventorySummary(report);
        return report;
    }

    public List<DailySalesReport> getDailySales(LocalDate from, LocalDate to)
            throws SQLException {

        List<DailySalesReport> rows = new ArrayList<>();

        String sql =
                "SELECT CAST(orders.OrderDate AS date) AS ReportDate, "
                + "       COUNT_BIG(*) AS OrderCount, "
                + "       SUM(" + REVENUE_EXPRESSION + ") AS Revenue "
                + "FROM dbo.SalesOrder orders "
                + ORDER_AMOUNT_JOINS
                + "WHERE orders.OrderDate >= ? "
                + "  AND orders.OrderDate < DATEADD(day, 1, ?) "
                + "GROUP BY CAST(orders.OrderDate AS date) "
                + "ORDER BY CAST(orders.OrderDate AS date)";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setDate(1, Date.valueOf(from));
            statement.setDate(2, Date.valueOf(to));

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    DailySalesReport row = new DailySalesReport();

                    Date reportDate = result.getDate("ReportDate");
                    if (reportDate != null) {
                        row.setReportDate(reportDate.toLocalDate());
                    }

                    row.setOrderCount(result.getLong("OrderCount"));
                    row.setRevenue(result.getBigDecimal("Revenue"));
                    rows.add(row);
                }
            }
        }

        return rows;
    }

    private void loadInventorySummary(AdminReport report)
            throws SQLException {

        String sql =
                "SELECT ISNULL(SUM(CAST(variant.Quantity AS bigint)), 0) "
                + "           AS InventoryUnits, "
                + "       COUNT_BIG(*) AS ActiveVariants, "
                + "       ISNULL(SUM(CASE "
                + "           WHEN variant.Quantity <= 5 THEN 1 "
                + "           ELSE 0 "
                + "       END), 0) AS LowStockVariants "
                + "FROM dbo.ProductVariant variant "
                + "INNER JOIN dbo.Product product "
                + "        ON product.ProductID = variant.ProductID "
                + "WHERE variant.Status = 'Active' "
                + "  AND product.Status = 'Active'";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            if (result.next()) {
                report.setInventoryUnits(result.getLong("InventoryUnits"));
                report.setActiveVariants(result.getLong("ActiveVariants"));
                report.setLowStockVariants(
                        result.getLong("LowStockVariants")
                );
            }
        }
    }
}