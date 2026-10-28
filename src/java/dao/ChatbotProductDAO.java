package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.ChatbotProduct;
import util.AppConfig;
import util.DBConnection;

/** Reads current public catalog data; natural-language processing never produces SQL. */
public class ChatbotProductDAO {
    public List<ChatbotProduct> getSearchableCatalog() throws SQLException {
        int limit = AppConfig.integer("CHATBOT_CATALOG_VARIANT_LIMIT", 1, 100000);
        String sql = "SELECT TOP (?) p.ProductID, p.ProductName, c.CategoryName, p.Brand, "
                + "p.Description, p.Material, pv.SKU, pv.Color, pv.Size, pv.Price, pv.Quantity "
                + "FROM dbo.Product p JOIN dbo.ProductCategory c ON c.CategoryID=p.CategoryID "
                + "JOIN dbo.ProductVariant pv ON pv.ProductID=p.ProductID "
                + "WHERE p.Status='Active' AND c.Status='Active' AND pv.Status='Active' "
                + "ORDER BY p.ProductID DESC, pv.VariantID";
        List<ChatbotProduct> catalog = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit + 1);
            statement.setQueryTimeout(AppConfig.integer("CHATBOT_CATALOG_QUERY_TIMEOUT_SECONDS", 1, 60));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (catalog.size() == limit) throw new SQLException("CHATBOT_CATALOG_LIMIT_EXCEEDED");
                    ChatbotProduct product = new ChatbotProduct();
                    product.setProductID(rows.getInt("ProductID"));
                    product.setProductName(rows.getString("ProductName"));
                    product.setCategoryName(rows.getString("CategoryName"));
                    product.setBrand(rows.getString("Brand"));
                    product.setDescription(rows.getString("Description"));
                    product.setMaterial(rows.getString("Material"));
                    product.setSku(rows.getString("SKU"));
                    product.setColor(rows.getString("Color"));
                    product.setSize(rows.getString("Size"));
                    product.setPrice(rows.getBigDecimal("Price"));
                    product.setQuantity(rows.getInt("Quantity"));
                    catalog.add(product);
                }
            }
        }
        return catalog;
    }
}
