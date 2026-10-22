package filter;

import dao.PromotionDAO;
import dao.ProductCatalogDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import model.Product;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;

/** Loads current offers and products for the existing index.jsp entry point. */
@WebFilter(urlPatterns = {"/index.jsp", "/"})
public class HomePromotionFilter implements Filter {
    private final PromotionDAO promotions = new PromotionDAO();
    private final ProductCatalogDAO products = new ProductCatalogDAO();

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
        try {
            List<Product> latestProducts = products.getActiveProducts(null);
            request.setAttribute("homeProducts", latestProducts.subList(0, Math.min(7, latestProducts.size())));
        } catch (SQLException e) {
            request.getServletContext().log("Could not load homepage products.", e);
            request.setAttribute("homeProducts", Collections.emptyList());
            request.setAttribute("homeProductsUnavailable", Boolean.TRUE);
        }
        chain.doFilter(request, response);
    }
}
