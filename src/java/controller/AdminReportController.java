package controller;

import dao.AdminReportDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.AdminReport;
import model.Staff;

@WebServlet("/admin/reports")
public class AdminReportController extends HttpServlet {

    private AdminReportDAO reportDAO;

    @Override
    public void init() {
        reportDAO = new AdminReportDAO();
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

        if (staff.getRole() == null
                || !"Admin".equalsIgnoreCase(staff.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate defaultFrom = today.withDayOfMonth(1);

        LocalDate from = parseDate(request.getParameter("from"), defaultFrom);
        LocalDate to = parseDate(request.getParameter("to"), today);

        if (from.isAfter(to)) {
            request.setAttribute("error",
                    "Ngày bắt đầu không được sau ngày kết thúc.");
            from = defaultFrom;
            to = today;
        }

        request.setAttribute("from", from);
        request.setAttribute("to", to);

        try {
            request.setAttribute("report",
                    reportDAO.getSummary(from, to));
            request.setAttribute("dailySales",
                    reportDAO.getDailySales(from, to));
        } catch (SQLException e) {
            getServletContext().log("Could not load admin reports.", e);

            request.setAttribute("error",
                    "Không thể tải báo cáo. Vui lòng kiểm tra kết nối database.");
            request.setAttribute("report", new AdminReport());
            request.setAttribute("dailySales", Collections.emptyList());
        }

        request.getRequestDispatcher("/admin/reports.jsp")
                .forward(request, response);
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

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}