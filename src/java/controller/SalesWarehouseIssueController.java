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
        Integer issueID = positiveInteger(request.getParameter("issueId"));
        String action = value(request.getParameter("action"));
        String note = value(request.getParameter("resolutionNote"));

        if (issueID == null || note.length() > 1000) {
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    "Dữ liệu xử lý sự cố không hợp lệ.");
            redirectToIssues(request, response);
            return;
        }

        String status;

        if ("resolve".equals(action)) {
            status = "Resolved";
        } else if ("reject".equals(action)) {
            status = "Rejected";
        } else {
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    "Thao tác xử lý không hợp lệ.");
            redirectToIssues(request, response);
            return;
        }

        try {
            boolean updated = issueDAO.resolveIssue(
                    issueID,
                    staff.getStaffID(),
                    status,
                    note
            );

            session.setAttribute(
                    updated
                            ? "WAREHOUSE_ISSUE_MESSAGE"
                            : "WAREHOUSE_ISSUE_ERROR",
                    updated
                            ? "Đã cập nhật trạng thái sự cố."
                            : "Sự cố không tồn tại hoặc đã được xử lý."
            );
        } catch (SQLException e) {
            getServletContext().log("Could not resolve warehouse issue.", e);
            session.setAttribute("WAREHOUSE_ISSUE_ERROR",
                    "Không thể cập nhật sự cố. Vui lòng thử lại.");
        }

        redirectToIssues(request, response);
    }

    private boolean isSalesOrAdmin(Staff staff) {
        String role = staff.getRole();
        return role != null
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