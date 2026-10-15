package filter;

import dao.PromotionDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;

/** Loads current offers for the existing index.jsp entry point. */
@WebFilter(urlPatterns = {"/index.jsp", "/"})
public class HomePromotionFilter implements Filter {
    private final PromotionDAO promotions = new PromotionDAO();

    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        ((HttpServletResponse) response).setHeader("Cache-Control", "no-store");
        try {
            request.setAttribute("homePromotions", promotions.getAvailablePromotions());
        } catch (SQLException e) {
            request.getServletContext().log("Could not load homepage promotions.", e);
            request.setAttribute("homePromotions", Collections.emptyList());
            request.setAttribute("homePromotionsUnavailable", Boolean.TRUE);
        }
        chain.doFilter(request, response);
    }
}
