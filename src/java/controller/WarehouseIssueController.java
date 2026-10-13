package controller;

import dao.WarehouseIssueDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Staff;
import model.WarehouseIssue;

@WebServlet("/staff/warehouse/inventory-issues")
public class WarehouseIssueController extends HttpServlet {

    private WarehouseIssueDAO issueDAO;

    @Override
    public void init() {
        issueDAO = new WarehouseIssueDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect(
                request.getContextPath() + "/staff/warehouse/orders"
        );
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        Staff staff = getStaff(request.getSession(false));

        if (staff == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        if (staff.getRole() == null
                || !"Warehouse".equalsIgnoreCase(staff.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        HttpSession session = request.getSession(false);

        Integer orderID = positiveInteger(request.getParameter("orderId"));
        Integer variantID = positiveInteger(request.getParameter("variantId"));
        Integer quantity = positiveInteger(request.getParameter("quantity"));
        String issueType = value(request.getParameter("issueType"));
        String description = value(request.getParameter("description"));

        if (orderID == null || variantID == null || quantity == null) {
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    "Thông tin đơn hàng, SKU hoặc số lượng không hợp lệ.");
            redirectToOrders(request, response);
            return;
        }

        if (!"Missing".equals(issueType)
                && !"Damaged".equals(issueType)
                && !"Other".equals(issueType)) {
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    "Loại sự cố không hợp lệ.");
            redirectToOrders(request, response);
            return;
        }

        if (description.isEmpty() || description.length() > 1000) {
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    "Mô tả sự cố là bắt buộc và tối đa 1000 ký tự.");
            redirectToOrders(request, response);
            return;
        }

        WarehouseIssue issue = new WarehouseIssue();
        issue.setOrderID(orderID);
        issue.setVariantID(variantID);
        issue.setQuantity(quantity);
        issue.setIssueType(issueType);
        issue.setDescription(description);

        try {
            boolean created = issueDAO.createIssue(issue, staff.getStaffID());

            if (created) {
                session.setAttribute("WAREHOUSE_ISSUE_MESSAGE",
                        "Đã gửi báo cáo sự cố cho Sales.");
            } else {
                session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                        "Không tìm thấy SKU trong đơn đang xử lý "
                        + "hoặc số lượng sự cố vượt số lượng đặt.");
            }
        } catch (SQLException e) {
            getServletContext().log("Could not report warehouse issue.", e);
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    "Không thể lưu báo cáo sự cố. Vui lòng thử lại.");
        }

        redirectToOrders(request, response);
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

    private Integer positiveInteger(String value) {
        try {
            int number = Integer.parseInt(value);
            return number > 0 ? number : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private void redirectToOrders(HttpServletRequest request,
                                  HttpServletResponse response)
            throws IOException {
        response.sendRedirect(
                request.getContextPath() + "/staff/warehouse/orders"
        );
    }
}