package controller;

import dao.ChatbotProductDAO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.ChatbotProduct;
import model.ChatbotCollection;
import model.OutfitCollection;
import service.CollectionSearchService;
import model.ProductSearchCriteria;
import service.ChatbotService;
import service.ChatbotService.ChatbotMessage;
import service.ProductSearchService;
import service.ProductSearchService.Result;
import util.AppConfig;
import util.ShippingJson;

@WebServlet("/chatbot")
public class ChatbotController extends HttpServlet {
    private static final String HISTORY_KEY = "chatbot.geminiHistory";
    private static final String SEARCH_KEY = "chatbot.productSearchCriteria";
    private static final String RATE_WINDOW_KEY = "chatbot.rateWindowStart";
    private static final String RATE_COUNT_KEY = "chatbot.rateCount";
    private final ChatbotService chatbotService = new ChatbotService();
    private final ChatbotProductDAO productDAO = new ChatbotProductDAO();
    private final ProductSearchService searchService = new ProductSearchService();
    private final CollectionSearchService collectionSearch = new CollectionSearchService();

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        if ("1".equals(request.getParameter("reset"))) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(HISTORY_KEY);
                session.removeAttribute(SEARCH_KEY);
                session.removeAttribute("chatbot.productSearchActive");
            }
            writeJson(response, 200, Map.of("ok", true));
            return;
        }
        String message = request.getParameter("message");
        if (message == null || message.isBlank()) {
            writeError(response, 400, "Bạn hãy nhập nội dung cần hỏi nhé."); return;
        }
        message = message.trim();
        if (message.length() > AppConfig.integer("CHATBOT_MAX_MESSAGE_LENGTH", 1, 10000)) {
            writeError(response, 400, "Tin nhắn tối đa " + AppConfig.integer("CHATBOT_MAX_MESSAGE_LENGTH", 1, 10000) + " ký tự."); return;
        }
        HttpSession session = request.getSession(true);
        if (!allowRequest(session)) {
            writeError(response, 429, "Bạn gửi hơi nhanh. Vui lòng đợi một chút rồi thử lại."); return;
        }
        @SuppressWarnings("unchecked")
        List<ChatbotMessage> stored = (List<ChatbotMessage>) session.getAttribute(HISTORY_KEY);
        List<ChatbotMessage> history = stored == null ? new ArrayList<>() : new ArrayList<>(stored);
        while (history.size() > AppConfig.integer("CHATBOT_MAX_HISTORY_MESSAGES", 2, 100) - 2) history.remove(0);
        Result result = null;
        try {
            ProductSearchCriteria previous = session.getAttribute(SEARCH_KEY) instanceof ProductSearchCriteria
                    ? (ProductSearchCriteria) session.getAttribute(SEARCH_KEY) : null;
            List<ChatbotProduct> catalog = loadCatalog();
            result = searchService.search(message, previous, catalog);
            if (result.search) {
                if (result.criteria.collectionMode) {
                    List<ChatbotCollection> collections = collectionSearch.search(result.criteria, catalog, loadCollections());
                    session.setAttribute(SEARCH_KEY, result.criteria);
                    String reply = collections.isEmpty()
                            ? "Mình chưa tìm thấy bộ sưu tập đã đăng và còn đủ các món khớp điều kiện này. Bạn thử đổi dịp sử dụng hoặc nới ngân sách nhé."
                            : "Mình tìm được " + collections.size() + " bộ sưu tập đã đăng, còn đủ các món phù hợp. Giá bên dưới là tổng các món trong bộ; bạn mở bộ sưu tập để chọn màu và size nhé.";
                    saveHistory(session, history, message, reply);
                    writeCollectionResponse(response, reply, result, collections, request.getContextPath());
                    return;
                }
                session.setAttribute(SEARCH_KEY, result.criteria);
                String reply = productReply(result);
                if (result.advice && !result.products.isEmpty()) {
                    reply = chatbotService.replyForProducts(message, history, productContext(result)).getText();
                }
                saveHistory(session, history, message, reply);
                writeSearchResponse(response, reply, result, request.getContextPath());
            } else {
                String reply = chatbotService.reply(message, history).getText();
                saveHistory(session, history, message, reply);
                writeJson(response, 200, Map.of("reply", reply, "products", List.of()));
            }
        } catch (SQLException error) {
            getServletContext().log("Chatbot catalog lookup failed.", error);
            writeError(response, 503, "Chưa đọc được danh mục sản phẩm. Bạn thử lại sau nhé.");
        } catch (IllegalArgumentException error) {
            writeError(response, 400, error.getMessage() == null ? "Bạn kiểm tra lại điều kiện tìm kiếm nhé." : error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            getServletContext().log("Chatbot advice interrupted.");
            fallback(response, session, history, message, result, request.getContextPath());
        } catch (IOException | IllegalStateException error) {
            getServletContext().log("Chatbot advice unavailable: " + error.getClass().getSimpleName());
            fallback(response, session, history, message, result, request.getContextPath());
        }
    }

    protected List<ChatbotProduct> loadCatalog() throws SQLException {
        return productDAO.getSearchableCatalog();
    }

    protected List<OutfitCollection> loadCollections() throws SQLException {
        return new dao.OutfitCollectionDAO().list(true);
    }

    private void fallback(HttpServletResponse response, HttpSession session, List<ChatbotMessage> history,
            String message, Result result, String contextPath) throws IOException {
        if (result != null && result.search) {
            String reply = productReply(result);
            saveHistory(session, history, message, reply);
            writeSearchResponse(response, reply, result, contextPath);
        } else writeError(response, 503, "Trợ lý tư vấn đang bận. Bạn vẫn có thể hỏi tên sản phẩm, màu, size hoặc khoảng giá để mình tìm trong cửa hàng nhé.");
    }

    private String productReply(Result result) {
        if (result.products.isEmpty()) {
            if (result.summary.isBlank()) return "Bạn muốn tìm loại sản phẩm nào? Cho mình biết thêm màu, size hoặc ngân sách nhé.";
            if (result.outOfStock) return "Có mẫu khớp điều kiện bạn chọn, nhưng các phiên bản phù hợp hiện đã hết hàng. Bạn có thể thử màu hoặc size khác nhé.";
            if (!result.criteria.shownProductIDs.isEmpty()) return "Mình đã hiển thị hết các mẫu còn hàng khớp điều kiện này. Bạn có thể thay đổi màu, size hoặc khoảng giá để xem thêm.";
            return "Mình chưa tìm thấy mẫu còn hàng khớp đầy đủ các điều kiện này. Bạn có thể bỏ bớt một điều kiện bên dưới để tìm lại.";
        }
        String missing = result.missingGroups.isEmpty() ? "" : " Chưa có lựa chọn khớp cho: " + String.join(", ", result.missingGroups) + ".";
        if (result.criteria.requestedFamilies.size() > 1) return "Mình đã chọn các món còn hàng theo từng nhóm bạn cần. Đây là các gợi ý sản phẩm lẻ; bạn có thể mở từng món để chọn size." + missing;
        return "Mình tìm được " + result.products.size() + " mẫu còn hàng phù hợp. Bạn xem ảnh và thông tin bên dưới nhé."
                + (result.criteria.sort == ProductSearchCriteria.Sort.PRICE_ASC ? " Các mẫu đang xếp theo giá từ thấp đến cao." : "")
                + " Bạn có thể hỏi tiếp, chẳng hạn đổi màu, đổi size hoặc xem mẫu khác.";
    }

    private String productContext(Result result) {
        StringBuilder context = new StringBuilder("Điều kiện tìm kiếm đang áp dụng: ").append(result.summary)
                .append(". Chỉ tư vấn các mẫu trong danh sách này; không tự thêm mẫu, đổi giá hoặc suy đoán size khác.\n");
        for (ChatbotProduct product : result.products) {
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("id", product.getProductID()); item.put("name", product.getProductName());
            item.put("category", product.getCategoryName()); item.put("material", product.getMaterial());
            item.put("description", product.getDescription()); item.put("color", product.getColor());
            item.put("size", product.getSize()); item.put("priceVnd", product.getPrice());
            item.put("stock", product.getQuantity());
            context.append(ShippingJson.write(item)).append('\n');
        }
        return context.toString();
    }

    private void writeSearchResponse(HttpServletResponse response, String reply, Result result, String contextPath) throws IOException {
        List<Map<String,Object>> products = new ArrayList<>();
        for (ChatbotProduct product : result.products) {
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("id", product.getProductID()); item.put("name", product.getProductName());
            item.put("brand", product.getBrand()); item.put("color", product.getColor()); item.put("size", product.getSize());
            item.put("price", AppConfig.money(product.getPrice())); item.put("stock", "Còn " + product.getQuantity() + " sản phẩm");
            item.put("url", contextPath + "/product-detail?productId=" + product.getProductID());
            String color = product.getColor() == null ? "" : product.getColor();
            item.put("imageUrl", contextPath + "/product-image?productId=" + product.getProductID()
                    + "&color=" + URLEncoder.encode(color, StandardCharsets.UTF_8));
            products.add(item);
        }
        writeJson(response, 200, Map.of("reply", reply, "products", products,
                "searchSummary", result.summary, "suggestions", result.suggestions));
    }

    private void writeCollectionResponse(HttpServletResponse response, String reply, Result result,
            List<ChatbotCollection> matches, String contextPath) throws IOException {
        List<Map<String,Object>> collections = new ArrayList<>();
        for (ChatbotCollection collection : matches) {
            Map<String,Object> card = new LinkedHashMap<>();
            card.put("id", collection.id); card.put("name", collection.title); card.put("description", collection.description);
            card.put("style", java.util.Arrays.stream(service.OutfitRecommendationService.Style.values())
                    .filter(style -> style.getId().equals(collection.style)).map(service.OutfitRecommendationService.Style::getLabel)
                    .findFirst().orElse(collection.style));
            card.put("price", "Cả bộ từ " + AppConfig.money(collection.fromPrice));
            card.put("amount", collection.fromPrice);
            card.put("url", contextPath + "/collections?source=curated&collectionId=" + collection.id);
            List<Map<String,Object>> pieces = new ArrayList<>();
            for (ChatbotProduct piece : collection.items) {
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("id", piece.getProductID()); item.put("name", piece.getProductName());
                item.put("color", piece.getColor()); item.put("size", piece.getSize());
                item.put("price", AppConfig.money(piece.getPrice()));
                item.put("imageUrl", contextPath + "/product-image?productId=" + piece.getProductID() + "&color="
                        + URLEncoder.encode(piece.getColor() == null ? "" : piece.getColor(), StandardCharsets.UTF_8));
                pieces.add(item);
            }
            card.put("items", pieces); collections.add(card);
        }
        List<String> actions = new ArrayList<>();
        if (!matches.isEmpty()) { actions.add("Giá thấp nhất"); actions.add("Xem bộ sưu tập khác"); }
        if (result.criteria.minPrice != null || result.criteria.maxPrice != null) actions.add("Không giới hạn giá");
        actions.add("Tìm sản phẩm mới");
        writeJson(response, 200, Map.of("reply", reply, "products", List.of(), "collections", collections,
                "searchSummary", result.summary + " · ngân sách tính cho cả bộ", "suggestions", actions));
    }

    private void saveHistory(HttpSession session, List<ChatbotMessage> history, String message, String reply) {
        history.add(new ChatbotMessage("user", message)); history.add(new ChatbotMessage("model", reply));
        while (history.size() > AppConfig.integer("CHATBOT_MAX_HISTORY_MESSAGES", 2, 100)) history.remove(0);
        session.setAttribute(HISTORY_KEY, history);
    }

    private boolean allowRequest(HttpSession session) {
        synchronized (session) {
            long now = System.currentTimeMillis();
            Long start = (Long) session.getAttribute(RATE_WINDOW_KEY);
            Integer count = (Integer) session.getAttribute(RATE_COUNT_KEY);
            if (start == null || now - start >= AppConfig.integer("CHATBOT_RATE_WINDOW_SECONDS", 1, 3600) * 1000L) {
                session.setAttribute(RATE_WINDOW_KEY, now); session.setAttribute(RATE_COUNT_KEY, 1); return true;
            }
            if (count != null && count >= AppConfig.integer("CHATBOT_MAX_REQUESTS", 1, 1000)) return false;
            session.setAttribute(RATE_COUNT_KEY, count == null ? 1 : count + 1); return true;
        }
    }
    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        writeJson(response, status, Map.of("error", message));
    }
    private void writeJson(HttpServletResponse response, int status, Map<String,?> body) throws IOException {
        response.setStatus(status);
        response.getOutputStream().write(ShippingJson.write(body).getBytes(StandardCharsets.UTF_8));
    }
}
