package controller;

import dao.CustomerDAO;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Date;
import java.sql.SQLException;
import java.util.Locale;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.Customer;
import util.PasswordUtil;
import util.AuthSession;

@WebServlet("/register")
public class RegisterController extends HttpServlet {

    private CustomerDAO customerDAO;

    @Override
    public void init() {
        customerDAO = new CustomerDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.getRequestDispatcher("/register.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        String fullName = value(request.getParameter("fullName"));
        String email = value(request.getParameter("email"))
                .toLowerCase(Locale.ROOT);
        String phone = value(request.getParameter("phone"));
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        String gender = value(request.getParameter("gender"));
        String dateText = value(request.getParameter("dateOfBirth"));

        if (fullName.isEmpty() || fullName.length() > 150) {
            forwardError(request, response, "Enter a valid full name.");
            return;
        }

        if (email.length() > 150
                || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            forwardError(request, response, "Enter a valid email address.");
            return;
        }

        if (password == null || password.length() < 8) {
            forwardError(request, response,
                    "Password must contain at least 8 characters.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            forwardError(request, response,
                    "Password confirmation does not match.");
            return;
        }

        if (phone.length() > 20
                || (!phone.isEmpty()
                && !phone.matches("^[0-9+(). -]{8,20}$"))) {
            forwardError(request, response, "Enter a valid phone number.");
            return;
        }

        if (gender.length() > 20
                || (!gender.isEmpty()
                && !gender.equals("Male")
                && !gender.equals("Female")
                && !gender.equals("Other"))) {
            forwardError(request, response, "Select a valid gender.");
            return;
        }

        Date dateOfBirth = null;

        if (!dateText.isEmpty()) {
            try {
                dateOfBirth = Date.valueOf(dateText);
            } catch (IllegalArgumentException e) {
                forwardError(request, response,
                        "Enter a valid date of birth.");
                return;
            }
        }

        try {
            if (customerDAO.emailExists(email)) {
                forwardError(request, response,
                        "This email is already registered.");
                return;
            }

            Customer customer = new Customer();
            customer.setFullName(fullName);
            customer.setEmail(email);
            customer.setPhone(phone.isEmpty() ? null : phone);
            customer.setPasswordHash(PasswordUtil.hashPassword(password));
            customer.setDateOfBirth(dateOfBirth);
            customer.setGender(gender.isEmpty() ? null : gender);
            int customerID = customerDAO.register(customer);

            if (customerID <= 0) {
                forwardError(request, response,
                        "Registration failed. Please try again.");
                return;
            }

            customer.setCustomerID(customerID);
            customer.setStatus("Active");

            AuthSession.loginCustomer(request, customer);

            response.sendRedirect(
                    request.getContextPath() + "/index.jsp"
            );

        } catch (SQLException | GeneralSecurityException e) {
            throw new ServletException("Customer registration failed.", e);
        }
    }

    private void forwardError(
            HttpServletRequest request,
            HttpServletResponse response,
            String message)
            throws ServletException, IOException {

        request.setAttribute("error", message);
        request.getRequestDispatcher("/register.jsp")
                .forward(request, response);
    }

    private String value(String text) {
        return text == null ? "" : text.trim();
    }
}
