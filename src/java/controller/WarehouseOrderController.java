package controller;

import dao.WarehouseOrderDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Staff;
import model.WarehouseOrder;

@WebServlet(urlPatterns = {
    "/staff/warehouse/orders",
    "/staff/warehouse/order-detail"
})
public class WarehouseOrderController extends HttpServlet {

    private static final String MESSAGE_KEY = "WAREHOUSE_MESSAGE";
    private static final String ERROR_KEY = "WAREHOUSE_ERROR";

    private WarehouseOrderDAO warehouseOrderDAO;

    @Override
    public void init() {
        warehouseOrderDAO = new WarehouseOrderDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if (requireWarehouseStaff(request, response) == null) {
            return;
        }

        HttpSession session = request.getSession(false);
        request.setAttribute("message", takeFlash(session, MESSAGE_KEY));
        request.setAttribute("error", takeFlash(session, ERROR_KEY));

        if (request.getServletPath().endsWith("/order-detail")) {
            showOrderDetail(request, response);
        } else {
            showOrders(request, response);
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if (requireWarehouseStaff(request, response) == null) {
            return;
        }

        HttpSession session = request.getSession(false);

        try {
            int orderID = Integer.parseInt(
                    request.getParameter("orderId")
            );

            warehouseOrderDAO.processShipment(
                    orderID,
                    request.getParameter("action"),
                    request.getParameter("carrier"),
                    request.getParameter("trackingNumber")
            );

            if ("ship".equalsIgnoreCase(request.getParameter("action"))) {
                session.setAttribute(
                        MESSAGE_KEY,
                        "Đã cập nhật đơn hàng sang trạng thái đang giao."
                );
            } else {
                session.setAttribute(
                        MESSAGE_KEY,
                        "Đã xác nhận giao hàng thành công."
                );
            }

        } catch (NumberFormatException e) {
            session.setAttribute(ERROR_KEY, "Mã đơn hàng không hợp lệ.");

        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());

        } catch (SQLException e) {
            getServletContext().log("Could not update warehouse order.", e);
            session.setAttribute(ERROR_KEY, messageFor(e));
        }

        response.sendRedirect(
                request.getContextPath() + "/staff/warehouse/orders"
        );
    }

    private void showOrders(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        try {
            request.setAttribute(
                    "orders",
                    warehouseOrderDAO.getWorkOrders()
            );
        } catch (SQLException e) {
            getServletContext().log("Could not load warehouse orders.", e);
            request.setAttribute(
                    "error",
                    "Không tải được danh sách đơn hàng của kho."
            );
        }

        request.getRequestDispatcher("/staff/warehouse/orders.jsp")
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
            WarehouseOrder order =
                    warehouseOrderDAO.getOrderForWarehouse(orderID);

            if (order == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            request.setAttribute("order", order);
            HttpSession session = request.getSession(false);
            if (session.getAttribute("ghn.csrf") == null) session.setAttribute("ghn.csrf", java.util.UUID.randomUUID().toString());
            request.setAttribute("ghnCsrf", session.getAttribute("ghn.csrf"));
            request.setAttribute("ghnDelivery", new dao.GhnDeliveryDAO().find(orderID));
            request.setAttribute("ghnConfigured", service.GhnClient.configured());
            request.setAttribute(
                    "orderLines",
                    warehouseOrderDAO.getOrderLines(orderID)
            );

        } catch (SQLException e) {
            getServletContext().log("Could not load warehouse order detail.", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        request.getRequestDispatcher(
                "/staff/warehouse/order-detail.jsp"
        ).forward(request, response);
    }

    private Staff requireWarehouseStaff(
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

        if (!"Warehouse".equalsIgnoreCase(role)
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

        if ("SHIPMENT_NOT_FOUND".equals(code)) {
            return "Đơn hàng chưa có thông tin giao hàng.";
        }

        if ("ORDER_NOT_READY_TO_SHIP".equals(code)) {
            return "Chỉ đơn đã được Sales xác nhận mới được xuất kho.";
        }

        if ("SHIPMENT_NOT_PENDING".equals(code)) {
            return "Đơn hàng không còn ở trạng thái chờ xuất kho.";
        }

        if ("ORDER_NOT_SHIPPED".equals(code)) {
            return "Chỉ đơn đang giao mới được xác nhận đã giao.";
        }

        return "Không thể cập nhật trạng thái giao hàng.";
    }
}
