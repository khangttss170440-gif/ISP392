package controller;

import dao.AdminProductDAO;
import dao.AdminVariantDAO;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.ProductVariant;
import model.Staff;

@WebServlet(urlPatterns = {"/admin/variants", "/staff/warehouse/variants"})
public class AdminVariantController extends HttpServlet {

    private static final String PAGE = "/admin/variants.jsp";
    private static final String ADMIN_MESSAGE_KEY = "ADMIN_VARIANT_MESSAGE";
    private static final String ADMIN_ERROR_KEY = "ADMIN_VARIANT_ERROR";
    private static final String STAFF_MESSAGE_KEY = "WAREHOUSE_VARIANT_MESSAGE";
    private static final String STAFF_ERROR_KEY = "WAREHOUSE_VARIANT_ERROR";
    private AdminVariantDAO variantDAO;
    private AdminProductDAO productDAO;

    @Override
    public void init() {
        variantDAO = new AdminVariantDAO();
        productDAO = new AdminProductDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Staff staff = requireActiveStaff(request, response);
        if (staff == null) return;
        boolean warehouseMode = "Warehouse".equalsIgnoreCase(staff.getRole());
        if (!warehouseMode && !"Admin".equalsIgnoreCase(staff.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        HttpSession session = request.getSession(false);
        request.setAttribute("message", takeFlash(session,
                warehouseMode ? STAFF_MESSAGE_KEY : ADMIN_MESSAGE_KEY));
        request.setAttribute("error", takeFlash(session,
                warehouseMode ? STAFF_ERROR_KEY : ADMIN_ERROR_KEY));
        request.setAttribute("warehouseMode", warehouseMode);
        request.setAttribute("formAction", request.getContextPath()
                + (warehouseMode ? "/staff/warehouse/variants" : "/admin/variants"));
        request.setAttribute("backUrl", request.getContextPath()
                + (warehouseMode ? "/staff/warehouse/orders" : "/admin/products"));

        if ("product".equals(request.getParameter("created"))) {
            request.setAttribute("message", "Sản phẩm đã được tạo. Thêm SKU, màu, kích cỡ và giá bán để khách có thể mua.");
        } else if ("variant".equals(request.getParameter("created"))) {
            request.setAttribute("message", "Đã thêm biến thể/SKU.");
        }

        int productID = 0;
        try {
            String productIDValue = request.getParameter("productId");
            if (productIDValue != null && !productIDValue.trim().isEmpty()) {
                productID = Integer.parseInt(productIDValue);
                if (productID < 0) throw new NumberFormatException();
            }
            request.setAttribute("selectedProductId", productID);
            request.setAttribute("variants", variantDAO.getVariants(productID));
            request.setAttribute("products", productDAO.getAllProducts());
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        } catch (SQLException e) {
            getServletContext().log("Could not load variants.", e);
            request.setAttribute("error", "Không tải được biến thể hoặc sản phẩm.");
        }
        request.getRequestDispatcher(PAGE).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        Staff staff = requireActiveStaff(request, response);
        if (staff == null) return;
        boolean isAdmin = "Admin".equalsIgnoreCase(staff.getRole());
        boolean isWarehouse = "Warehouse".equalsIgnoreCase(staff.getRole());
        String action = request.getParameter("action");
        if (!isAdmin && !(isWarehouse && "create".equals(action))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        HttpSession session = request.getSession(false);
        String messageKey = isWarehouse ? STAFF_MESSAGE_KEY : ADMIN_MESSAGE_KEY;
        String errorKey = isWarehouse ? STAFF_ERROR_KEY : ADMIN_ERROR_KEY;
        String redirectPath = isWarehouse ? "/staff/warehouse/variants" : "/admin/variants";
        String productIdValue = request.getParameter("productId");
        String selectedProduct = "";
        try {
            int selected = Integer.parseInt(productIdValue);
            if (selected > 0) selectedProduct = "?productId=" + selected;
        } catch (RuntimeException ignored) {
            // The DAO/controller reports malformed product data below.
        }

        boolean createdVariant = false;
        try {
            if ("create".equals(action)) {
                ProductVariant variant = readVariant(request);
                variantDAO.createVariant(variant);
                session.setAttribute(messageKey, "Đã thêm biến thể. Tồn kho ban đầu là 0.");
                createdVariant = isWarehouse;
            } else if ("update".equals(action) && isAdmin) {
                ProductVariant variant = readVariant(request);
                variant.setVariantID(Integer.parseInt(request.getParameter("variantId")));
                variantDAO.updateVariant(variant);
                session.setAttribute(messageKey, "Đã cập nhật biến thể.");
            } else if ("toggle".equals(action) && isAdmin) {
                int variantID = Integer.parseInt(request.getParameter("variantId"));
                variantDAO.updateVariantStatus(variantID, request.getParameter("status"));
                session.setAttribute(messageKey, "Đã cập nhật trạng thái biến thể.");
            } else {
                throw new IllegalArgumentException("Thao tác biến thể không hợp lệ.");
            }
        } catch (NumberFormatException e) {
            session.setAttribute(errorKey, "Mã, sản phẩm hoặc giá trị không hợp lệ.");
        } catch (IllegalArgumentException e) {
            session.setAttribute(errorKey, variantErrorMessage(e));
        } catch (SQLException e) {
            getServletContext().log("Could not save variant.", e);
            session.setAttribute(errorKey, "VARIANT_NOT_FOUND".equals(e.getMessage())
                    ? "Không tìm thấy biến thể." : "Không thể lưu biến thể.");
        }

        response.sendRedirect(request.getContextPath() + redirectPath + selectedProduct
                + (createdVariant ? (selectedProduct.isEmpty() ? "?" : "&") + "created=variant" : ""));
    }

    private ProductVariant readVariant(HttpServletRequest request) {
        ProductVariant variant = new ProductVariant();
        variant.setProductID(Integer.parseInt(request.getParameter("productId")));
        variant.setSku(request.getParameter("sku"));
        variant.setColor(request.getParameter("color"));
        variant.setSize(request.getParameter("size"));
        variant.setPrice(new BigDecimal(request.getParameter("price")));
        String quantity = request.getParameter("quantity");
        variant.setQuantity(quantity == null || quantity.trim().isEmpty()
                ? 0 : Integer.parseInt(quantity.trim()));
        String status = request.getParameter("status");
        variant.setStatus(status == null || status.trim().isEmpty() ? "Active" : status);
        return variant;
    }

    private Staff requireActiveStaff(HttpServletRequest request,
                                     HttpServletResponse response)
            throws IOException {
        Staff staff = getStaff(request.getSession(false));
        if (staff == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return null;
        }
        if (!"Active".equalsIgnoreCase(staff.getStatus())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return staff;
    }

    private Staff getStaff(HttpSession session) {
        if (session == null) return null;
        Object value = session.getAttribute("staff");
        if (!(value instanceof Staff)) value = session.getAttribute("STAFF");
        return value instanceof Staff ? (Staff) value : null;
    }

    private Object takeFlash(HttpSession session, String key) {
        if (session == null) return null;
        Object value = session.getAttribute(key);
        session.removeAttribute(key);
        return value;
    }

    private String variantErrorMessage(IllegalArgumentException e) {
        if ("SKU_ALREADY_EXISTS".equals(e.getMessage())) return "SKU này đã tồn tại.";
        return e.getMessage();
    }
}
