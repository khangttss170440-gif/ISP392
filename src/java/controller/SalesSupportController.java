package controller;

import dao.SupportDAO;
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
import model.SupportTicket;

@WebServlet("/staff/sales/support")
public class SalesSupportController extends HttpServlet {

    private SupportDAO supportDAO;

    @Override
    public void init() {
        supportDAO = new SupportDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        Staff staff = getStaff(session);

        if (staff == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        if (!isSalesOrAdmin(staff)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        request.setAttribute("message",
                removeFlash(session, "SALES_SUPPORT_MESSAGE"));
        request.setAttribute("error",
                removeFlash(session, "SALES_SUPPORT_ERROR"));

        try {
            request.setAttribute("tickets", supportDAO.getSalesTickets());

            Integer ticketID = positiveInteger(
                    request.getParameter("ticketId")
            );

            if (request.getParameter("ticketId") != null
                    && ticketID == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            if (ticketID != null) {
                SupportTicket ticket =
                        supportDAO.findSalesTicket(ticketID);

                if (ticket == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                request.setAttribute("selectedTicket", ticket);
                request.setAttribute(
                        "messages",
                        supportDAO.getMessages(ticketID)
                );
            } else {
                request.setAttribute("messages", Collections.emptyList());
            }

        } catch (SQLException e) {
            getServletContext().log("Could not load sales support tickets.", e);
            request.setAttribute("tickets", Collections.emptyList());
            request.setAttribute("messages", Collections.emptyList());
            request.setAttribute("error",
                    "Không thể tải danh sách yêu cầu hỗ trợ.");
        }

        request.getRequestDispatcher("/staff/sales/support.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Staff staff = getStaff(session);

        if (staff == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        if (!isSalesOrAdmin(staff)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Integer ticketID = positiveInteger(
                request.getParameter("ticketId")
        );
        String action = value(request.getParameter("action"));
        String message = value(request.getParameter("message"));

        if (ticketID == null || message.isEmpty() || message.length() > 2000) {
            session.setAttribute("SALES_SUPPORT_ERROR",
                    "Ticket hoặc nội dung phản hồi không hợp lệ.");
            redirectToSupport(request, response);
            return;
        }

        String newStatus;

        if ("reply".equals(action)) {
            newStatus = "InProgress";
        } else if ("resolve".equals(action)) {
            newStatus = "Resolved";
        } else {
            session.setAttribute("SALES_SUPPORT_ERROR",
                    "Thao tác xử lý ticket không hợp lệ.");
            redirectToSupport(request, response);
            return;
        }

        try {
            boolean updated = supportDAO.salesReply(
                    ticketID,
                    staff.getStaffID(),
                    message,
                    newStatus
            );

            session.setAttribute(
                    updated ? "SALES_SUPPORT_MESSAGE" : "SALES_SUPPORT_ERROR",
                    updated
                            ? "Đã cập nhật ticket hỗ trợ."
                            : "Ticket không tồn tại hoặc đã đóng."
            );

            response.sendRedirect(
                    request.getContextPath()
                    + "/staff/sales/support?ticketId=" + ticketID
            );

        } catch (SQLException e) {
            getServletContext().log("Could not update support ticket.", e);
            session.setAttribute("SALES_SUPPORT_ERROR",
                    "Không thể cập nhật ticket. Vui lòng thử lại.");
            redirectToSupport(request, response);
        }
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

    private Object removeFlash(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        session.removeAttribute(key);
        return value;
    }

    private void redirectToSupport(HttpServletRequest request,
                                   HttpServletResponse response)
            throws IOException {
        response.sendRedirect(
                request.getContextPath() + "/staff/sales/support"
        );
    }
}