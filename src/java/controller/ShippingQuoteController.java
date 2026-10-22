package controller;

import dao.CustomerAddressDAO;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Customer;
import model.CustomerAddress;
import model.Cart;
import service.ShippingService;

@WebServlet("/customer/shipping-quote")
public class ShippingQuoteController extends HttpServlet {
    private final ShippingService shipping = new ShippingService();
    private final CustomerAddressDAO addresses = new CustomerAddressDAO();

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        Object account = session == null ? null : session.getAttribute("customer");
        if (!(account instanceof Customer)) { response.setStatus(401); response.getWriter().write("{\"error\":\"Vui lòng đăng nhập lại.\"}"); return; }
        synchronized (session) {
            Long last = (Long) session.getAttribute("shipping.lastQuote");
            if (last != null && System.currentTimeMillis() - last < 1500) {
                response.setStatus(429); response.getWriter().write("{\"error\":\"Vui lòng đợi một chút rồi thử lại.\"}"); return;
            }
            session.setAttribute("shipping.lastQuote", System.currentTimeMillis());
        }
        try {
            CustomerAddress address;
            String id = request.getParameter("addressId");
            if (id != null && !id.isBlank() && !"new".equals(id)) {
                address = addresses.findById(Integer.parseInt(id), ((Customer) account).getCustomerID());
                if (address == null) throw new IllegalArgumentException("Địa chỉ không thuộc tài khoản của bạn.");
            } else {
                address = new CustomerAddress();
                address.setAddressLine(value(request, "addressLine", 1000));
                address.setWard(value(request, "ward", 100));
                address.setDistrict(value(request, "district", 100));
                address.setProvince(value(request, "province", 100));
                if (address.getAddressLine().isEmpty() || address.getProvince().isEmpty()) throw new IllegalArgumentException("Nhập địa chỉ chi tiết và tỉnh/thành phố để tìm kiếm.");
            }
            Object cartValue = session.getAttribute("CART");
            BigDecimal subtotal = cartValue instanceof Cart ? ((Cart) cartValue).getSubtotal() : BigDecimal.ZERO;
            int quantity = cartValue instanceof Cart ? ((Cart) cartValue).getTotalQuantity() : 0;
            ShippingService.Quote quote = shipping.quote(address, subtotal, quantity);
            response.getWriter().write("{\"fee\":" + quote.fee.toPlainString() + ",\"priced\":" + quote.isPriced()
                    + ",\"freeByOrderValue\":" + quote.freeByOrderValue
                    + ",\"zone\":" + json(quote.zone == null ? "" : quote.zone)
                    + ",\"estimatedHalfKilogramUnits\":" + quote.estimatedHalfKilogramUnits
                    + ",\"address\":" + json(address.locationAddress()) + ",\"message\":" + json(quote.message) + "}");
        } catch (IllegalArgumentException e) {
            response.setStatus(400); response.getWriter().write("{\"error\":" + json(e.getMessage()) + "}");
        } catch (SQLException e) {
            getServletContext().log("Could not load address for shipping.", e);
            response.setStatus(503); response.getWriter().write("{\"error\":\"Chưa đọc được địa chỉ. Vui lòng thử lại.\"}");
        }
    }

    private String value(HttpServletRequest request, String field, int max) {
        String value = request.getParameter(field); value = value == null ? "" : value.trim();
        if (value.length() > max || value.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException("Địa chỉ không hợp lệ.");
        return value;
    }
    private String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
}
