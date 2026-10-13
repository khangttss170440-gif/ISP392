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
import model.Customer;
import model.SupportTicket;

@WebServlet("/customer/support")
public class CustomerSupportController extends HttpServlet {

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
        Customer customer = getCustomer(session);

        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        request.setAttribute("message",
                removeFlash(session, "SUPPORT_MESSAGE"));
        request.setAttribute("error",
                removeFlash(session, "SUPPORT_ERROR"));

        try {
            request.setAttribute(
                    "tickets",
                    supportDAO.getCustomerTickets(customer.getCustomerID())
            );

            Integer ticketID = positiveInteger(
                    request.getParameter("ticketId")
            );

            if (request.getParameter("ticketId") != null
                    && ticketID == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            if (ticketID != null) {
                SupportTicket ticket = supportDAO.findCustomerTicket(
                        ticketID,
                        customer.getCustomerID()
                );

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
            getServletContext().log("Could not load customer support.", e);
            request.setAttribute("tickets", Collections.emptyList());
            request.setAttribute("messages", Collections.emptyList());
            request.setAttribute("error",
                    "Không thể tải yêu cầu hỗ trợ.");
        }

        request.getRequestDispatcher("/customer/support.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Customer customer = getCustomer(session);

        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        String action = value(request.getParameter("action"));

        try {
            if ("create".equals(action)) {
                createTicket(request, response, session, customer);
            } else if ("reply".equals(action)) {
                replyToTicket(request, response, session, customer);
            } else {
                session.setAttribute("SUPPORT_ERROR",
                        "Thao tác hỗ trợ không hợp lệ.");
                redirectToSupport(request, response);
            }
        } catch (SQLException e) {
            getServletContext().log("Could not save customer support request.", e);
            session.setAttribute("SUPPORT_ERROR",
                    "Không thể lưu yêu cầu hỗ trợ. Vui lòng thử lại.");
            redirectToSupport(request, response);
        }
    }

    private void createTicket(HttpServletRequest request,
                              HttpServletResponse response,
                              HttpSession session,
                              Customer customer)
            throws SQLException, IOException {

        String subject = value(request.getParameter("subject"));
        String message = value(request.getParameter("message"));
        String orderText = value(request.getParameter("orderId"));
        Integer orderID = null;

        if (!orderText.isEmpty()) {
            orderID = positiveInteger(orderText);

            if (orderID == null) {
                session.setAttribute("SUPPORT_ERROR",
                        "Mã đơn hàng không hợp lệ.");
                redirectToSupport(request, response);
                return;
            }
        }

        if (subject.isEmpty() || subject.length() > 150
                || message.isEmpty() || message.length() > 2000) {
            session.setAttribute("SUPPORT_ERROR",
                    "Chủ đề là bắt buộc (tối đa 150 ký tự), "
                    + "nội dung tối đa 2000 ký tự.");
            redirectToSupport(request, response);
            return;
        }

        int ticketID = supportDAO.createTicket(
                customer.getCustomerID(),
                orderID,
                subject,
                message
        );

        if (ticketID <= 0) {
            session.setAttribute("SUPPORT_ERROR",
                    "Đơn hàng không tồn tại hoặc không thuộc tài khoản của bạn.");
            redirectToSupport(request, response);
            return;
        }

        session.setAttribute("SUPPORT_MESSAGE",
                "Đã gửi yêu cầu hỗ trợ.");
        response.sendRedirect(
                request.getContextPath()
                + "/customer/support?ticketId=" + ticketID
        );
    }

    private void replyToTicket(HttpServletRequest request,
                               HttpServletResponse response,
                               HttpSession session,
                               Customer customer)
            throws SQLException, IOException {

        Integer ticketID = positiveInteger(
                request.getParameter("ticketId")
        );
        String message = value(request.getParameter("message"));

        if (ticketID == null || message.isEmpty() || message.length() > 2000) {
            session.setAttribute("SUPPORT_ERROR",
                    "Mã ticket hoặc nội dung phản hồi không hợp lệ.");
            redirectToSupport(request, response);
            return;
        }

        boolean replied = supportDAO.customerReply(
                ticketID,
                customer.getCustomerID(),
                message
        );

        if (!replied) {
            session.setAttribute("SUPPORT_ERROR",
                    "Ticket không tồn tại hoặc đã đóng.");
            redirectToSupport(request, response);
            return;
        }

        session.setAttribute("SUPPORT_MESSAGE",
                "Đã gửi phản hồi.");
        response.sendRedirect(
                request.getContextPath()
                + "/customer/support?ticketId=" + ticketID
        );
    }

    private Customer getCustomer(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute("customer");
        return value instanceof Customer ? (Customer) value : null;
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
                request.getContextPath() + "/customer/support"
        );
    }
}