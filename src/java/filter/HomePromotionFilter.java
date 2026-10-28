package filter;

import dao.PromotionDAO;
import dao.ProductCatalogDAO;
import dao.OutfitCatalogDAO;
import dao.OutfitCollectionDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.Product;
import model.ProductCategory;
import model.OutfitSet;
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
            request.setAttribute("homeProducts", latestProducts.subList(0, Math.min(util.AppConfig.integer("HOME_PRODUCT_LIMIT", 1, 100), latestProducts.size())));
        } catch (SQLException e) {
            request.getServletContext().log("Could not load homepage products.", e);
            request.setAttribute("homeProducts", Collections.emptyList());
            request.setAttribute("homeProductsUnavailable", Boolean.TRUE);
        }
        try {
            List<ProductCategory> categories = products.getHomepageCategories();
            request.setAttribute("homeCategories", categories);
        } catch (SQLException e) {
            request.getServletContext().log("Could not load homepage categories.", e);
            request.setAttribute("homeCategories", Collections.emptyList());
            request.setAttribute("homeCategoriesUnavailable", Boolean.TRUE);
        }
        try {
            List<OutfitSet> published = new OutfitCollectionDAO().published(
                    new OutfitCatalogDAO().availableItems(), "all", null, null);
            Map<String, OutfitSet> byStyle = new LinkedHashMap<>();
            for (OutfitSet outfit : published) byStyle.putIfAbsent(outfit.getStyle(), outfit);
            request.setAttribute("homeOutfits", List.copyOf(byStyle.values()));
        } catch (SQLException e) {
            request.getServletContext().log("Could not load homepage outfit collections.", e);
            request.setAttribute("homeOutfits", Collections.emptyList());
        }
        chain.doFilter(request, response);
    }
}
