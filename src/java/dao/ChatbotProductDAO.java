package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.ChatbotProduct;
import util.DBConnection;

/** Searches the active catalog with user-provided criteria, never model-generated SQL. */
public class ChatbotProductDAO {
    private static final Pattern SIZE = Pattern.compile("(?i)(?<![\\p{L}\\d])(XXXL|XXL|XXS|XL|XS|[SML]|(?:2[8-9]|3[0-8]))(?![\\p{L}\\d])");
    private static final Pattern MONEY = Pattern.compile("(?i)(dưới|duoi|trên|tren|tầm|tam|khoảng|khoang|\u0111ến|den)?\\s*(\\d+(?:[.,]\\d+)?)\\s*(triệu|trieu|tr|nghìn|nghin|ngàn|ngan|k|đ|đồng|dong|vnd)?");
    private static final String[][] COLORS = {
        {"đỏ", "do", "red"}, {"xanh dương", "xanh navy", "navy", "blue", "xanh biển"},
        {"xanh lá", "xanh la", "green"}, {"đen", "den", "black"},
        {"trắng", "trang", "white"}, {"xám", "xam", "ghi", "gray", "grey"},
        {"be", "kem", "cream", "beige"}, {"nâu", "nau", "brown"},
        {"vàng", "vang", "yellow"}, {"tím", "tim", "purple"}, {"hồng", "hong", "pink"}
    };

    public boolean isProductSearch(String message, boolean continuingSearch) {
        String q = message.toLowerCase();
        if (continuingSearch && (hasColor(q) || SIZE.matcher(q).find()
                || MONEY.matcher(q).find() || containsAny(q, "quần", "quan", "áo", "ao", "jean", "pants", "shirt", "mẫu", "mau", "còn", "con"))) return true;
        return containsAny(q, "tìm", "tim", "mua", "có bán", "co ban", "sản phẩm", "san pham",
                "quần", "quan", "áo", "ao", "jean", "jeans", "trousers", "pants", "shirt", "áo khoác", "ao khoac",
                "giá", "gia", "tồn kho", "ton kho", "còn hàng", "con hang", "màu", "mau")
                || hasColor(q) || SIZE.matcher(q).find() || MONEY.matcher(q).find();
    }

    public List<ChatbotProduct> search(String message) throws SQLException {
        String q = message.toLowerCase();
        List<String> colors = findColorTerms(q);
        Matcher sizeMatcher = SIZE.matcher(q);
        String size = sizeMatcher.find() ? sizeMatcher.group(1).toUpperCase() : null;
        PriceFilter price = findPrice(q);
        List<String> keywords = findKeywords(q, colors, size, price);

        StringBuilder sql = new StringBuilder("SELECT DISTINCT TOP 8 p.ProductID, p.ProductName, c.CategoryName, p.Brand, pv.Color, pv.Size, pv.Price, pv.Quantity ")
                .append("FROM dbo.Product p INNER JOIN dbo.ProductCategory c ON c.CategoryID=p.CategoryID ")
                .append("INNER JOIN dbo.ProductVariant pv ON pv.ProductID=p.ProductID ")
                .append("WHERE p.Status='Active' AND c.Status='Active' AND pv.Status='Active' ");
        List<Object> parameters = new ArrayList<>();
        if (!colors.isEmpty()) {
            sql.append("AND (");
            for (int i = 0; i < colors.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("LOWER(pv.Color) LIKE ?");
                parameters.add("%" + colors.get(i) + "%");
            }
            sql.append(") ");
        }
        if (size != null) { sql.append("AND UPPER(pv.Size)=? "); parameters.add(size); }
        if (price.min != null) { sql.append("AND pv.Price>=? "); parameters.add(price.min); }
        if (price.max != null) { sql.append("AND pv.Price<=? "); parameters.add(price.max); }
        if (!keywords.isEmpty()) {
            sql.append("AND (");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(LOWER(p.ProductName) LIKE ? OR LOWER(c.CategoryName) LIKE ? OR LOWER(COALESCE(p.Brand,'')) LIKE ? OR LOWER(COALESCE(p.Description,'')) LIKE ?)");
                String like = "%" + keywords.get(i) + "%";
                for (int n = 0; n < 4; n++) parameters.add(like);
            }
            sql.append(") ");
        }
        sql.append("ORDER BY pv.Quantity DESC, p.ProductID DESC");

        List<ChatbotProduct> results = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                Object value = parameters.get(i);
                if (value instanceof BigDecimal) statement.setBigDecimal(i + 1, (BigDecimal) value);
                else statement.setString(i + 1, (String) value);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ChatbotProduct product = new ChatbotProduct();
                    product.setProductID(rows.getInt("ProductID"));
                    product.setProductName(rows.getString("ProductName"));
                    product.setCategoryName(rows.getString("CategoryName"));
                    product.setBrand(rows.getString("Brand"));
                    product.setColor(rows.getString("Color"));
                    product.setSize(rows.getString("Size"));
                    product.setPrice(rows.getBigDecimal("Price"));
                    product.setQuantity(rows.getInt("Quantity"));
                    results.add(product);
                }
            }
        }
        return results;
    }

    private boolean hasColor(String q) { return !findColorTerms(q).isEmpty(); }
    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private List<String> findColorTerms(String q) {
        for (String[] color : COLORS) {
            for (String alias : color) {
                if (q.contains(alias)) return new ArrayList<>(Arrays.asList(color));
            }
        }
        return new ArrayList<>();
    }

    private PriceFilter findPrice(String q) {
        Matcher matcher = MONEY.matcher(q);
        while (matcher.find()) {
            String number = matcher.group(2);
            String unit = matcher.group(3);
            String direction = matcher.group(1);
            try {
                String normalized = number;
                if (number.matches("\\d{1,3}(?:[.,]\\d{3})+")) {
                    normalized = number.replace(".", "").replace(",", "");
                } else {
                    normalized = number.replace(',', '.');
                }
                BigDecimal amount = new BigDecimal(normalized);
                if (unit != null && unit.matches("(?i)triệu|trieu|tr")) amount = amount.multiply(BigDecimal.valueOf(1_000_000));
                else if (unit != null && unit.matches("(?i)nghìn|nghin|ngàn|ngan|k")) amount = amount.multiply(BigDecimal.valueOf(1_000));
                else if (unit == null && amount.compareTo(BigDecimal.valueOf(1000)) < 0) continue;
                PriceFilter filter = new PriceFilter();
                if (direction != null && direction.matches("(?i)dưới|duoi")) filter.max = amount;
                else if (direction != null && direction.matches("(?i)trên|tren")) filter.min = amount;
                else { filter.min = amount.multiply(BigDecimal.valueOf(0.8)); filter.max = amount.multiply(BigDecimal.valueOf(1.2)); }
                return filter;
            } catch (NumberFormatException ignored) { }
        }
        return new PriceFilter();
    }

    private List<String> findKeywords(String q, List<String> colors, String size, PriceFilter price) {
        List<String> keywords = new ArrayList<>();
        if (containsAny(q, "quần", "quan", "trousers", "pants", "jean")) addAll(keywords, "quần", "trousers", "pants", "jean");
        else if (containsAny(q, "áo thun", "ao thun", "t-shirt", "tshirt")) addAll(keywords, "áo thun", "t-shirt", "tshirt");
        else if (containsAny(q, "áo sơ mi", "ao so mi", "shirt")) addAll(keywords, "áo sơ mi", "shirt");
        else if (containsAny(q, "áo khoác", "ao khoac", "jacket")) addAll(keywords, "áo khoác", "jacket");
        else if (containsAny(q, "áo", "ao", "polo")) addAll(keywords, "áo", "shirt", "polo");
        return keywords;
    }
    private void addAll(List<String> list, String... values) { list.addAll(Arrays.asList(values)); }

    private static final class PriceFilter {
        private BigDecimal min;
        private BigDecimal max;
    }
}



