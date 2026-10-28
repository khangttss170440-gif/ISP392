package util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import model.Customer;
import model.Staff;

/** Shared session lifecycle for customer registration and account login. */
public final class AuthSession {
    public static final int TIMEOUT_SECONDS = AppConfig.integer("SESSION_TIMEOUT_SECONDS", 60, 86400);

    private AuthSession() { }

    public static void loginCustomer(HttpServletRequest request, Customer customer) {
        customer.setPasswordHash(null);
        newSession(request).setAttribute("customer", customer);
    }

    public static void loginStaff(HttpServletRequest request, Staff staff) {
        staff.setPasswordHash(null);
        newSession(request).setAttribute("staff", staff);
    }

    private static HttpSession newSession(HttpServletRequest request) {
        logout(request);
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(TIMEOUT_SECONDS);
        return session;
    }

    public static void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    public static String home(HttpSession session) {
        if (session == null) return null;
        if (session.getAttribute("customer") instanceof Customer) return "/index.jsp";
        Object account = session.getAttribute("staff");
        if (!(account instanceof Staff)) account = session.getAttribute("STAFF");
        if (!(account instanceof Staff)) return null;
        return staffHome(((Staff) account).getRole());
    }

    public static String staffHome(String role) {
        if ("Admin".equalsIgnoreCase(role)) return "/admin/products";
        if ("Sales".equalsIgnoreCase(role)) return "/staff/sales/orders";
        if ("Warehouse".equalsIgnoreCase(role)) return "/staff/warehouse/orders";
        return null;
    }
}
