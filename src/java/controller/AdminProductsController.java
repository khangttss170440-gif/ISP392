package controller;

import dao.AdminProductDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Staff;

@WebServlet("/admin/products")
public class AdminProductsController extends HttpServlet {

    private static final String PAGE = "/admin/products.jsp";
    private static final String MESSAGE_KEY = "ADMIN_PRODUCT_MESSAGE";
    private static final String ERROR_KEY = "ADMIN_PRODUCT_ERROR";

    private AdminProductDAO productDAO;

    @Override
    public void init() {
        productDAO = new AdminProductDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if (requireAdmin(request, response) == null) {
            return;
        }

        HttpSession session = request.getSession(false);
        request.setAttribute("message", takeFlash(session, MESSAGE_KEY));
        request.setAttribute("error", takeFlash(session, ERROR_KEY));

        try {
            request.setAttribute("products", productDAO.getAllProducts());
        } catch (SQLException e) {
            getServletContext().log("Could not load admin products.", e);
            request.setAttribute("error", "Không tải được danh sách sản phẩm.");
        }

        request.getRequestDispatcher(PAGE).forward(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if (requireAdmin(request, response) == null) {
            return;
        }

        HttpSession session = request.getSession(false);

        try {
            int productID = Integer.parseInt(
                    request.getParameter("productId")
            );

            productDAO.updateProductStatus(
                    productID,
                    request.getParameter("status")
            );

            session.setAttribute(
                    MESSAGE_KEY,
                    "Đã cập nhật trạng thái sản phẩm."
            );

        } catch (NumberFormatException e) {
            session.setAttribute(ERROR_KEY, "Mã sản phẩm không hợp lệ.");

        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());

        } catch (SQLException e) {
            getServletContext().log("Could not update product status.", e);
            session.setAttribute(
                    ERROR_KEY,
                    "Không thể cập nhật trạng thái sản phẩm."
            );
        }

        response.sendRedirect(
                request.getContextPath() + "/admin/products"
        );
    }

    private Staff requireAdmin(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        Staff staff = getStaff(request.getSession(false));

        if (staff == null) {
            response.sendRedirect(
                    request.getContextPath() + "/login.jsp"
            );
            return null;
        }

        if (!"Active".equalsIgnoreCase(staff.getStatus())
                || !"Admin".equalsIgnoreCase(staff.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        return staff;
    }

    private Staff getStaff(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute("staff");

        if (!(value instanceof Staff)) {
            value = session.getAttribute("STAFF");
        }

        return value instanceof Staff ? (Staff) value : null;
    }

    private String takeFlash(HttpSession session, String key) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(key);
        session.removeAttribute(key);

        return value == null ? null : value.toString();
    }
}