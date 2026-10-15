package controller;

import dao.ProductCatalogDAO;
import dao.ProductReviewDAO;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Collections;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.Product;
import service.ProductImageStore;

@WebServlet("/product-detail")
public class ProductDetailController extends HttpServlet {

    private ProductCatalogDAO productCatalogDAO;
    private ProductReviewDAO productReviewDAO;

    @Override
    public void init() {
        productCatalogDAO = new ProductCatalogDAO();
        productReviewDAO = new ProductReviewDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        int productID;

        try {
            productID = Integer.parseInt(request.getParameter("productId"));
        } catch (NumberFormatException | NullPointerException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (productID <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            Product product =
                    productCatalogDAO.getActiveProductById(productID);

            if (product == null
                    || !"Active".equals(product.getStatus())) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            request.setAttribute("product", product);
            request.setAttribute("productImages", ProductImageStore.from(getServletContext()).list(productID));
            request.setAttribute("imageColors", ProductImageStore.from(getServletContext()).imageColors(productID));
            request.setAttribute(
                    "variants",
                    productCatalogDAO.getActiveVariantsByProduct(productID)
            );

            try {
                request.setAttribute(
                        "reviews",
                        productReviewDAO.getVisibleReviewsByProduct(productID)
                );
                request.setAttribute(
                        "averageRating",
                        productReviewDAO.getAverageRating(productID)
                );
            } catch (SQLException e) {
                getServletContext().log(
                        "Could not load product reviews.",
                        e
                );
                request.setAttribute("reviews", Collections.emptyList());
                request.setAttribute("averageRating", BigDecimal.ZERO);
            }

            request.getRequestDispatcher("/product-detail.jsp")
                    .forward(request, response);

        } catch (SQLException e) {
            throw new ServletException(
                    "Could not load product details from the database.",
                    e
            );
        }
    }
}
