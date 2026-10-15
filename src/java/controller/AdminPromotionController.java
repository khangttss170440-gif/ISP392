package controller;

import dao.AdminPromotionDAO;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Promotion;
import model.Staff;

@WebServlet("/admin/promotions")
public class AdminPromotionController extends HttpServlet {

    private static final String PAGE = "/admin/promotions.jsp";
    private static final String MESSAGE_KEY = "ADMIN_PROMOTION_MESSAGE";
    private static final String ERROR_KEY = "ADMIN_PROMOTION_ERROR";
    private static final String CSRF_KEY = "adminPromotions.csrf";

    private AdminPromotionDAO promotionDAO;

    @Override
    public void init() {
        promotionDAO = new AdminPromotionDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if (requireAdmin(request, response) == null) {
            return;
        }

        HttpSession session = request.getSession(false);
        response.setHeader("Cache-Control", "no-store");
        if (session.getAttribute(CSRF_KEY) == null) session.setAttribute(CSRF_KEY, java.util.UUID.randomUUID().toString());
        request.setAttribute("csrfToken", session.getAttribute(CSRF_KEY));
        request.setAttribute("message", takeFlash(session, MESSAGE_KEY));
        request.setAttribute("error", takeFlash(session, ERROR_KEY));

        try {
            request.setAttribute(
                    "promotions",
                    promotionDAO.getAllPromotions()
            );
        } catch (SQLException e) {
            getServletContext().log("Could not load promotions.", e);
            request.setAttribute(
                    "error",
                    "Không tải được danh sách khuyến mãi."
            );
        }

        request.getRequestDispatcher(PAGE).forward(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if (requireAdmin(request, response) == null) {
            return;
        }

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Object token = session.getAttribute(CSRF_KEY);
        if (!(token instanceof String) || !token.equals(request.getParameter("csrfToken"))) {
            response.sendError(403, "Phiên làm việc không hợp lệ. Vui lòng tải lại trang.");
            return;
        }
        String action = request.getParameter("action");

        try {
            if ("create".equals(action)) {
                promotionDAO.createPromotion(readPromotion(request));
                session.setAttribute(
                        MESSAGE_KEY,
                        "Đã tạo chương trình khuyến mãi."
                );

            } else if ("update".equals(action)) {
                Promotion promotion = readPromotion(request);
                promotion.setPromotionID(
                        Integer.parseInt(
                                request.getParameter("promotionId")
                        )
                );
                promotionDAO.updatePromotion(promotion);
                session.setAttribute(
                        MESSAGE_KEY,
                        "Đã cập nhật chương trình khuyến mãi."
                );

            } else if ("toggle".equals(action)) {
                promotionDAO.updatePromotionStatus(
                        Integer.parseInt(
                                request.getParameter("promotionId")
                        ),
                        request.getParameter("status")
                );
                session.setAttribute(
                        MESSAGE_KEY,
                        "Đã cập nhật trạng thái khuyến mãi."
                );

            } else if ("delete".equals(action)) {
                promotionDAO.deletePromotion(Integer.parseInt(request.getParameter("promotionId")));
                session.setAttribute(MESSAGE_KEY, "Đã xóa chương trình khuyến mãi.");
            } else {
                throw new IllegalArgumentException(
                        "Thao tác khuyến mãi không hợp lệ."
                );
            }

        } catch (NumberFormatException e) {
            session.setAttribute(ERROR_KEY, "Mã hoặc giá trị không hợp lệ.");

        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, promotionErrorMessage(e));

        } catch (SQLException e) {
            getServletContext().log("Could not save promotion.", e);

            if ("PROMOTION_NOT_FOUND".equals(e.getMessage())) {
                session.setAttribute(
                        ERROR_KEY,
                        "Không tìm thấy chương trình khuyến mãi."
                );
            } else if ("PROMOTION_QUANTITY_CHANGED".equals(e.getMessage())) {
                session.setAttribute(ERROR_KEY, "Số lượt coupon đã thay đổi hoặc mã không còn tồn tại. Vui lòng tải lại và kiểm tra số lượng.");
            } else if (e.getErrorCode() == 547) {
                session.setAttribute(ERROR_KEY, "Khuyến mãi đang được dữ liệu khác sử dụng nên chưa thể xóa. Bạn có thể tắt khuyến mãi để ngừng hiển thị.");
            } else {
                session.setAttribute(
                        ERROR_KEY,
                        "Không thể lưu chương trình khuyến mãi."
                );
            }
        }

        response.sendRedirect(
                request.getContextPath() + "/admin/promotions"
        );
    }

    private Promotion readPromotion(HttpServletRequest request) {
        Promotion promotion = new Promotion();
        String quantity = request.getParameter("quantity");
        if (quantity == null || quantity.isBlank()) throw new IllegalArgumentException("Hãy nhập số lượt coupon.");
        promotion.setQuantity(Integer.parseInt(quantity));
        String original = request.getParameter("originalQuantity");
        promotion.setOriginalQuantity(original == null || original.isBlank() ? 0 : Integer.parseInt(original));

        promotion.setPromotionCode(
                request.getParameter("promotionCode")
        );
        promotion.setPromotionName(
                request.getParameter("promotionName")
        );
        promotion.setDiscountType(
                request.getParameter("discountType")
        );
        String discountValue = request.getParameter("discountValue");
        if (discountValue == null || discountValue.isBlank()) throw new IllegalArgumentException("Hãy nhập giá trị giảm.");
        promotion.setDiscountValue(new BigDecimal(discountValue));
        promotion.setStartDate(parseTimestamp(
                request.getParameter("startDate")
        ));
        promotion.setEndDate(parseTimestamp(
                request.getParameter("endDate")
        ));
        promotion.setMinOrderValue(parseOptionalDecimal(
                request.getParameter("minOrderValue")
        ));
        promotion.setMaxDiscount(parseOptionalDecimal(
                request.getParameter("maxDiscount")
        ));

        String status = request.getParameter("status");
        promotion.setStatus(
                status == null || status.trim().isEmpty()
                        ? "Active"
                        : status
        );

        return promotion;
    }

    private Timestamp parseTimestamp(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Hãy nhập thời gian bắt đầu và kết thúc."
            );
        }

        try {
            return Timestamp.valueOf(LocalDateTime.parse(value));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Thời gian khuyến mãi không hợp lệ.");
        }
    }

    private BigDecimal parseOptionalDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return new BigDecimal(value.trim());
    }

    private Staff requireAdmin(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        Staff staff = getStaff(request.getSession(false));

        if (staff == null) {
            response.sendRedirect(
                    request.getContextPath() + "/login.jsp"
            );
            return null;
        }

        if (!"Active".equalsIgnoreCase(staff.getStatus())
                || !"Admin".equalsIgnoreCase(staff.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        return staff;
    }

    private Staff getStaff(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute("staff");

        if (!(value instanceof Staff)) {
            value = session.getAttribute("STAFF");
        }

        return value instanceof Staff ? (Staff) value : null;
    }

    private String takeFlash(HttpSession session, String key) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(key);
        session.removeAttribute(key);

        return value == null ? null : value.toString();
    }

    private String promotionErrorMessage(IllegalArgumentException e) {
        if ("PROMOTION_CODE_EXISTS".equals(e.getMessage())) {
            return "Mã khuyến mãi đã tồn tại.";
        }

        return e.getMessage();
    }
}
