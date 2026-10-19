package controller;

import dao.WarehouseIssueDAO;
import dao.WarehouseOrderDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.OrderLine;
import model.Staff;
import model.WarehouseIssue;
import model.WarehouseOrder;

@WebServlet("/staff/warehouse/inventory-issues")
public class WarehouseIssueController extends HttpServlet {

    private WarehouseIssueDAO issueDAO;
    private WarehouseOrderDAO orderDAO;

    @Override
    public void init() {
        issueDAO = new WarehouseIssueDAO();
        orderDAO = new WarehouseOrderDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {
        Staff staff = requireWarehouseStaff(request, response);
        if (staff == null) return;

        HttpSession session = request.getSession(false);
        request.setAttribute("message", takeFlash(session, "WAREHOUSE_ISSUE_MESSAGE"));
        request.setAttribute("error", takeFlash(session, "WAREHOUSE_ISSUE_ERROR"));

        try {
            List<WarehouseOrder> eligibleOrders =
                    orderDAO.getReportableOrders();
            request.setAttribute("eligibleOrders", eligibleOrders);

            String selectedOrderID = value(request.getParameter("orderId"));
            if (!selectedOrderID.isEmpty()) {
                Integer orderID = positiveInteger(selectedOrderID);
                if (orderID == null) {
                    request.setAttribute("error", "Mã đơn hàng không hợp lệ.");
                } else {
                    WarehouseOrder selectedOrder = null;
                    for (WarehouseOrder order : eligibleOrders) {
                        if (order.getOrderID() == orderID) {
                            selectedOrder = order;
                            break;
                        }
                    }

                    if (selectedOrder == null) {
                        request.setAttribute(
                                "error",
                                "Chỉ có thể báo sự cố cho đơn đã được xác nhận và đang xử lý."
                        );
                    } else {
                        List<OrderLine> lines =
                                orderDAO.getOrderLines(orderID);
                        request.setAttribute("selectedOrder", selectedOrder);
                        request.setAttribute("orderLines", lines);
                    }
                }
            }
        } catch (SQLException e) {
            getServletContext().log("Could not load orders for warehouse issue.", e);
            request.setAttribute("eligibleOrders", Collections.emptyList());
            request.setAttribute(
                    "error",
                    "Không thể tải đơn hàng để báo sự cố."
            );
        }

        try {
            request.setAttribute(
                    "issues",
                    issueDAO.getIssuesReportedBy(staff.getStaffID())
            );
        } catch (SQLException e) {
            getServletContext().log("Could not load reported warehouse issues.", e);
            request.setAttribute("issues", Collections.emptyList());
            if (request.getAttribute("error") == null) {
                request.setAttribute("error", "Không thể tải danh sách báo cáo sự cố.");
            }
        }

        request.getRequestDispatcher(
                "/staff/warehouse/inventory-issues.jsp"
        ).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        Staff staff = requireWarehouseStaff(request, response);
        if (staff == null) return;

        HttpSession session = request.getSession(false);

        Integer orderID = positiveInteger(request.getParameter("orderId"));
        Integer variantID = positiveInteger(request.getParameter("variantId"));
        Integer quantity = positiveInteger(request.getParameter("quantity"));
        String issueType = value(request.getParameter("issueType"));
        String description = value(request.getParameter("description"));

        if (orderID == null || variantID == null || quantity == null) {
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    "Thông tin đơn hàng, SKU hoặc số lượng không hợp lệ.");
            redirectToIssueList(request, response);
            return;
        }

        if (!"Missing".equals(issueType)
                && !"Damaged".equals(issueType)
                && !"Other".equals(issueType)) {
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    "Loại sự cố không hợp lệ.");
            redirectToIssueList(request, response);
            return;
        }

        if (description.isEmpty() || description.length() > 1000) {
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    "Mô tả sự cố là bắt buộc và tối đa 1000 ký tự.");
            redirectToIssueList(request, response);
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

        redirectToIssueList(request, response);
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

    private Staff requireWarehouseStaff(HttpServletRequest request,
                                        HttpServletResponse response)
            throws IOException {
        Staff staff = getStaff(request.getSession(false));

        if (staff == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return null;
        }

        if (!"Active".equalsIgnoreCase(staff.getStatus())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        String role = staff.getRole();
        if (role == null
                || (!"Warehouse".equalsIgnoreCase(role)
                && !"Admin".equalsIgnoreCase(role))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        return staff;
    }

    private Object takeFlash(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        session.removeAttribute(key);
        return value;
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

    private void redirectToIssueList(HttpServletRequest request,
                                     HttpServletResponse response)
            throws IOException {
        response.sendRedirect(
                request.getContextPath() + "/staff/warehouse/inventory-issues"
        );
    }
}
