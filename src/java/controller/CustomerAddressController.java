package controller;

import dao.CustomerAddressDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Customer;

@WebServlet("/customer/addresses")
public class CustomerAddressController extends HttpServlet {

    private static final String MESSAGE_KEY = "ADDRESS_MESSAGE";
    private static final String ERROR_KEY = "ADDRESS_ERROR";

    private CustomerAddressDAO addressDAO;

    @Override
    public void init() {
        addressDAO = new CustomerAddressDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        Customer customer = getCustomer(request.getSession(false));

        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        HttpSession session = request.getSession(false);

        request.setAttribute("message", removeSessionValue(session, MESSAGE_KEY));
        request.setAttribute("error", removeSessionValue(session, ERROR_KEY));

        try {
            request.setAttribute(
                    "addresses",
                    addressDAO.findByCustomer(customer.getCustomerID())
            );
        } catch (SQLException e) {
            getServletContext().log("Could not load customer addresses.", e);
            request.setAttribute("error",
                    "Không thể tải sổ địa chỉ. Vui lòng thử lại.");
        }

        request.getRequestDispatcher("/customer/addresses.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        Customer customer = getCustomer(request.getSession(false));

        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        HttpSession session = request.getSession(false);
        String action = value(request.getParameter("action"));
        Integer addressID = positiveInteger(
                request.getParameter("addressId")
        );

        if (addressID == null) {
            session.setAttribute(ERROR_KEY, "Địa chỉ không hợp lệ.");
            redirectToList(request, response);
            return;
        }

        try {
            if ("default".equals(action)) {
                boolean updated = addressDAO.setDefault(
                        addressID,
                        customer.getCustomerID()
                );

                session.setAttribute(
                        updated ? MESSAGE_KEY : ERROR_KEY,
                        updated
                                ? "Đã cập nhật địa chỉ mặc định."
                                : "Không tìm thấy địa chỉ."
                );

            } else if ("delete".equals(action)) {
                boolean deleted = addressDAO.delete(
                        addressID,
                        customer.getCustomerID()
                );

                session.setAttribute(
                        deleted ? MESSAGE_KEY : ERROR_KEY,
                        deleted
                                ? "Đã xóa địa chỉ."
                                : "Không tìm thấy địa chỉ."
                );

            } else {
                session.setAttribute(ERROR_KEY, "Thao tác không hợp lệ.");
            }

        } catch (SQLException e) {
            getServletContext().log("Could not update customer address.", e);
            session.setAttribute(ERROR_KEY,
                    "Không thể cập nhật địa chỉ. Vui lòng thử lại.");
        }

        redirectToList(request, response);
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
            int id = Integer.parseInt(value);
            return id > 0 ? id : null;
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

    private void redirectToList(HttpServletRequest request,
                                HttpServletResponse response)
            throws IOException {
        response.sendRedirect(
                request.getContextPath() + "/customer/addresses"
        );
    }
}