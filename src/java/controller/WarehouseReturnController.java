package controller;

import dao.WarehouseReturnDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Staff;

@WebServlet("/staff/warehouse/returns")
public class WarehouseReturnController extends HttpServlet {

    private static final String MESSAGE_KEY = "WAREHOUSE_RETURN_MESSAGE";
    private static final String ERROR_KEY = "WAREHOUSE_RETURN_ERROR";

    private WarehouseReturnDAO returnDAO;

    @Override
    public void init() {
        returnDAO = new WarehouseReturnDAO();
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

        loadPage(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        Staff staff = requireWarehouseStaff(request, response);

        if (staff == null) {
            return;
        }

        HttpSession session = request.getSession(false);

        try {
            int requestID = Integer.parseInt(
                    request.getParameter("requestId")
            );

            Map<Integer, Integer> received =
                    readQuantities(request, "receivedQuantity");

            Map<Integer, Integer> accepted =
                    readQuantities(request, "acceptedQuantity");

            returnDAO.receiveReturn(
                    requestID,
                    staff.getStaffID(),
                    received,
                    accepted,
                    request.getParameter("warehouseNote")
            );

            session.setAttribute(
                    MESSAGE_KEY,
                    "Đã tiếp nhận hàng trả và cập nhật tồn kho."
            );

            response.sendRedirect(
                    request.getContextPath() + "/staff/warehouse/returns"
            );

        } catch (NumberFormatException e) {
            session.setAttribute(ERROR_KEY, "Mã yêu cầu không hợp lệ.");
            response.sendRedirect(
                    request.getContextPath() + "/staff/warehouse/returns"
            );

        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, messageFor(e.getMessage()));
            response.sendRedirect(
                    request.getContextPath() + "/staff/warehouse/returns"
            );

        } catch (SQLException e) {
            getServletContext().log("Could not receive returned goods.", e);
            session.setAttribute(ERROR_KEY, messageFor(e.getMessage()));
            response.sendRedirect(
                    request.getContextPath() + "/staff/warehouse/returns"
            );
        }
    }

    private void loadPage(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        try {
            request.setAttribute(
                    "approvedReturns",
                    returnDAO.getApprovedReturns()
            );
        } catch (SQLException e) {
            getServletContext().log("Could not load approved returns.", e);
            request.setAttribute(
                    "error",
                    "Không tải được danh sách hàng trả."
            );
        }

        request.getRequestDispatcher("/staff/warehouse/returns.jsp")
                .forward(request, response);
    }

    private Map<Integer, Integer> readQuantities(
            HttpServletRequest request,
            String parameterName) {

        String[] orderDetailIDs =
                request.getParameterValues("orderDetailId");
        String[] quantityValues =
                request.getParameterValues(parameterName);

        if (orderDetailIDs == null || quantityValues == null
                || orderDetailIDs.length != quantityValues.length) {
            throw new IllegalArgumentException("RETURN_LINES_MISMATCH");
        }

        if (orderDetailIDs.length > 20) {
            throw new IllegalArgumentException("TOO_MANY_RETURN_LINES");
        }

        Map<Integer, Integer> quantities = new LinkedHashMap<>();

        for (int i = 0; i < orderDetailIDs.length; i++) {
            int orderDetailID = Integer.parseInt(orderDetailIDs[i]);
            int quantity = Integer.parseInt(quantityValues[i]);

            if (quantities.put(orderDetailID, quantity) != null) {
                throw new IllegalArgumentException(
                        "DUPLICATE_RETURN_LINE"
                );
            }
        }

        return quantities;
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

    private String messageFor(String code) {
        if ("REQUEST_NOT_FOUND".equals(code)) {
            return "Không tìm thấy yêu cầu trả hàng.";
        }
        if ("REQUEST_IS_NOT_RETURN".equals(code)) {
            return "Yêu cầu này không phải yêu cầu trả hàng.";
        }
        if ("RETURN_NOT_APPROVED".equals(code)) {
            return "Yêu cầu chưa được Sales duyệt hoặc đã được tiếp nhận.";
        }
        if ("RETURN_HAS_NO_LINES".equals(code)) {
            return "Yêu cầu không có sản phẩm để tiếp nhận.";
        }
        if ("RETURN_LINES_MISMATCH".equals(code)
                || "DUPLICATE_RETURN_LINE".equals(code)) {
            return "Danh sách sản phẩm nhận về không hợp lệ.";
        }
        if ("INVALID_RETURN_QUANTITY".equals(code)) {
            return "Số lượng nhận/chấp nhận không hợp lệ.";
        }

        return "Không thể tiếp nhận hàng trả.";
    }
}