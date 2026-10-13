package controller;

import dao.SalesOrderDAO;
import dao.WarehouseOrderDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Staff;
import model.SalesOrder;
import model.OrderLine;

@WebServlet(urlPatterns = {
    "/staff/sales/orders",
    "/staff/sales/order-detail"
})
public class SalesOrderController extends HttpServlet {

    private static final String PAGE = "/staff/sales/orders.jsp";
    private static final String MESSAGE_KEY = "SALES_ORDER_MESSAGE";
    private static final String ERROR_KEY = "SALES_ORDER_ERROR";

    private SalesOrderDAO salesOrderDAO;

    @Override
    public void init() {
        salesOrderDAO = new SalesOrderDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        Staff staff = requireSalesStaff(request, response);

        if (staff == null) {
            return;
        }

        HttpSession session = request.getSession(false);

        request.setAttribute("message", takeFlash(session, MESSAGE_KEY));
        request.setAttribute("error", takeFlash(session, ERROR_KEY));

        if (request.getServletPath().endsWith("/order-detail")) {
            showOrderDetail(request, response);
            return;
        }

        try {
            request.setAttribute(
                    "orders",
                    salesOrderDAO.getPendingOrders()
            );
        } catch (SQLException e) {
            getServletContext().log("Could not load pending sales orders.", e);
            request.setAttribute(
                    "error",
                    "Không tải được danh sách đơn hàng."
            );
        }

        request.getRequestDispatcher(PAGE)
                .forward(request, response);
    }

    private void showOrderDetail(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        int orderID;
        try {
            orderID = Integer.parseInt(request.getParameter("orderId"));
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            List<SalesOrder> pendingOrders = salesOrderDAO.getPendingOrders();
            SalesOrder selected = null;
            for (SalesOrder order : pendingOrders) {
                if (order.getOrderID() == orderID) {
                    selected = order;
                    break;
                }
            }

            if (selected == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            request.setAttribute("order", selected);
            request.setAttribute(
                    "orderLines",
                    new WarehouseOrderDAO().getOrderLines(orderID)
            );
            request.getRequestDispatcher(
                    "/staff/sales/order-detail.jsp"
            ).forward(request, response);
        } catch (SQLException e) {
            getServletContext().log("Could not load sales order detail.", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        Staff staff = requireSalesStaff(request, response);

        if (staff == null) {
            return;
        }

        HttpSession session = request.getSession(false);

        try {
            int orderID = Integer.parseInt(
                    request.getParameter("orderId")
            );

            salesOrderDAO.processPendingOrder(
                    orderID,
                    staff.getStaffID(),
                    request.getParameter("action"),
                    request.getParameter("reason")
            );

            String action = request.getParameter("action");

            if ("confirm".equalsIgnoreCase(action)) {
                session.setAttribute(
                        MESSAGE_KEY,
                        "Đã xác nhận đơn hàng."
                );
            } else {
                session.setAttribute(
                        MESSAGE_KEY,
                        "Đã hủy đơn hàng và hoàn số lượng vào kho."
                );
            }

        } catch (NumberFormatException e) {
            session.setAttribute(ERROR_KEY, "Mã đơn hàng không hợp lệ.");

        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());

        } catch (SQLException e) {
            getServletContext().log("Could not process sales order.", e);
            session.setAttribute(ERROR_KEY, messageFor(e));
        }

        response.sendRedirect(
                request.getContextPath() + "/staff/sales/orders"
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

        if ("ORDER_NOT_FOUND".equals(code)) {
            return "Không tìm thấy đơn hàng.";
        }

        if ("ORDER_NOT_PENDING".equals(code)) {
            return "Đơn hàng không còn ở trạng thái chờ xử lý.";
        }

        if ("PAID_ORDER_CANNOT_BE_CANCELLED".equals(code)) {
            return "Đơn đã thanh toán; cần xử lý hoàn tiền trước khi hủy.";
        }

        return "Không thể xử lý đơn hàng. Vui lòng kiểm tra lại dữ liệu.";
    }
}
