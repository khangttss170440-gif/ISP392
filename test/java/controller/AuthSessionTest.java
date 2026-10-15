package controller;

import dao.CustomerDAO;
import dao.StaffDAO;
import filter.AuthSessionFilter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Customer;
import model.Staff;
import util.AuthSession;
import util.PasswordUtil;

/** Standalone regression checks; uses servlet doubles and never writes to the database. */
public class AuthSessionTest {
    public static void main(String[] args) throws Exception {
        String hash = PasswordUtil.hashPassword("password123");
        LoginController login = new LoginController();
        inject(login, "customerDAO", new CustomerDAO() {
            @Override public Customer findActiveByEmail(String email) {
                Customer customer = new Customer();
                customer.setCustomerID(7);
                customer.setFullName("Test Customer");
                customer.setPasswordHash(hash);
                return customer;
            }
        });
        inject(login, "staffDAO", new StaffDAO() {
            @Override public Staff findActiveByEmail(String email) {
                Staff staff = new Staff();
                staff.setRole("Admin");
                staff.setPasswordHash(hash);
                return staff;
            }
        });

        Exchange customer = credentials();
        customer.session.values.put("staff", new Staff());
        SessionState old = customer.session;
        login.doPost(customer.request(), customer.response());
        check(old.invalidated, "Login invalidates the previous session");
        check(customer.session != old, "Login creates a new session identity");
        check(customer.session.timeout == 1800, "Login sets a 30-minute idle timeout");
        check(customer.session.values.get("staff") == null, "Old identity is cleared");
        check(((Customer) customer.session.values.get("customer")).getPasswordHash() == null,
                "Customer password hash is not stored in session");
        check("/store/index.jsp".equals(customer.redirect), "Customer landing page");

        Exchange invalid = credentials();
        invalid.parameters.put("password", "wrong-password");
        login.doPost(invalid.request(), invalid.response());
        check(invalid.session.values.isEmpty() && !invalid.session.invalidated,
                "Failed login does not authenticate or rotate session");
        check("/login.jsp".equals(invalid.forward), "Failed login renders form");

        Exchange staff = credentials();
        staff.parameters.put("accountType", "STAFF");
        login.doPost(staff.request(), staff.response());
        check("/store/admin/products".equals(staff.redirect), "Staff uses role landing page");
        check(((Staff) staff.session.values.get("staff")).getPasswordHash() == null,
                "Staff password hash is not stored in session");

        RegisterController register = new RegisterController();
        inject(register, "customerDAO", new CustomerDAO() {
            @Override public boolean emailExists(String email) { return false; }
            @Override public int register(Customer account) { return 42; }
        });
        Exchange registration = credentials();
        registration.parameters.put("fullName", "New Customer");
        registration.parameters.put("confirmPassword", "password123");
        register.doPost(registration.request(), registration.response());
        Customer created = (Customer) registration.session.values.get("customer");
        check(created != null && created.getCustomerID() == 42, "Registration logs in persisted customer");
        check(created.getPasswordHash() == null && registration.session.timeout == 1800,
                "Registration uses the same safe session policy");
        check("/store/index.jsp".equals(registration.redirect), "Registration landing page");

        AuthSessionFilter filter = new AuthSessionFilter();
        customer.path = "/login.jsp";
        customer.redirect = null;
        filter.doFilter(customer.request(), customer.response(), blockedChain());
        check("/store/index.jsp".equals(customer.redirect), "Authenticated users skip direct login JSP");
        customer.path = "/register";
        filter.doFilter(customer.request(), customer.response(), blockedChain());
        check("/store/index.jsp".equals(customer.redirect), "Authenticated users skip registration");
        customer.path = "/customer/account.jsp";
        boolean[] passed = {false};
        filter.doFilter(customer.request(), customer.response(), (req, res) -> passed[0] = true);
        check(passed[0], "Customer session survives subsequent page requests");
        check("no-store".equals(customer.headers.get("Cache-Control")), "Account pages disable caching");

        Exchange anonymous = new Exchange();
        anonymous.session = null;
        anonymous.path = "/customer/account.jsp";
        filter.doFilter(anonymous.request(), anonymous.response(), blockedChain());
        check("/store/login".equals(anonymous.redirect), "Missing/expired session returns to login");
        check(anonymous.session == null, "Auth checks do not create anonymous sessions");
        staff.path = "/customer/account.jsp";
        filter.doFilter(staff.request(), staff.response(), blockedChain());
        check("/store/login".equals(staff.redirect), "Staff is not treated as customer");

        SessionState loggedIn = customer.session;
        new LogoutController().doPost(customer.request(), customer.response());
        check(loggedIn.invalidated && loggedIn.values.isEmpty(), "Logout clears all session state");
        check("/store/index.jsp".equals(customer.redirect), "Logout landing page");
        check(AuthSession.home(null) == null, "No identity after expiration");
        System.out.println("PASS: registration, customer/staff login, failed login, new session identity, timeout, credential removal, redirects, protected page access and logout.");
    }

    private static FilterChain blockedChain() {
        return (request, response) -> { throw new AssertionError("Request should have redirected"); };
    }

    private static Exchange credentials() {
        Exchange exchange = new Exchange();
        exchange.parameters.put("email", "test@example.com");
        exchange.parameters.put("password", "password123");
        return exchange;
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static class SessionState {
        final Map<String, Object> values = new HashMap<>();
        boolean invalidated;
        int timeout;
        final HttpSession proxy = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class}, (object, method, args) -> {
                    switch (method.getName()) {
                        case "invalidate": invalidated = true; values.clear(); return null;
                        case "getAttribute": return values.get(args[0]);
                        case "setAttribute": values.put((String) args[0], args[1]); return null;
                        case "setMaxInactiveInterval": timeout = (Integer) args[0]; return null;
                        default: return null;
                    }
                });
    }

    private static class Exchange {
        SessionState session = new SessionState();
        final Map<String, String> parameters = new HashMap<>();
        final Map<String, String> headers = new HashMap<>();
        String path = "/login", redirect, forward;

        HttpServletRequest request() {
            return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class}, (object, method, args) -> {
                        switch (method.getName()) {
                            case "getParameter": return parameters.get(args[0]);
                            case "getContextPath": return "/store";
                            case "getServletPath": return path;
                            case "getSession":
                                boolean create = args == null || args.length == 0 || Boolean.TRUE.equals(args[0]);
                                if (session != null && session.invalidated) session = null;
                                if (session == null && create) session = new SessionState();
                                return session == null ? null : session.proxy;
                            case "getRequestDispatcher":
                                String destination = (String) args[0];
                                return Proxy.newProxyInstance(RequestDispatcher.class.getClassLoader(),
                                        new Class<?>[]{RequestDispatcher.class}, (dispatcher, action, params) -> {
                                            forward = destination; return null;
                                        });
                            default: return null;
                        }
                    });
        }

        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class}, (object, method, args) -> {
                        if ("sendRedirect".equals(method.getName())) redirect = (String) args[0];
                        if ("setHeader".equals(method.getName())) headers.put((String) args[0], (String) args[1]);
                        return null;
                    });
        }
    }
}
