package controller;

import dao.CustomerDAO;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Customer;
import util.AuthSession;
import util.GmailSmtpService;
import util.RegistrationCode;

@WebServlet("/register/verify")
public class RegistrationVerificationController extends HttpServlet {
    private CustomerDAO customerDAO;

    @Override
    public void init() {
        customerDAO = new CustomerDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Customer pending = getPendingCustomer(session);
        if (pending == null) {
            response.sendRedirect(request.getContextPath() + "/register?verification=1");
            return;
        }
        request.setAttribute("email", maskEmail(pending.getEmail()));
        request.getRequestDispatcher("/register-verify.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        Customer pending = getPendingCustomer(session);
        if (pending == null) {
            response.sendRedirect(request.getContextPath() + "/register?verification=1");
            return;
        }

        String action = request.getParameter("action");
        if ("resend".equals(action)) {
            resendCode(request, response, session, pending);
            return;
        }

        String code = request.getParameter("code");
        if (code == null || !code.trim().matches("[0-9]{6}")) {
            show(request, response, pending, "Vui lòng nhập mã gồm 6 chữ số.", null);
            return;
        }

        Long expiresAt = numberAttribute(session, RegisterController.CODE_EXPIRES);
        if (expiresAt == null || System.currentTimeMillis() > expiresAt) {
            show(request, response, pending,
                    "Mã đã hết hạn. Bạn có thể yêu cầu gửi mã mới.", null);
            return;
        }

        Integer attempts = (Integer) session.getAttribute(RegisterController.CODE_ATTEMPTS);
        int usedAttempts = attempts == null ? 0 : attempts;
        if (usedAttempts >= RegisterController.MAX_CODE_ATTEMPTS) {
            RegisterController.clearPendingVerification(session);
            response.sendRedirect(request.getContextPath() + "/register?verification=1");
            return;
        }

        try {
            String expectedHash = (String) session.getAttribute(RegisterController.CODE_HASH);
            if (!RegistrationCode.matches(code.trim(), expectedHash)) {
                usedAttempts++;
                session.setAttribute(RegisterController.CODE_ATTEMPTS, usedAttempts);
                if (usedAttempts >= RegisterController.MAX_CODE_ATTEMPTS) {
                    RegisterController.clearPendingVerification(session);
                    response.sendRedirect(request.getContextPath() + "/register?verification=1");
                    return;
                }
                show(request, response, pending,
                        "Mã xác thực chưa đúng. Bạn còn "
                        + (RegisterController.MAX_CODE_ATTEMPTS - usedAttempts)
                        + " lần thử.", null);
                return;
            }

            if (customerDAO.emailExists(pending.getEmail())) {
                RegisterController.clearPendingVerification(session);
                response.sendRedirect(request.getContextPath()
                        + "/register?duplicate=1");
                return;
            }
            int customerID = customerDAO.register(pending);
            if (customerID <= 0) {
                show(request, response, pending,
                        "Chưa tạo được tài khoản. Vui lòng thử xác thực lại.", null);
                return;
            }

            pending.setCustomerID(customerID);
            pending.setStatus("Active");
            RegisterController.clearPendingVerification(session);
            AuthSession.loginCustomer(request, pending);
            response.sendRedirect(request.getContextPath() + "/index.jsp");
        } catch (SQLException | GeneralSecurityException e) {
            throw new ServletException("Could not verify customer email.", e);
        }
    }

    private void resendCode(HttpServletRequest request, HttpServletResponse response,
            HttpSession session, Customer pending) throws ServletException, IOException {
        Long sentAt = numberAttribute(session, RegisterController.CODE_SENT_AT);
        long now = System.currentTimeMillis();
        if (sentAt != null && now - sentAt < RegisterController.RESEND_WAIT_MILLIS) {
            long seconds = (RegisterController.RESEND_WAIT_MILLIS - (now - sentAt) + 999) / 1000;
            show(request, response, pending,
                    "Vui lòng đợi " + seconds + " giây trước khi gửi lại mã.", null);
            return;
        }
        try {
            String code = RegistrationCode.generate();
            GmailSmtpService.sendRegistrationCode(
                    pending.getEmail(), pending.getFullName(), code);
            session.setAttribute(RegisterController.CODE_HASH, RegistrationCode.hash(code));
            session.setAttribute(RegisterController.CODE_EXPIRES,
                    now + RegisterController.CODE_LIFETIME_MILLIS);
            session.setAttribute(RegisterController.CODE_SENT_AT, now);
            session.setAttribute(RegisterController.CODE_ATTEMPTS, 0);
            show(request, response, pending, null,
                    "Đã gửi mã xác thực mới đến email của bạn.");
        } catch (IllegalStateException e) {
            getServletContext().log("Gmail SMTP is not configured.", e);
            show(request, response, pending,
                    "Website chưa cấu hình Gmail gửi mã.", null);
        } catch (IOException e) {
            getServletContext().log("Could not resend registration verification email.", e);
            show(request, response, pending,
                    "Không gửi lại được email. Vui lòng thử lại sau.", null);
        } catch (GeneralSecurityException e) {
            throw new ServletException("Could not create registration code.", e);
        }
    }

    private void show(HttpServletRequest request, HttpServletResponse response,
            Customer pending, String error, String message)
            throws ServletException, IOException {
        request.setAttribute("email", maskEmail(pending.getEmail()));
        request.setAttribute("error", error);
        request.setAttribute("message", message);
        request.getRequestDispatcher("/register-verify.jsp").forward(request, response);
    }

    private Customer getPendingCustomer(HttpSession session) {
        Object value = session == null ? null
                : session.getAttribute(RegisterController.PENDING_CUSTOMER);
        return value instanceof Customer ? (Customer) value : null;
    }

    private Long numberAttribute(HttpSession session, String name) {
        Object value = session.getAttribute(name);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at < 1) return email;
        String visible = email.substring(0, Math.min(2, at));
        return visible + "***" + email.substring(at);
    }
}
