package controller;

import dao.ProductCatalogDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/products")
public class ProductController extends HttpServlet {

    private ProductCatalogDAO productCatalogDAO;

    @Override
    public void init() {
        productCatalogDAO = new ProductCatalogDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        Integer categoryID = null;
        String categoryValue = request.getParameter("categoryId");

        if (categoryValue != null && !categoryValue.trim().isEmpty()) {
            try {
                categoryID = Integer.valueOf(categoryValue.trim());

                if (categoryID <= 0) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        }

        try {
            request.setAttribute(
                    "categories",
                    productCatalogDAO.getActiveCategories()
            );
            request.setAttribute(
                    "productList",
                    productCatalogDAO.getActiveProducts(categoryID)
            );
            request.setAttribute("categoryId", categoryID);

            request.getRequestDispatcher("/products.jsp")
                    .forward(request, response);

        } catch (SQLException e) {
            throw new ServletException(
                    "Could not load product catalog from SQL Server.",
                    e
            );
        }
    }
}