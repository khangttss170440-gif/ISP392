package controller;

import dao.WarehouseIssueDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Staff;

@WebServlet("/staff/sales/inventory-issues")
public class SalesWarehouseIssueController extends HttpServlet {

    private static final String CSRF_KEY = "warehouseIssue.csrf";
    private WarehouseIssueDAO issueDAO;

    @Override
    public void init() {
        issueDAO = new WarehouseIssueDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        Staff staff = getStaff(request.getSession(false));

        if (staff == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        if (!isSalesOrAdmin(staff)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session.getAttribute(CSRF_KEY) == null) {
            session.setAttribute(CSRF_KEY, java.util.UUID.randomUUID().toString());
        }
        request.setAttribute("csrfToken", session.getAttribute(CSRF_KEY));
        request.setAttribute(
                "message",
                removeSessionValue(session, "WAREHOUSE_ISSUE_MESSAGE")
        );
        request.setAttribute(
                "error",
                removeSessionValue(session, "WAREHOUSE_ISSUE_ERROR")
        );

        try {
            request.setAttribute("issues", issueDAO.getOpenIssues());
        } catch (SQLException e) {
            getServletContext().log("Could not load warehouse issues.", e);
            request.setAttribute("issues", Collections.emptyList());
            request.setAttribute("error",
                    "Không thể tải danh sách sự cố kho.");
        }

        request.getRequestDispatcher(
                "/staff/sales/inventory-issues.jsp"
        ).forward(request, response);
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

        if (!isSalesOrAdmin(staff)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        HttpSession session = request.getSession(false);
        Object csrfToken = session == null ? null : session.getAttribute(CSRF_KEY);
        if (!(csrfToken instanceof String)
                || !csrfToken.equals(request.getParameter("csrfToken"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Integer issueID = positiveInteger(request.getParameter("issueId"));
        String action = value(request.getParameter("action"));
        String note = value(request.getParameter("resolutionNote"));

        if (issueID == null || note.isEmpty() || note.length() > 1000) {
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    "Hãy nhập ghi chú xử lý hoặc nội dung gửi khách (tối đa 1000 ký tự).");
            redirectToIssues(request, response);
            return;
        }

        try {
            boolean updated;
            String successMessage;

            if ("resolve".equals(action)) {
                updated = issueDAO.resolveIssue(
                        issueID, staff.getStaffID(), "Resolved", note
                );
                successMessage = "Đã cập nhật trạng thái sự cố.";
            } else if ("reject".equals(action)) {
                updated = issueDAO.resolveIssue(
                        issueID, staff.getStaffID(), "Rejected", note
                );
                successMessage = "Đã từ chối báo cáo sự cố.";
            } else if ("cancel_order".equals(action)) {
                updated = issueDAO.cancelOrderForIssue(
                        issueID, staff.getStaffID(), note
                );
                successMessage =
                        "Đã hủy đơn, cập nhật lại tồn kho và gửi thông báo "
                        + "cho khách trong mục Hỗ trợ.";
            } else if ("simulate_refund".equals(action)) {
                updated = issueDAO.simulateRefundForIssue(
                        issueID, staff.getStaffID(), note
                );
                successMessage = "Đã mô phỏng hoàn tiền VNPay Sandbox. Hãy bấm hủy đơn để hoàn tồn kho và báo khách.";
            } else {
                session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                        "Thao tác xử lý không hợp lệ.");
                redirectToIssues(request, response);
                return;
            }

            session.setAttribute(
                    updated ? "WAREHOUSE_ISSUE_MESSAGE" : "WAREHOUSE_ISSUE_ERROR",
                    updated ? successMessage
                            : "Sự cố không tồn tại hoặc đã được xử lý."
            );
        } catch (SQLException e) {
            getServletContext().log("Could not resolve warehouse issue.", e);
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    messageFor(e));
        }

        redirectToIssues(request, response);
    }

    private boolean isSalesOrAdmin(Staff staff) {
        String role = staff.getRole();
        return "Active".equalsIgnoreCase(staff.getStatus())
                && role != null
                && ("Sales".equalsIgnoreCase(role)
                || "Admin".equalsIgnoreCase(role));
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

    private String messageFor(SQLException e) {
        String code = e.getMessage();
        if ("PAID_ORDER_REQUIRES_REFUND".equals(code)) {
            return "Đơn đã thanh toán. Hãy hoàn tiền cho khách trước khi hủy đơn.";
        }
        if ("ORDER_ALREADY_SHIPPED".equals(code)) {
            return "Đơn đã xuất giao hoặc giao thành công nên không thể hủy tại đây.";
        }
        if ("ORDER_NOT_CANCELLABLE".equals(code)) {
            return "Trạng thái đơn hiện tại không cho phép hủy.";
        }
        if ("REFUND_SIMULATION_VNPAY_ONLY".equals(code)) {
            return "Chỉ mô phỏng hoàn tiền cho đơn đã thanh toán qua VNPay Sandbox.";
        }
        if ("NO_PAID_VNPAY_PAYMENT".equals(code)) {
            return "Không tìm thấy khoản VNPay đang ở trạng thái đã thanh toán để mô phỏng hoàn tiền.";
        }
        if ("PAID_PAYMENT_REMAINS".equals(code)) {
            return "Đơn còn khoản thanh toán khác đang Paid; chưa ghi nhận mô phỏng hoàn tiền.";
        }
        return "Không thể cập nhật sự cố. Vui lòng thử lại.";
    }

    private Object removeSessionValue(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        session.removeAttribute(key);
        return value;
    }

    private void redirectToIssues(HttpServletRequest request,
                                  HttpServletResponse response)
            throws IOException {
        response.sendRedirect(
                request.getContextPath() + "/staff/sales/inventory-issues"
        );
    }
}
