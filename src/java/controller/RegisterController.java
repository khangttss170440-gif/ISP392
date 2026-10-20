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
import javax.servlet.http.HttpSession;
import model.Customer;
import util.AuthSession;
import util.GmailSmtpService;
import util.PasswordUtil;
import util.RegistrationCode;

@WebServlet("/register")
public class RegisterController extends HttpServlet {
    static final String PENDING_CUSTOMER = "PENDING_EMAIL_VERIFICATION_CUSTOMER";
    static final String CODE_HASH = "PENDING_EMAIL_VERIFICATION_HASH";
    static final String CODE_EXPIRES = "PENDING_EMAIL_VERIFICATION_EXPIRES";
    static final String CODE_SENT_AT = "PENDING_EMAIL_VERIFICATION_SENT_AT";
    static final String CODE_ATTEMPTS = "PENDING_EMAIL_VERIFICATION_ATTEMPTS";
    static final long CODE_LIFETIME_MILLIS = 10 * 60 * 1000L;
    static final long RESEND_WAIT_MILLIS = 60 * 1000L;
    static final int MAX_CODE_ATTEMPTS = 5;

    private CustomerDAO customerDAO;

    @Override
    public void init() {
        customerDAO = new CustomerDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        if ("1".equals(request.getParameter("verification"))) {
            request.setAttribute("error",
                    "Phiên xác thực đã kết thúc. Vui lòng đăng ký lại.");
        } else if ("1".equals(request.getParameter("duplicate"))) {
            request.setAttribute("error",
                    "Email này vừa được dùng để tạo tài khoản khác. Vui lòng đăng nhập.");
        }
        request.getRequestDispatcher("/register.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
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
            forwardError(request, response, "Vui lòng nhập họ tên hợp lệ.");
            return;
        }
        if (email.length() > 150
                || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            forwardError(request, response, "Vui lòng nhập địa chỉ Gmail/email hợp lệ.");
            return;
        }
        if (password == null || password.length() < 8) {
            forwardError(request, response, "Mật khẩu phải có ít nhất 8 ký tự.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            forwardError(request, response, "Mật khẩu nhập lại không khớp.");
            return;
        }
        if (phone.length() > 20
                || (!phone.isEmpty() && !phone.matches("^[0-9+(). -]{8,20}$"))) {
            forwardError(request, response, "Vui lòng nhập số điện thoại hợp lệ.");
            return;
        }
        if (gender.length() > 20
                || (!gender.isEmpty() && !gender.equals("Male")
                && !gender.equals("Female") && !gender.equals("Other"))) {
            forwardError(request, response, "Vui lòng chọn giới tính hợp lệ.");
            return;
        }

        Date dateOfBirth = null;
        if (!dateText.isEmpty()) {
            try {
                dateOfBirth = Date.valueOf(dateText);
            } catch (IllegalArgumentException e) {
                forwardError(request, response, "Vui lòng nhập ngày sinh hợp lệ.");
                return;
            }
        }

        try {
            if (customerDAO.emailExists(email)) {
                forwardError(request, response, "Email này đã được đăng ký.");
                return;
            }

            Customer customer = new Customer();
            customer.setFullName(fullName);
            customer.setEmail(email);
            customer.setPhone(phone.isEmpty() ? null : phone);
            customer.setPasswordHash(PasswordUtil.hashPassword(password));
            customer.setDateOfBirth(dateOfBirth);
            customer.setGender(gender.isEmpty() ? null : gender);

            String code = RegistrationCode.generate();
            GmailSmtpService.sendRegistrationCode(email, fullName, code);

            HttpSession session = request.getSession(true);
            clearPendingVerification(session);
            session.setAttribute(PENDING_CUSTOMER, customer);
            session.setAttribute(CODE_HASH, RegistrationCode.hash(code));
            session.setAttribute(CODE_EXPIRES,
                    System.currentTimeMillis() + CODE_LIFETIME_MILLIS);
            session.setAttribute(CODE_SENT_AT, System.currentTimeMillis());
            session.setAttribute(CODE_ATTEMPTS, 0);
            response.sendRedirect(request.getContextPath() + "/register/verify");
        } catch (IllegalStateException e) {
            getServletContext().log("Gmail SMTP is not configured.", e);
            forwardError(request, response,
                    "Website chưa cấu hình Gmail gửi mã. Vui lòng liên hệ quản trị viên.");
        } catch (IOException e) {
            getServletContext().log("Could not send registration verification email.", e);
            forwardError(request, response,
                    "Không gửi được email xác thực. Kiểm tra cấu hình Gmail rồi thử lại.");
        } catch (SQLException | GeneralSecurityException e) {
            throw new ServletException("Customer registration verification failed.", e);
        }
    }

    static void clearPendingVerification(HttpSession session) {
        session.removeAttribute(PENDING_CUSTOMER);
        session.removeAttribute(CODE_HASH);
        session.removeAttribute(CODE_EXPIRES);
        session.removeAttribute(CODE_SENT_AT);
        session.removeAttribute(CODE_ATTEMPTS);
    }

    private void forwardError(HttpServletRequest request,
            HttpServletResponse response, String message)
            throws ServletException, IOException {
        request.setAttribute("error", message);
        request.getRequestDispatcher("/register.jsp").forward(request, response);
    }

    private String value(String text) {
        return text == null ? "" : text.trim();
    }
}
