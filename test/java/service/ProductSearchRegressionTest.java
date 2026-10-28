package service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import model.ChatbotProduct;
import service.ProductSearchService.Result;

/** Offline relevance, exact-filter, Vietnamese parsing and conversation regression cases. */
public final class ProductSearchRegressionTest {
    private static int checks;
    private static final ProductSearchService SEARCH = new ProductSearchService();
    private static final List<ChatbotProduct> CATALOG = new ArrayList<>();
    public static void main(String[] args) {
        item(1, "Áo sơ mi Oxford trắng", "Áo sơ mi", "Trắng", "L", 450000, 5, "Cotton Oxford", "Phù hợp đi làm");
        item(1, "Áo sơ mi Oxford trắng", "Áo sơ mi", "Trắng", "M", 400000, 4, "Cotton Oxford", "Phù hợp đi làm");
        item(2, "Áo polo cotton piqué", "Áo polo", "Navy", "L", 350000, 100, "Cotton", "Mặc hằng ngày");
        item(3, "Áo sơ mi đen", "Áo sơ mi", "Đen", "L", 600000, 2, "Cotton", "Đi làm");
        item(4, "Đồng hồ dây da Classic", "Đồng hồ", "Nâu", "40mm", 1590000, 3, "Da bò", "Đi làm");
        item(5, "Thắt lưng da bò", "Thắt lưng", "Đen", "100cm", 390000, 5, "Da bò thật", "Khóa kim");
        item(6, "Ví da bò gập đôi", "Ví & bao thẻ", "Nâu", "Free Size", 450000, 4, "Da bò thật", "Gọn nhẹ");
        item(7, "Áo thun basic", "Áo thun", "Xám", "L", 250000, 0, "Cotton", "Đi chơi");
        item(8, "Áo polo đen", "Áo polo", "Đen", "M", 320000, 10, "Cotton", "Đi chơi");
        item(9, "Áo sơ mi Oxford xanh", "Áo sơ mi", "Xanh dương", "L", 480000, 3, "Cotton Oxford", "Đi làm");
        item(10, "Áo sơ mi Oxford cao cấp", "Áo sơ mi", "Trắng", "L", 1200000, 7, "Cotton", "Dự tiệc");
        item(11, "Áo khoác gọn", "Áo khoác", "Đen", "L", 1400000, 50, "Da", "Mặc cùng áo sơ mi");
        item(12, "Áo khoác da lộn", "Áo khoác", "Xám", "L", 2000000, 2, "Da lộn", "Đi chơi");
        item(13, "Giày sneaker da trắng", "Giày", "Trắng", "42", 1000000, 6, "Da bò", "Đi chơi");
        item(14, "Ví da bò gập đôi đen", "Ví & bao thẻ", "Đen", "Free Size", 450000, 2, "Da bò", "Gọn nhẹ");
        item(15, "Đồng hồ Classic đen", "Đồng hồ", "Đen", "40mm", 1590000, 4, "Da bò", "Đi làm");

        check(ids(search("tim dong ho")).equals(Set.of(4, 15)), "accessory search and no substring color collision");
        check(ids(search("đồng hồ dưới 2tr")).equals(Set.of(4, 15)), "accented product and million budget");
        Result shirts = search("tim ao so mi duoi 500k");
        check(ids(shirts).equals(Set.of(1, 9)), "specific category wins over stock and description mentions");
        check(shirts.products.size() == ids(shirts).size(), "deduplicate variant cards by product");
        Result whiteL = SEARCH.search("còn màu trắng size L không", shirts.criteria, CATALOG);
        check(ids(whiteL).equals(Set.of(1)) && whiteL.products.get(0).getSize().equals("L"), "follow-up retains category and price");
        Result black = SEARCH.search("còn màu đen", whiteL.criteria, CATALOG);
        check(black.products.isEmpty() && black.criteria.maxPrice.compareTo(new BigDecimal("500000")) == 0, "do not silently relax budget");
        Result noBudget = SEARCH.search("không giới hạn giá", black.criteria, CATALOG);
        check(ids(noBudget).equals(Set.of(3)) && noBudget.criteria.size.equals("L"), "clear only requested filter");
        Result wallets = SEARCH.search("tìm ví", whiteL.criteria, CATALOG);
        check(ids(wallets).equals(Set.of(6, 14)) && wallets.criteria.size == null && wallets.criteria.maxPrice == null,
                "changing category resets previous constraints");
        check(ids(search("polo màu navy")).equals(Set.of(2)), "navy does not also select black");
        Result polo = search("polo");
        check(ids(SEARCH.search("không chọn màu đen", polo.criteria, CATALOG)).equals(Set.of(2)), "excluded colors");
        Result range = search("áo sơ mi từ 300 đến 500k");
        check(ids(range).equals(Set.of(1, 9)) && range.criteria.minPrice.compareTo(new BigDecimal("300000")) == 0,
                "range inherits shared currency unit");
        check(ids(search("sơ mi 300.000-500.000")).equals(Set.of(1, 9)), "thousands separators in both range endpoints");
        check(ids(search("ao so mi tren 400k duoi 500k")).equals(Set.of(1, 9)), "combine lower and upper price clauses");
        check(ids(search("ao polo tam 400k")).equals(Set.of(2, 8)), "approximate configured budget tolerance");
        Result million = search("đồng hồ tầm 1tr5");
        check(ids(million).equals(Set.of(4, 15)) && million.criteria.minPrice.compareTo(new BigDecimal("1200000")) == 0,
                "Vietnamese million shorthand");
        check(ids(search("that lung 100cm")).equals(Set.of(5)), "catalog accessory sizes");
        check(ids(search("giay size 42 toi da 1.000.000")).equals(Set.of(13)), "numeric shoe size is not a budget");
        check(ids(search("tim oxfrd")).equals(Set.of(1, 9, 10)), "minor name typo");
        Result soldOut = search("áo thun xám");
        check(soldOut.products.isEmpty() && soldOut.outOfStock, "stock zero is not advertised as available");
        check(!search("xin chào").search && !search("cảm ơn").search, "greetings are not product queries");
        check(search("tim do nam").criteria.colors.isEmpty(), "unaccented do nam is not red");
        Result restart = SEARCH.search("tìm sản phẩm mới", whiteL.criteria, CATALOG);
        check(restart.products.isEmpty() && restart.summary.isBlank() && !restart.suggestions.isEmpty(), "restart asks a useful question");
        check(SEARCH.search("xem mẫu khác", polo.criteria, CATALOG).products.isEmpty(), "next results exclude previous product IDs");
        Result selected = SEARCH.search("mẫu 2 size L", shirts.criteria, CATALOG);
        check(selected.products.size() == 1 && selected.products.get(0).getProductID() == shirts.criteria.lastProductIDs.get(1),
                "ordinal follow-up references actual returned products");
        check(search("ao so mi size XXL").products.isEmpty(), "unknown size does not fall back to another size");
        check(whiteL.criteria != shirts.criteria && shirts.criteria.size == null, "conversation state copies avoid cross-turn mutation");
        item(16, "Đồng hồ bạc", "Đồng hồ", "Bạc", "40mm", 1700000, 2, "Thép", "Đi làm");
        check(ids(search("tim dong ho mau bac")).equals(Set.of(16)), "new catalog colors require no source-code edits");
        item(17, "Quần âu dáng slim", "Quần âu", "Đen", "L", 500000, 5, "Vải", "Đi làm");
        item(18, "Cà vạt lụa xám", "Cà vạt", "Xám", "Free Size", 180000, 5, "Lụa", "Đi làm");
        Result work = search("tìm đồ đi làm gồm quần áo giày đồng hồ cà vatk");
        check(work.criteria.requestedFamilies.size() == 5 && work.missingGroups.isEmpty(), "multi-category request and typo in cà vạt");
        check(work.products.stream().anyMatch(product -> product.getProductID() == 17)
                && work.products.stream().anyMatch(product -> product.getProductID() == 18)
                && work.products.stream().anyMatch(product -> product.getCategoryName().equals("Giày"))
                && work.products.stream().anyMatch(product -> product.getCategoryName().equals("Đồng hồ"))
                && work.products.stream().anyMatch(product -> product.getCategoryName().contains("Áo")),
                "requested clothing, footwear and accessory groups are represented");
        try { search("áo sơ mi 900k-300k"); throw new AssertionError("reversed price range accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        System.out.println("Product search regression checks passed: " + checks);
    }

    private static Result search(String message) {
        Result result = SEARCH.search(message, null, CATALOG);
        if (Boolean.getBoolean("search.debug")) System.out.println(message + " -> " + ids(result) + "; " + result.summary
                + "; terms=" + (result.criteria == null ? "none" : result.criteria.terms));
        return result;
    }
    private static Set<Integer> ids(Result result) { return result.products.stream().map(ChatbotProduct::getProductID).collect(Collectors.toSet()); }
    private static void item(int id, String name, String category, String color, String size, int price, int stock, String material, String description) {
        ChatbotProduct product = new ChatbotProduct();
        product.setProductID(id); product.setProductName(name); product.setCategoryName(category);
        product.setColor(color); product.setSize(size); product.setPrice(BigDecimal.valueOf(price)); product.setQuantity(stock);
        product.setMaterial(material); product.setDescription(description); product.setBrand("Fixture"); product.setSku("TEST-" + id);
        CATALOG.add(product);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
