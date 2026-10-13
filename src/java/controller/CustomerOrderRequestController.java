package controller;

import dao.CustomerOrderDAO;
import dao.CustomerOrderRequestDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Customer;
import model.CustomerOrder;

@WebServlet("/customer/order-request")
public class CustomerOrderRequestController extends HttpServlet {

    private CustomerOrderDAO customerOrderDAO;
    private CustomerOrderRequestDAO requestDAO;

    @Override
    public void init() {
        customerOrderDAO = new CustomerOrderDAO();
        requestDAO = new CustomerOrderRequestDAO();
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

        int orderID;

        try {
            orderID = Integer.parseInt(request.getParameter("orderId"));
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (!loadForm(request, response, customer, orderID)) {
            return;
        }

        request.getRequestDispatcher("/customer/order-request.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        Customer customer = getCustomer(request.getSession(false));

        if (customer == null) {
            response.sendRedirect(
                    request.getContextPath() + "/login.jsp"
            );
            return;
        }

        int orderID;

        try {
            orderID = Integer.parseInt(request.getParameter("orderId"));

            Map<Integer, Integer> lines = readRequestedLines(request);

            requestDAO.createRequest(
                    customer.getCustomerID(),
                    orderID,
                    request.getParameter("requestType"),
                    request.getParameter("reason"),
                    lines
            );

            response.sendRedirect(
                    request.getContextPath()
                    + "/customer/orders?requestSent=1"
            );

        } catch (NumberFormatException e) {
            request.setAttribute("error", "Mã đơn hàng không hợp lệ.");
            request.getRequestDispatcher("/customer/order-request.jsp")
                    .forward(request, response);

        } catch (IllegalArgumentException e) {
            request.setAttribute("error", messageFor(e.getMessage()));

            if (request.getParameter("orderId") != null) {
                try {
                    orderID = Integer.parseInt(
                            request.getParameter("orderId")
                    );

                    if (!loadForm(request, response, customer, orderID)) {
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
            }

            request.getRequestDispatcher("/customer/order-request.jsp")
                    .forward(request, response);

        } catch (SQLException e) {
            getServletContext().log("Could not create customer order request.", e);
            request.setAttribute("error", messageFor(e.getMessage()));

            try {
                orderID = Integer.parseInt(
                        request.getParameter("orderId")
                );

                if (!loadForm(request, response, customer, orderID)) {
                    return;
                }
            } catch (NumberFormatException ignored) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            request.getRequestDispatcher("/customer/order-request.jsp")
                    .forward(request, response);
        }
    }

    private boolean loadForm(
            HttpServletRequest request,
            HttpServletResponse response,
            Customer customer,
            int orderID) throws IOException {

        try {
            CustomerOrder order = customerOrderDAO.getOrderByCustomer(
                    customer.getCustomerID(),
                    orderID
            );

            if (order == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return false;
            }

            request.setAttribute("order", order);
            request.setAttribute(
                    "requestableLines",
                    requestDAO.getRequestableLines(
                            customer.getCustomerID(),
                            orderID
                    )
            );

            request.setAttribute(
                    "canRequestCancellation",
                    "Pending".equalsIgnoreCase(order.getOrderStatus())
                    || "Confirmed".equalsIgnoreCase(order.getOrderStatus())
            );

            request.setAttribute(
                    "canRequestReturnOrExchange",
                    "Delivered".equalsIgnoreCase(order.getOrderStatus())
            );

            return true;

        } catch (SQLException e) {
            getServletContext().log("Could not load order request form.", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return false;
        }
    }

    private Map<Integer, Integer> readRequestedLines(
            HttpServletRequest request) {

        Map<Integer, Integer> lines = new LinkedHashMap<>();

        String[] detailIDs =
                request.getParameterValues("orderDetailId");
        String[] quantities =
                request.getParameterValues("quantity");

        if (detailIDs == null && quantities == null) {
            return lines;
        }

        if (detailIDs == null || quantities == null
                || detailIDs.length != quantities.length) {
            throw new IllegalArgumentException("INVALID_REQUEST_LINE");
        }

        if (detailIDs.length > 20) {
            throw new IllegalArgumentException("TOO_MANY_REQUEST_LINES");
        }

        for (int i = 0; i < detailIDs.length; i++) {
            if (detailIDs[i] == null || detailIDs[i].trim().isEmpty()) {
                continue;
            }

            int orderDetailID = Integer.parseInt(detailIDs[i]);
            int quantity = Integer.parseInt(quantities[i]);

            if (lines.put(orderDetailID, quantity) != null) {
                throw new IllegalArgumentException("DUPLICATE_REQUEST_LINE");
            }
        }

        return lines;
    }

    private Customer getCustomer(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute("customer");
        return value instanceof Customer ? (Customer) value : null;
    }

    private String messageFor(String code) {
        if ("INVALID_REQUEST_TYPE".equals(code)) {
            return "Loại yêu cầu không hợp lệ.";
        }
        if ("INVALID_REASON".equals(code)) {
            return "Hãy nhập lý do, tối đa 1000 ký tự.";
        }
        if ("REQUEST_LINES_REQUIRED".equals(code)) {
            return "Hãy chọn ít nhất một sản phẩm cần trả hoặc đổi.";
        }
        if ("ORDER_NOT_CANCELLABLE".equals(code)) {
            return "Đơn hàng hiện không thể gửi yêu cầu hủy.";
        }
        if ("ORDER_NOT_DELIVERED".equals(code)) {
            return "Chỉ có thể yêu cầu trả hoặc đổi sau khi đơn đã giao.";
        }
        if ("PENDING_REQUEST_EXISTS".equals(code)) {
            return "Đơn hàng đã có một yêu cầu cùng loại đang chờ xử lý.";
        }
        if ("INVALID_ORDER_LINE".equals(code)) {
            return "Sản phẩm được chọn không thuộc đơn hàng này.";
        }
        if ("QUANTITY_EXCEEDS_AVAILABLE".equals(code)) {
            return "Số lượng yêu cầu vượt quá số lượng còn có thể trả hoặc đổi.";
        }
        if ("INVALID_REQUEST_LINE".equals(code)
                || "DUPLICATE_REQUEST_LINE".equals(code)) {
            return "Thông tin sản phẩm yêu cầu không hợp lệ.";
        }
        if ("TOO_MANY_REQUEST_LINES".equals(code)) {
            return "Mỗi yêu cầu được chọn tối đa 20 dòng sản phẩm.";
        }
        if ("ORDER_NOT_FOUND_OR_NOT_OWNED".equals(code)) {
            return "Không tìm thấy đơn hàng của tài khoản này.";
        }

        return "Không thể tạo yêu cầu. Vui lòng kiểm tra lại thông tin.";
    }
}