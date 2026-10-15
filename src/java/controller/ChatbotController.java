package controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.NumberFormat;
import java.sql.SQLException;
import dao.ChatbotProductDAO;
import model.ChatbotProduct;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import service.ChatbotService;
import service.ChatbotService.ChatbotApiException;
import service.ChatbotService.ChatbotMessage;
import service.ChatbotService.ChatbotReply;

@WebServlet("/chatbot")
public class ChatbotController extends HttpServlet {

    private static final String HISTORY_KEY = "chatbot.geminiHistory";
    private static final String RATE_WINDOW_KEY = "chatbot.rateWindowStart";
    private static final String RATE_COUNT_KEY = "chatbot.rateCount";
    private static final long RATE_WINDOW_MILLIS = 60_000L;
    private static final int MAX_REQUESTS_PER_WINDOW = 12;
    private final ChatbotService chatbotService = new ChatbotService();
    private final ChatbotProductDAO productDAO = new ChatbotProductDAO();
    private static final String PRODUCT_SEARCH_KEY = "chatbot.productSearchActive";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");

        String reset = request.getParameter("reset");
        if ("1".equals(reset)) {
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                existingSession.removeAttribute(HISTORY_KEY);
                existingSession.removeAttribute(PRODUCT_SEARCH_KEY);
            }
            writeJson(response, HttpServletResponse.SC_OK,
                    "{\"ok\":true}");
            return;
        }

        String message = request.getParameter("message");
        if (message == null || message.trim().isEmpty()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Bạn hãy nhập nội dung cần hỏi nhé.");
            return;
        }
        message = message.trim();
        if (message.length() > 1200) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Tin nhắn tối đa 1.200 ký tự.");
            return;
        }

        HttpSession session = request.getSession(true);
        if (!allowRequest(session)) {
            writeError(response, 429,
                    "Bạn gửi hơi nhanh. Vui lòng đợi một chút rồi thử lại.");
            return;
        }

        @SuppressWarnings("unchecked")
        List<ChatbotMessage> storedHistory =
                (List<ChatbotMessage>) session.getAttribute(HISTORY_KEY);
        List<ChatbotMessage> history = storedHistory == null
                ? new ArrayList<>() : new ArrayList<>(storedHistory);
        while (history.size() > 18) {
            history.remove(0);
        }

        boolean productSearch = false;
        List<ChatbotProduct> products = new ArrayList<>();
        try {
            boolean continuingSearch = Boolean.TRUE.equals(session.getAttribute(PRODUCT_SEARCH_KEY));
            productSearch = productDAO.isProductSearch(message, continuingSearch);
            products = productSearch
                    ? productDAO.search(message) : new ArrayList<>();
            if (productSearch) session.setAttribute(PRODUCT_SEARCH_KEY, Boolean.TRUE);
            String catalogContext = productSearch ? productContext(products) : null;
            ChatbotReply answer = chatbotService.reply(message, history, catalogContext);
            history.add(new ChatbotMessage("user", message));
            history.add(new ChatbotMessage("model", answer.getText()));
            session.setAttribute(HISTORY_KEY, history);
            writeJson(response, HttpServletResponse.SC_OK,
                    "{\"reply\":" + jsonString(answer.getText())
                    + ",\"products\":" + productJson(products, request.getContextPath()) + "}");
        } catch (IllegalStateException e) {
            getServletContext().log("Chatbot is not configured.", e);
            if (productSearch) {
                writeProductFallback(response, session, history, message, products, request.getContextPath());
                return;
            }
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Trợ lý AI hiện chưa sẵn sàng. Bạn vui lòng thử lại sau hoặc liên hệ hỗ trợ khách hàng nhé.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getServletContext().log("Chatbot request was interrupted.", e);
            if (productSearch) {
                writeProductFallback(response, session, history, message, products, request.getContextPath());
                return;
            }
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Chatbot đang bận. Bạn thử gửi lại sau nhé.");
        } catch (ChatbotApiException e) {
            getServletContext().log("Chatbot API request failed.", e);
            if (productSearch) {
                writeProductFallback(response, session, history, message, products, request.getContextPath());
                return;
            }
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    apiErrorMessage(e.getStatusCode()));
        } catch (SQLException e) {
            getServletContext().log("Chatbot product catalog lookup failed.", e);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Chưa đọc được danh mục sản phẩm. Bạn thử lại sau nhé.");
        } catch (IOException e) {
            getServletContext().log("Chatbot API request failed.", e);
            if (productSearch) {
                writeProductFallback(response, session, history, message, products, request.getContextPath());
                return;
            }
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Trợ lý AI đang gặp lỗi kết nối. Bạn vui lòng thử lại sau nhé.");
        }
    }

    private void writeProductFallback(HttpServletResponse response, HttpSession session,
            List<ChatbotMessage> history, String message, List<ChatbotProduct> products,
            String contextPath) throws IOException {
        String fallback = products.isEmpty()
                ? "Mình chưa tìm thấy sản phẩm phù hợp trong danh mục hiện tại. Bạn thử đổi màu, size hoặc mức giá nhé. (Gemini đang tạm thời không phản hồi.)"
                : "Gemini đang tạm thời không phản hồi, nhưng mình đã tra cứu được "
                + products.size() + " phiên bản sản phẩm từ danh mục cửa hàng. Bạn xem các kết quả bên dưới nhé.";
        history.add(new ChatbotMessage("user", message));
        history.add(new ChatbotMessage("model", fallback));
        while (history.size() > 20) history.remove(0);
        session.setAttribute(HISTORY_KEY, history);
        writeJson(response, HttpServletResponse.SC_OK,
                "{\"reply\":" + jsonString(fallback)
                + ",\"products\":" + productJson(products, contextPath) + "}");
    }

    private String productContext(List<ChatbotProduct> products) {
        if (products.isEmpty()) return "Không có sản phẩm/phiên bản đang hoạt động nào khớp điều kiện tìm kiếm trong cơ sở dữ liệu.";
        StringBuilder context = new StringBuilder("Sản phẩm đang hoạt động khớp tìm kiếm:\n");
        NumberFormat money = NumberFormat.getInstance(new Locale("vi", "VN"));
        for (ChatbotProduct p : products) {
            context.append("- ").append(p.getProductName()).append("; danh mục: ")
                    .append(p.getCategoryName()).append("; thương hiệu: ")
                    .append(p.getBrand() == null ? "không rõ" : p.getBrand())
                    .append("; màu: ").append(p.getColor()).append("; size: ")
                    .append(p.getSize()).append("; giá: ").append(money.format(p.getPrice()))
                    .append(" đồng; còn: ").append(p.getQuantity()).append(" sản phẩm.\n");
        }
        return context.toString();
    }

    private String productJson(List<ChatbotProduct> products, String contextPath) {
        StringBuilder json = new StringBuilder("[");
        NumberFormat money = NumberFormat.getInstance(new Locale("vi", "VN"));
        for (int i = 0; i < products.size(); i++) {
            ChatbotProduct p = products.get(i);
            if (i > 0) json.append(',');
            json.append("{\"name\":").append(jsonString(p.getProductName()))
                    .append(",\"brand\":").append(jsonString(p.getBrand() == null ? "" : p.getBrand()))
                    .append(",\"color\":").append(jsonString(p.getColor()))
                    .append(",\"size\":").append(jsonString(p.getSize()))
                    .append(",\"price\":").append(jsonString(money.format(p.getPrice()) + "₫"))
                    .append(",\"stock\":").append(jsonString(p.getQuantity() > 0
                            ? "Còn " + p.getQuantity() + " sản phẩm" : "Tạm hết hàng"))
                    .append(",\"url\":").append(jsonString(contextPath + "/product-detail?productId=" + p.getProductID()))
                    .append('}');
        }
        return json.append(']').toString();
    }

    private String apiErrorMessage(int statusCode) {
        switch (statusCode) {
            case 400:
            case 401:
            case 403:
            case 404:
                return "Trợ lý AI hiện chưa sẵn sàng. Bạn vui lòng thử lại sau nhé.";
            case 429:
            case 503:
                return "Trợ lý AI đang bận. Bạn vui lòng thử lại sau ít phút nhé.";
            default:
                return "Trợ lý AI tạm thời chưa trả lời được. Bạn vui lòng thử lại sau nhé.";
        }
    }

    private boolean allowRequest(HttpSession session) {
        long now = System.currentTimeMillis();
        Long windowStart = (Long) session.getAttribute(RATE_WINDOW_KEY);
        Integer requestCount = (Integer) session.getAttribute(RATE_COUNT_KEY);
        if (windowStart == null || now - windowStart >= RATE_WINDOW_MILLIS) {
            session.setAttribute(RATE_WINDOW_KEY, now);
            session.setAttribute(RATE_COUNT_KEY, 1);
            return true;
        }
        if (requestCount != null && requestCount >= MAX_REQUESTS_PER_WINDOW) {
            return false;
        }
        session.setAttribute(RATE_COUNT_KEY,
                requestCount == null ? 1 : requestCount + 1);
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        writeJson(response, status, "{\"error\":" + jsonString(message) + "}");
    }

    private void writeJson(HttpServletResponse response, int status, String body)
            throws IOException {
        response.setStatus(status);
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private String jsonString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
            }
        }
        return escaped.append('"').toString();
    }
}



