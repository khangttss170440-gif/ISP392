package controller;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import model.ChatbotProduct;
import model.OutfitCollection;
import model.OutfitItem.Role;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import util.ShippingJson;

/** Real HTTP sessions, fixture catalog, no external Gemini call or database writes. */
public final class ChatbotSearchIntegrationTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        List<ChatbotProduct> catalog = List.of(product(100, "Áo sơ mi trắng", "Áo sơ mi", "Trắng", "M", 400000),
                product(100, "Áo sơ mi trắng", "Áo sơ mi", "Trắng", "L", 450000),
                product(101, "Áo sơ mi đen", "Áo sơ mi", "Đen", "L", 600000),
                product(102, "Áo polo đen", "Áo polo", "Đen", "M", 300000),
                product(103, "Quần âu xám", "Quần âu", "Xám", "L", 500000));
        OutfitCollection published = new OutfitCollection(); published.setId(200); published.setTitle("Công sở gọn gàng");
        published.setStyle("office"); published.setStatus("Published");
        published.getPieces().add(new OutfitCollection.Piece(100,"Trắng",Role.TOP,"Áo sơ mi trắng"));
        published.getPieces().add(new OutfitCollection.Piece(103,"Xám",Role.BOTTOM,"Quần âu xám"));
        OutfitCollection draft = new OutfitCollection(); draft.setId(201); draft.setTitle("Bộ nháp"); draft.setStatus("Draft");
        draft.getPieces().addAll(published.getPieces());
        Tomcat tomcat = new Tomcat();
        Path base = Files.createTempDirectory(Path.of("test/.scratch"), "chatbot-http-").toAbsolutePath();
        tomcat.setBaseDir(base.toString()); tomcat.setPort(0); tomcat.getConnector();
        Context context = tomcat.addContext("/search", Files.createDirectory(base.resolve("web")).toString());
        Tomcat.addServlet(context, "chatbot-test", new ChatbotController() {
            @Override protected List<ChatbotProduct> loadCatalog() { return catalog; }
            @Override protected List<OutfitCollection> loadCollections() { return List.of(published,draft); }
        });
        context.addServletMappingDecoded("/chatbot", "chatbot-test");
        try {
            tomcat.start();
            URI endpoint = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/search/chatbot");
            HttpClient first = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
            HttpClient second = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
            Map<String,Object> initial = send(first, endpoint, "message=t%C3%ACm+%C3%A1o+s%C6%A1+mi+d%C6%B0%E1%BB%9Bi+500k", 200);
            check(ShippingJson.array(initial.get("products")).size() == 1, "deduplicated JSON cards");
            Map<String,Object> card = ShippingJson.object(ShippingJson.array(initial.get("products")).get(0));
            check("400.000 ₫".equals(card.get("price")) || card.get("price").toString().contains("400.000"), "actual matched variant price");
            check(card.get("imageUrl").toString().startsWith("/search/product-image?productId=100"), "local product image URL");
            Map<String,Object> constrained = message(first, endpoint, "còn màu đen size L");
            check(ShippingJson.array(constrained.get("products")).isEmpty(), "follow-up retains budget");
            check(constrained.get("searchSummary").toString().contains("500.000"), "active filters disclosed");
            check(!ShippingJson.array(constrained.get("suggestions")).isEmpty(), "actionable empty-state suggestions");
            Map<String,Object> independent = message(second, endpoint, "màu đen");
            check(ShippingJson.array(independent.get("products")).size() == 2, "search preferences isolated by session");
            Map<String,Object> relaxed = message(first, endpoint, "không giới hạn giá");
            check(ShippingJson.array(relaxed.get("products")).size() == 1, "only requested budget filter cleared");
            Map<String,Object> relaxedCard = ShippingJson.object(ShippingJson.array(relaxed.get("products")).get(0));
            check(ShippingJson.number(relaxedCard, "id") == 101, "family, color and size retained");
            send(first, endpoint, "reset=1", 200);
            check(ShippingJson.array(message(first, endpoint, "màu đen").get("products")).size() == 2, "reset clears shopping context");
            send(second, endpoint, "message=" + java.net.URLEncoder.encode("áo sơ mi 900k-300k", StandardCharsets.UTF_8), 400);
            Map<String,Object> outfitSearch = message(first,endpoint,"tìm bộ sưu tập đi làm dưới 1 triệu");
            check(ShippingJson.array(outfitSearch.get("products")).isEmpty() && ShippingJson.array(outfitSearch.get("collections")).size()==1,
                    "collections returned separately from product variants");
            Map<String,Object> outfitCard=ShippingJson.object(ShippingJson.array(outfitSearch.get("collections")).get(0));
            check(outfitCard.get("url").equals("/search/collections?source=curated&collectionId=200"),"specific public collection link");
            check(ShippingJson.array(outfitCard.get("items")).size()==2 && new BigDecimal(outfitCard.get("amount").toString()).compareTo(new BigDecimal("900000"))==0,
                    "collection card contains actual pieces and whole-set total");
            check(ShippingJson.array(message(first,endpoint,"còn dưới 800k").get("collections")).isEmpty(),"whole-set budget survives HTTP follow-up");
            check(ShippingJson.array(message(first,endpoint,"không giới hạn giá").get("collections")).size()==1,"collection follow-up can clear budget");
            check(ShippingJson.array(message(first,endpoint,"xem bộ sưu tập khác").get("collections")).isEmpty(),"next collection excludes already returned set");
            Map<String,Object> productAgain=message(first,endpoint,"tìm áo sơ mi");
            check(!ShippingJson.array(productAgain.get("products")).isEmpty() && !productAgain.containsKey("collections"),"explicit product request leaves collection mode");
            System.out.println("Chatbot HTTP/session integration checks passed: " + checks);
        } finally { tomcat.stop(); tomcat.destroy(); }
    }
    private static Map<String,Object> message(HttpClient client, URI endpoint, String text) throws Exception {
        return send(client, endpoint, "message=" + java.net.URLEncoder.encode(text, StandardCharsets.UTF_8), 200);
    }
    private static Map<String,Object> send(HttpClient client, URI endpoint, String body, int expectedStatus) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != expectedStatus) throw new AssertionError("Chat HTTP status " + response.statusCode());
        return ShippingJson.object(ShippingJson.read(response.body()));
    }
    private static ChatbotProduct product(int id, String name, String category, String color, String size, int price) {
        ChatbotProduct product = new ChatbotProduct(); product.setProductID(id); product.setProductName(name);
        product.setCategoryName(category); product.setColor(color); product.setSize(size); product.setPrice(BigDecimal.valueOf(price));
        product.setQuantity(5); return product;
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
}
