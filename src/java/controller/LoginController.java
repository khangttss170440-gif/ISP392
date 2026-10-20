package controller;

import dao.CustomerDAO;
import dao.CustomerNotificationDAO;
import dao.StaffDAO;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Locale;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.Customer;
import model.CustomerNotification;
import model.Staff;
import util.PasswordUtil;
import util.AuthSession;

@WebServlet("/login")
public class LoginController extends HttpServlet {

    private CustomerDAO customerDAO;
    private StaffDAO staffDAO;

    @Override
    public void init() {
        customerDAO = new CustomerDAO();
        staffDAO = new StaffDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if ("1".equals(request.getParameter("registered"))) {
            request.setAttribute("message",
                    "Đăng ký thành công. Bạn có thể đăng nhập ngay.");
        } else if ("1".equals(request.getParameter("passwordReset"))) {
            request.setAttribute("message",
                    "Mật khẩu đã được đặt lại. Vui lòng đăng nhập bằng mật khẩu mới.");
        }

        request.getRequestDispatcher("/login.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String accountType = request.getParameter("accountType");

        if (accountType == null || accountType.trim().isEmpty()) {
            accountType = "CUSTOMER";
        }

        accountType = accountType.trim().toUpperCase(Locale.ROOT);

        if (email == null || password == null
                || email.trim().isEmpty() || password.isEmpty()) {
            showError(request, response,
                    "Vui lòng nhập email và mật khẩu.", accountType);
            return;
        }

        if (!"CUSTOMER".equals(accountType)
                && !"STAFF".equals(accountType)) {
            showError(request, response,
                    "Vui lòng chọn loại tài khoản hợp lệ.", "CUSTOMER");
            return;
        }

        email = email.trim().toLowerCase(Locale.ROOT);

        try {
            if ("STAFF".equals(accountType)) {
                loginStaff(request, response, email, password);
            } else {
                loginCustomer(request, response, email, password);
            }

        } catch (SQLException | GeneralSecurityException e) {
            throw new ServletException("Account login failed.", e);
        }
    }

    private void loginCustomer(HttpServletRequest request,
                               HttpServletResponse response,
                               String email,
                               String password)
            throws SQLException, GeneralSecurityException,
            ServletException, IOException {

        Customer customer = customerDAO.findActiveByEmail(email);

        if (customer == null
                || !PasswordUtil.verifyPassword(
                        password, customer.getPasswordHash())) {
            showError(request, response,
                    "Email hoặc mật khẩu không chính xác.", "CUSTOMER");
            return;
        }

        AuthSession.loginCustomer(request, customer);
        try {
            CustomerNotification latest = new CustomerNotificationDAO()
                    .getLatestUnread(customer.getCustomerID());
            if (latest != null) {
                request.getSession(false).setAttribute(
                        "LATEST_CUSTOMER_NOTIFICATION", latest);
            }
        } catch (SQLException e) {
            getServletContext().log(
                    "Could not load customer notifications after login.", e);
        }
        response.sendRedirect(request.getContextPath() + "/index.jsp");
    }

    private void loginStaff(HttpServletRequest request,
                            HttpServletResponse response,
                            String email,
                            String password)
            throws SQLException, GeneralSecurityException,
            ServletException, IOException {

        Staff staff = staffDAO.findActiveByEmail(email);

        if (staff == null
                || !PasswordUtil.verifyPassword(
                        password, staff.getPasswordHash())) {
            showError(request, response,
                    "Email hoặc mật khẩu không chính xác.", "STAFF");
            return;
        }

        String destination = AuthSession.staffHome(staff.getRole());
        if (destination == null) {
            showError(request, response,
                    "Tài khoản chưa được cấp quyền đăng nhập.", "STAFF");
            return;
        }

        AuthSession.loginStaff(request, staff);
        response.sendRedirect(request.getContextPath() + destination);
    }

    private void showError(
            HttpServletRequest request,
            HttpServletResponse response,
            String message,
            String accountType)
            throws ServletException, IOException {

        request.setAttribute("error", message);
        request.setAttribute("accountType", accountType);
        request.getRequestDispatcher("/login.jsp")
                .forward(request, response);
    }
}
