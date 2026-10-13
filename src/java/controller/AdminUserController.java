package controller;

import dao.AdminUserDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.security.GeneralSecurityException;
import java.util.Locale;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Staff;
import java.util.Collections;
import util.PasswordUtil;

@WebServlet("/admin/users")
public class AdminUserController extends HttpServlet {

    private static final String PAGE = "/admin/users.jsp";
    private static final String MESSAGE_KEY = "ADMIN_USER_MESSAGE";
    private static final String ERROR_KEY = "ADMIN_USER_ERROR";

    private AdminUserDAO userDAO;

    @Override
    public void init() {
        userDAO = new AdminUserDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if (requireAdmin(request, response) == null) {
            return;
        }

        HttpSession session = request.getSession(false);
        request.setAttribute("message", takeFlash(session, MESSAGE_KEY));
        request.setAttribute("error", takeFlash(session, ERROR_KEY));

        String keyword = request.getParameter("keyword");
        String accountType = request.getParameter("accountType");

        try {
            request.setAttribute(
                    "accounts",
                    userDAO.getAccounts(keyword, accountType)
            );
        } catch (IllegalArgumentException e) {
            request.setAttribute("accounts", Collections.emptyList());
            request.setAttribute("error", "Bộ lọc tài khoản không hợp lệ.");
        } catch (SQLException e) {
            getServletContext().log("Could not load user accounts.", e);
            request.setAttribute("error", "Không tải được danh sách tài khoản.");
        }

        request.setAttribute("keyword", keyword == null ? "" : keyword);
        request.setAttribute(
                "accountType",
                accountType == null ? "ALL" : accountType
        );

        request.getRequestDispatcher(PAGE).forward(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        Staff admin = requireAdmin(request, response);

        if (admin == null) {
            return;
        }

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);

        try {
            if ("createStaff".equals(request.getParameter("action"))) {
                createStaff(request);
                session.setAttribute(MESSAGE_KEY, "Đã tạo tài khoản nhân viên.");
                response.sendRedirect(request.getContextPath() + "/admin/users");
                return;
            }

            int accountID = Integer.parseInt(
                    request.getParameter("accountId")
            );

            String accountType = request.getParameter("accountType");
            String status = request.getParameter("status");

            if ("Customer".equalsIgnoreCase(accountType)) {
                userDAO.updateCustomerStatus(accountID, status);

            } else if ("Staff".equalsIgnoreCase(accountType)) {
                userDAO.updateStaffAccount(
                        accountID,
                        admin.getStaffID(),
                        request.getParameter("role"),
                        status
                );

            } else {
                throw new IllegalArgumentException(
                        "Loại tài khoản không hợp lệ."
                );
            }

            session.setAttribute(
                    MESSAGE_KEY,
                    "Đã cập nhật tài khoản."
            );

        } catch (NumberFormatException e) {
            session.setAttribute(ERROR_KEY, "Mã tài khoản không hợp lệ.");

        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());

        } catch (GeneralSecurityException e) {
            getServletContext().log("Could not hash the new staff password.", e);
            session.setAttribute(ERROR_KEY, "Không thể tạo mật khẩu cho tài khoản.");

        } catch (SQLException e) {
            getServletContext().log("Could not update account.", e);
            session.setAttribute(ERROR_KEY, messageFor(e));
        }

        response.sendRedirect(
                request.getContextPath() + "/admin/users"
        );
    }

    private void createStaff(HttpServletRequest request)
            throws SQLException, GeneralSecurityException {
        String fullName = value(request.getParameter("fullName"));
        String email = value(request.getParameter("email"))
                .toLowerCase(Locale.ROOT);
        String phone = value(request.getParameter("phone"));
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        String role = value(request.getParameter("role"));

        if (fullName.isEmpty() || fullName.length() > 150) {
            throw new IllegalArgumentException("Vui lòng nhập họ tên hợp lệ.");
        }
        if (email.length() > 150
                || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("Vui lòng nhập email hợp lệ.");
        }
        if (!phone.isEmpty()
                && !phone.matches("^[0-9+(). -]{8,20}$")) {
            throw new IllegalArgumentException("Số điện thoại không hợp lệ.");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 8 ký tự.");
        }
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }
        if (!"Sales".equalsIgnoreCase(role)
                && !"Warehouse".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException("Chọn vai trò Sales hoặc Warehouse.");
        }

        userDAO.createStaff(
                fullName,
                email,
                phone,
                PasswordUtil.hashPassword(password),
                role
        );
    }

    private Staff requireAdmin(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        Staff staff = getStaff(request.getSession(false));

        if (staff == null) {
            response.sendRedirect(
                    request.getContextPath() + "/login.jsp"
            );
            return null;
        }

        if (!"Active".equalsIgnoreCase(staff.getStatus())
                || !"Admin".equalsIgnoreCase(staff.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        return staff;
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

    private String takeFlash(HttpSession session, String key) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(key);
        session.removeAttribute(key);

        return value == null ? null : value.toString();
    }

    private String messageFor(SQLException e) {
        String code = e.getMessage();

        if ("ACCOUNT_NOT_FOUND".equals(code)) {
            return "Không tìm thấy tài khoản.";
        }
        if ("CANNOT_EDIT_OWN_ADMIN_ACCOUNT".equals(code)) {
            return "Không thể tự đổi quyền hoặc trạng thái tài khoản Admin đang đăng nhập.";
        }
        if ("LAST_ACTIVE_ADMIN".equals(code)) {
            return "Không thể vô hiệu hóa hoặc hạ quyền Admin cuối cùng.";
        }
        if ("EMAIL_ALREADY_EXISTS".equals(code)) {
            return "Email này đã được dùng cho một tài khoản khác.";
        }

        return "Không thể tạo hoặc cập nhật tài khoản.";
    }

    private String value(String text) {
        return text == null ? "" : text.trim();
    }
}
