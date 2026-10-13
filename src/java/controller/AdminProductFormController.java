package controller;

import dao.AdminProductDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Product;
import model.ProductCategory;
import model.Staff;

@WebServlet(urlPatterns = {"/admin/product-form", "/staff/warehouse/product-form"})
public class AdminProductFormController extends HttpServlet {

    private static final String PAGE = "/admin/product-form.jsp";
    private AdminProductDAO productDAO;

    @Override
    public void init() {
        productDAO = new AdminProductDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {
        String productIDValue = request.getParameter("productId");
        boolean editing = productIDValue != null && !productIDValue.trim().isEmpty();
        Staff actor = requireProductAccess(request, response, editing);
        if (actor == null) return;

        prepareFormContext(request, actor);
        if ("1".equals(request.getParameter("created"))) {
            request.setAttribute("message",
                    "Đã đăng sản phẩm. Admin tiếp tục cấu hình biến thể/SKU để khách có thể mua.");
        }

        try {
            loadCategories(request);
            if (editing) {
                int productID = Integer.parseInt(productIDValue);
                Product product = productDAO.getProductById(productID);
                if (product == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                request.setAttribute("product", product);
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        } catch (SQLException e) {
            getServletContext().log("Could not load product form.", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        request.getRequestDispatcher(PAGE).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String productIDValue = request.getParameter("productId");
        boolean editing = productIDValue != null && !productIDValue.trim().isEmpty();
        Staff actor = requireProductAccess(request, response, editing);
        if (actor == null) return;

        HttpSession session = request.getSession(false);
        Product product = readProduct(request);
        prepareFormContext(request, actor);

        try {
            if (!editing) {
                int createdProductID = productDAO.createProduct(product, actor.getStaffID());
                if ("Warehouse".equalsIgnoreCase(actor.getRole())) {
                    response.sendRedirect(request.getContextPath()
                            + "/staff/warehouse/variants?productId="
                            + createdProductID + "&created=product");
                    return;
                }
                session.setAttribute("ADMIN_PRODUCT_MESSAGE",
                        "Đã tạo sản phẩm. Tiếp theo hãy thêm biến thể/SKU.");
            } else {
                product.setProductID(Integer.parseInt(productIDValue));
                productDAO.updateProduct(product, actor.getStaffID());
                session.setAttribute("ADMIN_PRODUCT_MESSAGE", "Đã cập nhật sản phẩm.");
            }
            response.sendRedirect(request.getContextPath() + "/admin/products");
        } catch (NumberFormatException e) {
            forwardError(request, response, product, "Mã sản phẩm không hợp lệ.");
        } catch (IllegalArgumentException e) {
            forwardError(request, response, product, e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("Could not save product.", e);
            String message = "Không thể lưu sản phẩm.";
            if ("PRODUCT_NOT_FOUND".equals(e.getMessage())) {
                message = "Không tìm thấy sản phẩm.";
            }
            forwardError(request, response, product, message);
        }
    }

    private Product readProduct(HttpServletRequest request) {
        Product product = new Product();
        try {
            product.setCategoryID(Integer.parseInt(request.getParameter("categoryId")));
        } catch (Exception e) {
            product.setCategoryID(0);
        }
        product.setProductName(request.getParameter("productName"));
        product.setDescription(request.getParameter("description"));
        product.setBrand(request.getParameter("brand"));
        product.setMaterial(request.getParameter("material"));
        String status = request.getParameter("status");
        product.setStatus(status == null || status.trim().isEmpty() ? "Active" : status);
        return product;
    }

    private void loadCategories(HttpServletRequest request) throws SQLException {
        List<ProductCategory> categories = productDAO.getActiveCategories();
        request.setAttribute("categories", categories);
    }

    private void forwardError(HttpServletRequest request,
                              HttpServletResponse response,
                              Product product,
                              String error)
            throws ServletException, IOException {
        request.setAttribute("product", product);
        request.setAttribute("error", error);
        Staff actor = getStaff(request.getSession(false));
        if (actor != null) prepareFormContext(request, actor);
        try {
            loadCategories(request);
        } catch (SQLException e) {
            getServletContext().log("Could not reload categories.", e);
        }
        request.getRequestDispatcher(PAGE).forward(request, response);
    }

    private Staff requireProductAccess(HttpServletRequest request,
                                       HttpServletResponse response,
                                       boolean editing)
            throws IOException {
        Staff staff = getStaff(request.getSession(false));
        if (staff == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return null;
        }
        boolean isAdmin = "Admin".equalsIgnoreCase(staff.getRole());
        boolean isWarehouse = "Warehouse".equalsIgnoreCase(staff.getRole());
        if (!"Active".equalsIgnoreCase(staff.getStatus())
                || (!isAdmin && !(isWarehouse && !editing))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return staff;
    }

    private void prepareFormContext(HttpServletRequest request, Staff actor) {
        boolean warehouseCreator = "Warehouse".equalsIgnoreCase(actor.getRole());
        String action = warehouseCreator
                ? "/staff/warehouse/product-form" : "/admin/product-form";
        String cancel = warehouseCreator
                ? "/staff/warehouse/orders" : "/admin/products";
        request.setAttribute("warehouseCreator", warehouseCreator);
        request.setAttribute("formAction", request.getContextPath() + action);
        request.setAttribute("cancelUrl", request.getContextPath() + cancel);
    }

    private Staff getStaff(HttpSession session) {
        if (session == null) return null;
        Object value = session.getAttribute("staff");
        if (!(value instanceof Staff)) value = session.getAttribute("STAFF");
        return value instanceof Staff ? (Staff) value : null;
    }
}
