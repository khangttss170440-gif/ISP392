package filter;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Customer;
import util.AuthSession;

@WebFilter(urlPatterns = {"/login", "/login.jsp", "/register", "/register.jsp", "/customer/*", "/logout"})
public class AuthSessionFilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        String path = request.getServletPath();
        if (path.startsWith("/customer/")) {
            if (session == null || !(session.getAttribute("customer") instanceof Customer)) {
                response.sendRedirect(request.getContextPath() + "/login");
                return;
            }
        } else if (!"/logout".equals(path)) {
            String home = AuthSession.home(session);
            if (home != null) {
                response.sendRedirect(request.getContextPath() + home);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
