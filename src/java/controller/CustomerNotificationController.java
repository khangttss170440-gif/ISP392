package controller;

import dao.CustomerNotificationDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Customer;
import model.CustomerNotification;

@WebServlet("/customer/notifications/read")
public class CustomerNotificationController extends HttpServlet {
    private final CustomerNotificationDAO notificationDAO = new CustomerNotificationDAO();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Object account = session == null ? null : session.getAttribute("customer");
        if (!(account instanceof Customer)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Customer customer = (Customer) account;
        try {
            int notificationID = Integer.parseInt(request.getParameter("notificationId"));
            notificationDAO.markRead(notificationID, customer.getCustomerID());
            Object current = session.getAttribute("LATEST_CUSTOMER_NOTIFICATION");
            if (current instanceof CustomerNotification
                    && ((CustomerNotification) current).getNotificationID() == notificationID) {
                session.removeAttribute("LATEST_CUSTOMER_NOTIFICATION");
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        } catch (SQLException e) {
            throw new ServletException("Could not mark customer notification as read.", e);
        }
        response.sendRedirect(request.getContextPath() + "/index.jsp");
    }
}
