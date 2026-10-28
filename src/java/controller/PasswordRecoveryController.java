package controller;

import dao.CustomerAccountDAO;
import dao.CustomerDAO;
import java.io.IOException;
import java.security.GeneralSecurityException;
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

@WebServlet(urlPatterns = {"/password/forgot", "/password/change"})
public class PasswordRecoveryController extends HttpServlet {
    private static final String PREFIX = "PASSWORD_RECOVERY_";
    private static final long CODE_LIFETIME_MILLIS = util.AppConfig.integer("AUTH_CODE_LIFETIME_SECONDS", 1, 86400) * 1000L;
    private static final long RESEND_WAIT_MILLIS = util.AppConfig.integer("AUTH_CODE_RESEND_SECONDS", 1, 86400) * 1000L;
    private static final int MAX_CODE_ATTEMPTS = util.AppConfig.integer("AUTH_CODE_MAX_ATTEMPTS", 1, 20);

    private CustomerDAO customerDAO;
    private CustomerAccountDAO accountDAO;

    @Override
    public void init() {
        customerDAO = new CustomerDAO();
        accountDAO = new CustomerAccountDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        if (isChangeFlow(request) && currentCustomer(request) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        show(request, response, "start", null, null);
    }

    @Override
    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        boolean changeFlow = isChangeFlow(request);
        HttpSession session = request.getSession(true);
        if (changeFlow && currentCustomer(request) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String stage = request.getParameter("stage");
        if ("verify".equals(stage)) {
            finish(request, response, session, changeFlow);
        } else {
            sendCode(request, response, session, changeFlow);
        }
    }

    private void sendCode(HttpServletRequest request,
            HttpServletResponse response, HttpSession session,
            boolean changeFlow) throws ServletException, IOException {
        clearState(session);
        String email;
        Customer customer;

        try {
            if (changeFlow) {
                Customer signedIn = currentCustomer(request);
                email = signedIn.getEmail();
                customer = customerDAO.findActiveByEmail(email);
                String currentPassword = request.getParameter("currentPassword");
                if (customer == null || currentPassword == null
                        || !PasswordUtil.verifyPassword(
                                currentPassword, customer.getPasswordHash())) {
                    show(request, response, "start",
                            "Mật khẩu hiện tại chưa chính xác.", null);
                    return;
                }
            } else {
                email = value(request.getParameter("email"))
                        .toLowerCase(Locale.ROOT);
                if (!email.matches(
                        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    show(request, response, "start",
                            "Vui lòng nhập địa chỉ email hợp lệ.", null);
                    return;
                }
                customer = customerDAO.findActiveByEmail(email);
            }

            if (customer != null) {
                Long sentAt = number(session.getAttribute(key("sentAt")));
                long now = System.currentTimeMillis();
                if (sentAt != null && now - sentAt < RESEND_WAIT_MILLIS) {
                    long seconds = (RESEND_WAIT_MILLIS - (now - sentAt) + 999) / 1000;
                    show(request, response, "start",
                            "Vui lòng đợi " + seconds + " giây trước khi yêu cầu mã mới.", null);
                    return;
                }
                String code = RegistrationCode.generate();
                if (changeFlow) {
                    GmailSmtpService.sendPasswordChangeCode(
                            email, customer.getFullName(), code);
                } else {
                    GmailSmtpService.sendPasswordResetCode(
                            email, customer.getFullName(), code);
                }
                session.setAttribute(key("id"), customer.getCustomerID());
                session.setAttribute(key("email"), customer.getEmail());
                session.setAttribute(key("hash"), RegistrationCode.hash(code));
                session.setAttribute(key("expires"),
                        now + CODE_LIFETIME_MILLIS);
                session.setAttribute(key("attempts"), 0);
                session.setAttribute(key("sentAt"), now);
                if (changeFlow) {
                    session.setAttribute(key("oldHash"),
                            customer.getPasswordHash());
                }
            }

            if (changeFlow) {
                show(request, response, "verify", null,
                        "Đã gửi mã xác nhận đến email " + maskEmail(email) + ".");
            } else {
                // Keep the response the same whether or not the account exists.
                show(request, response, "verify", null,
                        "Nếu email thuộc một tài khoản đang hoạt động, mã xác nhận đã được gửi. "
                        + "Kiểm tra hộp thư đến và thư rác.");
            }
        } catch (IllegalStateException e) {
            getServletContext().log("Gmail SMTP is not configured.", e);
            show(request, response, "start",
                    "Website chưa cấu hình Gmail gửi mã. Vui lòng kiểm tra cấu hình quản trị.", null);
        } catch (IOException e) {
            getServletContext().log("Could not send password verification email.", e);
            show(request, response, "start",
                    "Không gửi được email xác nhận. Vui lòng thử lại sau.", null);
        } catch (SQLException | GeneralSecurityException e) {
            throw new ServletException("Could not start password verification.", e);
        }
    }

    private void finish(HttpServletRequest request,
            HttpServletResponse response, HttpSession session,
            boolean changeFlow) throws ServletException, IOException {
        if (session == null || !(session.getAttribute(key("id")) instanceof Number)) {
            show(request, response, "start",
                    "Phiên xác nhận không còn hiệu lực. Vui lòng yêu cầu mã mới.", null);
            return;
        }

        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");
        if (newPassword == null || newPassword.length() < 8
                || newPassword.length() > 128) {
            show(request, response, "verify",
                    "Mật khẩu mới phải có từ 8 đến 128 ký tự.", null);
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            show(request, response, "verify",
                    "Mật khẩu nhập lại không khớp.", null);
            return;
        }

        String code = value(request.getParameter("code"));
        if (!code.matches("[0-9]{6}")) {
            show(request, response, "verify", "Vui lòng nhập mã gồm 6 chữ số.", null);
            return;
        }

        Long expires = number(session.getAttribute(key("expires")));
        if (expires == null || System.currentTimeMillis() > expires) {
            clearState(session);
            show(request, response, "start",
                    "Mã đã hết hạn. Vui lòng yêu cầu gửi mã mới.", null);
            return;
        }
        int attempts = intValue(session.getAttribute(key("attempts")));
        if (attempts >= MAX_CODE_ATTEMPTS) {
            clearState(session);
            show(request, response, "start",
                    "Bạn đã nhập sai mã quá số lần cho phép. Vui lòng yêu cầu mã mới.", null);
            return;
        }

        try {
            String expectedHash = (String) session.getAttribute(key("hash"));
            if (!RegistrationCode.matches(code, expectedHash)) {
                attempts++;
                session.setAttribute(key("attempts"), attempts);
                if (attempts >= MAX_CODE_ATTEMPTS) {
                    clearState(session);
                    show(request, response, "start",
                            "Bạn đã nhập sai mã quá số lần cho phép. Vui lòng yêu cầu mã mới.", null);
                } else {
                    show(request, response, "verify",
                            "Mã xác nhận không đúng. Bạn còn "
                            + (MAX_CODE_ATTEMPTS - attempts) + " lần thử.", null);
                }
                return;
            }

            int customerID = ((Number) session.getAttribute(key("id"))).intValue();
            String newHash = PasswordUtil.hashPassword(newPassword);
            boolean updated;
            if (changeFlow) {
                String oldHash = (String) session.getAttribute(key("oldHash"));
                updated = oldHash != null && accountDAO.changePassword(
                        customerID, oldHash, newHash);
            } else {
                updated = accountDAO.resetPassword(customerID, newHash);
            }
            clearState(session);
            if (!updated) {
                show(request, response, "start",
                        "Không thể cập nhật mật khẩu. Tài khoản có thể đã thay đổi; vui lòng thử lại.", null);
                return;
            }

            if (changeFlow) {
                show(request, response, "done", null,
                        "Mật khẩu đã được đổi thành công.");
            } else {
                AuthSession.logout(request);
                response.sendRedirect(request.getContextPath()
                        + "/login?passwordReset=1");
            }
        } catch (SQLException | GeneralSecurityException e) {
            throw new ServletException("Could not update customer password.", e);
        }
    }

    private void show(HttpServletRequest request, HttpServletResponse response,
            String stage, String error, String message)
            throws ServletException, IOException {
        request.setAttribute("passwordFlow",
                isChangeFlow(request) ? "change" : "forgot");
        request.setAttribute("stage", stage);
        request.setAttribute("error", error);
        request.setAttribute("message", message);
        request.getRequestDispatcher("/customer/password.jsp")
                .forward(request, response);
    }

    private Customer currentCustomer(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("customer");
        return value instanceof Customer ? (Customer) value : null;
    }

    private boolean isChangeFlow(HttpServletRequest request) {
        return request.getServletPath().endsWith("/change");
    }

    private String key(String name) {
        return PREFIX + name.toUpperCase(Locale.ROOT);
    }

    private void clearState(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(key("id"));
        session.removeAttribute(key("email"));
        session.removeAttribute(key("hash"));
        session.removeAttribute(key("expires"));
        session.removeAttribute(key("attempts"));
        session.removeAttribute(key("oldHash"));
    }

    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 0) return "email của bạn";
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }

    private Long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
