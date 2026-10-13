package controller;

import dao.CustomerOrderDAO;
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
import model.CustomerOrder;
import java.util.Collections;
import java.util.Set;

@WebServlet(urlPatterns = {
    "/customer/orders",
    "/customer/order-detail"
})
public class CustomerOrderController extends HttpServlet {

    private CustomerOrderDAO customerOrderDAO;
    private ProductReviewDAO productReviewDAO;

    @Override
    public void init() {
        customerOrderDAO = new CustomerOrderDAO();
        productReviewDAO = new ProductReviewDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        Customer customer = getCustomer(request.getSession(false));

        if (customer == null) {
            response.sendRedirect(
                    request.getContextPath() + "/login.jsp"
            );
            return;
        }

        if (request.getServletPath().endsWith("/order-detail")) {
            showOrderDetail(request, response, customer);
        } else {
            showOrders(request, response, customer);
        }
    }

    private void showOrders(
            HttpServletRequest request,
            HttpServletResponse response,
            Customer customer)
            throws ServletException, IOException {

        try {
            request.setAttribute(
                    "orders",
                    customerOrderDAO.getOrdersByCustomer(
                            customer.getCustomerID()
                    )
            );
        } catch (SQLException e) {
            getServletContext().log("Could not load customer orders.", e);
            request.setAttribute(
                    "error",
                    "Không tải được danh sách đơn hàng."
            );
        }

        request.getRequestDispatcher("/customer/orders.jsp")
                .forward(request, response);
    }

    private void showOrderDetail(
            HttpServletRequest request,
            HttpServletResponse response,
            Customer customer)
            throws ServletException, IOException {

        int orderID;

        try {
            orderID = Integer.parseInt(request.getParameter("orderId"));
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            CustomerOrder order = customerOrderDAO.getOrderByCustomer(
                    customer.getCustomerID(),
                    orderID
            );

            if (order == null) {
                // Đơn không tồn tại hoặc không thuộc khách hàng đăng nhập.
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            request.setAttribute("order", order);
            Set<Integer> reviewedProductIDs = "Delivered".equalsIgnoreCase(order.getOrderStatus())
                    ? productReviewDAO.getReviewedProductIDs(customer.getCustomerID(), orderID)
                    : Collections.<Integer>emptySet();
            request.setAttribute("reviewedProductIDs", reviewedProductIDs);
            if ("1".equals(request.getParameter("reviewSubmitted"))) {
                request.setAttribute("reviewMessage", "Đánh giá sản phẩm đã được gửi.");
            }
            request.setAttribute(
                    "orderLines",
                    customerOrderDAO.getOrderLinesByCustomer(
                            customer.getCustomerID(),
                            orderID
                    )
            );

        } catch (SQLException e) {
            getServletContext().log(
                    "Could not load customer order detail.",
                    e
            );
            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );
            return;
        }

        request.getRequestDispatcher("/customer/order-detail.jsp")
                .forward(request, response);
    }

    private Customer getCustomer(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute("customer");
        return value instanceof Customer ? (Customer) value : null;
    }
}
