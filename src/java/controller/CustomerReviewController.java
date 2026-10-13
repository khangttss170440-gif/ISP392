package controller;

import dao.ProductReviewDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Customer;
import model.Product;
import model.ProductReview;

@WebServlet("/customer/review")
public class CustomerReviewController extends HttpServlet {

    private ProductReviewDAO reviewDAO;

    @Override
    public void init() {
        reviewDAO = new ProductReviewDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        Customer customer = getCustomer(request.getSession(false));

        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        Integer orderID = positiveInteger(request.getParameter("orderId"));
        Integer productID = positiveInteger(request.getParameter("productId"));

        if (orderID == null || productID == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            Product product = reviewDAO.findReviewableProduct(
                    customer.getCustomerID(),
                    orderID,
                    productID
            );

            if (product == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            boolean alreadyReviewed = reviewDAO.hasReviewed(
                    customer.getCustomerID(),
                    orderID,
                    productID);
            request.setAttribute("alreadyReviewed", alreadyReviewed);
            if (alreadyReviewed) {
                request.setAttribute("error",
                        "Bạn đã đánh giá sản phẩm này trong đơn hàng.");
            }

            request.setAttribute("product", product);
            request.setAttribute("orderId", orderID);
            request.setAttribute("productId", productID);

            request.getRequestDispatcher("/customer/review-form.jsp")
                    .forward(request, response);

        } catch (SQLException e) {
            getServletContext().log("Could not load product review form.", e);
            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );
        }
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        Customer customer = getCustomer(request.getSession(false));

        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        Integer orderID = positiveInteger(request.getParameter("orderId"));
        Integer productID = positiveInteger(request.getParameter("productId"));
        Integer rating = positiveInteger(request.getParameter("rating"));
        String comment = value(request.getParameter("comment"));

        if (orderID == null || productID == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (rating == null || rating < 1 || rating > 5) {
            forwardError(request, response, customer, orderID, productID,
                    "Đánh giá phải từ 1 đến 5 sao.");
            return;
        }

        if (comment.length() > 2000) {
            forwardError(request, response, customer, orderID, productID,
                    "Nhận xét không được vượt quá 2000 ký tự.");
            return;
        }

        ProductReview review = new ProductReview();
        review.setCustomerID(customer.getCustomerID());
        review.setOrderID(orderID);
        review.setProductID(productID);
        review.setRating(rating);
        review.setComment(comment.isEmpty() ? null : comment);

        try {
            boolean created = reviewDAO.createReview(review);

            if (!created) {
                forwardError(request, response, customer, orderID, productID,
                        "Đơn hàng chưa giao, sản phẩm không thuộc đơn hàng "
                        + "hoặc bạn đã đánh giá sản phẩm này.");
                return;
            }

            response.sendRedirect(
                    request.getContextPath() + "/customer/order-detail?orderId="
                    + orderID + "&reviewSubmitted=1"
            );

        } catch (SQLException e) {
            getServletContext().log("Could not save product review.", e);
            forwardError(request, response, customer, orderID, productID,
                    "Không thể lưu đánh giá. Vui lòng thử lại.");
        }
    }

    private void forwardError(HttpServletRequest request,
                              HttpServletResponse response,
                              Customer customer,
                              int orderID,
                              int productID,
                              String message)
            throws ServletException, IOException {

        try {
            Product product = reviewDAO.findReviewableProduct(
                    customer.getCustomerID(),
                    orderID,
                    productID
            );

            if (product == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            request.setAttribute("product", product);
            request.setAttribute("orderId", orderID);
            request.setAttribute("productId", productID);
            request.setAttribute("alreadyReviewed", reviewDAO.hasReviewed(
                    customer.getCustomerID(), orderID, productID));
            request.setAttribute("error", message);

            request.getRequestDispatcher("/customer/review-form.jsp")
                    .forward(request, response);

        } catch (SQLException e) {
            getServletContext().log("Could not reload review form.", e);
            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );
        }
    }

    private Customer getCustomer(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute("customer");
        return value instanceof Customer ? (Customer) value : null;
    }

    private Integer positiveInteger(String value) {
        try {
            int number = Integer.parseInt(value);
            return number > 0 ? number : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
