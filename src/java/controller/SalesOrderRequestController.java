package controller;

import dao.SalesOrderRequestDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Staff;

@WebServlet("/staff/sales/order-requests")
public class SalesOrderRequestController extends HttpServlet {

    private static final String MESSAGE_KEY = "SALES_REQUEST_MESSAGE";
    private static final String ERROR_KEY = "SALES_REQUEST_ERROR";

    private SalesOrderRequestDAO requestDAO;

    @Override
    public void init() {
        requestDAO = new SalesOrderRequestDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if (requireSalesStaff(request, response) == null) {
            return;
        }

        HttpSession session = request.getSession(false);
        request.setAttribute("message", takeFlash(session, MESSAGE_KEY));
        request.setAttribute("error", takeFlash(session, ERROR_KEY));

        try {
            request.setAttribute(
                    "orderRequests",
                    requestDAO.getPendingRequests()
            );
        } catch (SQLException e) {
            getServletContext().log("Could not load order requests.", e);
            request.setAttribute(
                    "error",
                    "Không tải được danh sách yêu cầu."
            );
        }

        // Dùng lại trang Sales hiện có; frontend có thể tách trang sau.
        request.getRequestDispatcher("/staff/sales/orders.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        Staff staff = requireSalesStaff(request, response);

        if (staff == null) {
            return;
        }

        HttpSession session = request.getSession(false);

        try {
            int requestID = Integer.parseInt(
                    request.getParameter("requestId")
            );

            requestDAO.processRequest(
                    requestID,
                    staff.getStaffID(),
                    request.getParameter("action"),
                    request.getParameter("staffResponse")
            );

            session.setAttribute(
                    MESSAGE_KEY,
                    "Đã cập nhật yêu cầu của khách hàng."
            );

        } catch (NumberFormatException e) {
            session.setAttribute(ERROR_KEY, "Mã yêu cầu không hợp lệ.");

        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());

        } catch (SQLException e) {
            getServletContext().log("Could not process order request.", e);
            session.setAttribute(ERROR_KEY, messageFor(e));
        }

        response.sendRedirect(
                request.getContextPath() + "/staff/sales/order-requests"
        );
    }

    private Staff requireSalesStaff(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        HttpSession session = request.getSession(false);
        Staff staff = getStaff(session);

        if (staff == null) {
            response.sendRedirect(
                    request.getContextPath() + "/login.jsp"
            );
            return null;
        }

        if (!"Active".equalsIgnoreCase(staff.getStatus())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        String role = staff.getRole();

        if (!"Sales".equalsIgnoreCase(role)
                && !"Admin".equalsIgnoreCase(role)) {
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

    private String messageFor(SQLException e) {
        String code = e.getMessage();

        if ("REQUEST_NOT_FOUND".equals(code)) {
            return "Không tìm thấy yêu cầu.";
        }
        if ("REQUEST_NOT_PENDING".equals(code)) {
            return "Yêu cầu này đã được xử lý.";
        }
        if ("ORDER_NOT_CANCELLABLE".equals(code)) {
            return "Đơn hàng không còn đủ điều kiện để hủy.";
        }
        if ("PAID_ORDER_REQUIRES_REFUND".equals(code)) {
            return "Đơn đã thanh toán; cần xử lý hoàn tiền trước khi duyệt hủy.";
        }
        if ("RETURN_ORDER_NOT_DELIVERED".equals(code)) {
            return "Chỉ duyệt trả/đổi với đơn đã giao.";
        }

        return "Không thể xử lý yêu cầu.";
    }
}