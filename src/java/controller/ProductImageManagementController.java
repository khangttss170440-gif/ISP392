package controller;

import dao.AdminProductDAO;
import dao.AdminVariantDAO;
import model.ProductVariant;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Product;
import model.Staff;
import service.ProductImageStore;

@WebServlet(urlPatterns = {"/staff/warehouse/product-images", "/admin/product-images"})
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 5 * 1024 * 1024, maxRequestSize = 32 * 1024 * 1024)
public class ProductImageManagementController extends HttpServlet {
    private static final String TOKEN = "productImages.csrf";
    private static final String MESSAGE = "productImages.message";
    private static final String ERROR = "productImages.error";
    private AdminProductDAO products;
    private ProductImageStore images;
    private AdminVariantDAO variants;

    @Override public void init() {
        products = new AdminProductDAO();
        images = ProductImageStore.from(getServletContext());
        variants = new AdminVariantDAO();
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Staff staff = requireAccess(request, response);
        if (staff == null) return;
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        if (session.getAttribute(TOKEN) == null) session.setAttribute(TOKEN, UUID.randomUUID().toString());
        request.setAttribute("csrfToken", session.getAttribute(TOKEN));
        request.setAttribute("warehouseMode", "Warehouse".equalsIgnoreCase(staff.getRole()));
        request.setAttribute("imageAction", request.getContextPath() + route(staff));
        request.setAttribute("message", session.getAttribute(MESSAGE));
        request.setAttribute("error", session.getAttribute(ERROR));
        session.removeAttribute(MESSAGE);
        session.removeAttribute(ERROR);
        try {
            request.setAttribute("products", products.getAllProducts());
            String value = request.getParameter("productId");
            if (value != null && !value.isBlank()) {
                int id = positiveId(value);
                Product selected = products.getProductById(id);
                if (selected == null) { response.sendError(404); return; }
                request.setAttribute("selectedProduct", selected);
                request.setAttribute("productImages", images.list(id));
                request.setAttribute("imageColors", images.imageColors(id));
                request.setAttribute("productColors", productColors(id));
            }
            request.getRequestDispatcher("/admin/product-images.jsp").forward(request, response);
        } catch (IllegalArgumentException e) {
            response.sendError(400);
        } catch (SQLException e) {
            getServletContext().log("Could not load product image manager.", e);
            response.sendError(503, "Chưa đọc được danh sách sản phẩm. Vui lòng thử lại sau.");
        }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        Staff staff = requireAccess(request, response);
        if (staff == null) return;
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        int id = 0;
        try {
            Object token = session.getAttribute(TOKEN);
            if (!(token instanceof String) || !token.equals(request.getParameter("csrfToken"))) {
                response.sendError(403, "Phiên làm việc không hợp lệ. Vui lòng tải lại trang.");
                return;
            }
            id = positiveId(request.getParameter("productId"));
            if (products.getProductById(id) == null) { response.sendError(404); return; }
            session.removeAttribute(MESSAGE);
            session.removeAttribute(ERROR);
            String action = request.getParameter("action");
            String file = request.getParameter("file");
            if ("upload".equals(action)) {
                List<ProductImageStore.Upload> uploads = images.readUploads(request.getParts());
                if (uploads.isEmpty()) throw new IllegalArgumentException("Hãy chọn ít nhất một ảnh để tải lên.");
                images.save(id, uploads);
            } else if ("replace".equals(action)) {
                images.replace(id, file, images.readUploads(request.getParts()));
            } else if ("color".equals(action)) {
                String color = request.getParameter("color");
                color = color == null ? "" : color.trim();
                if (!color.isEmpty() && !productColors(id).contains(color)) {
                    throw new IllegalArgumentException("Màu không thuộc biến thể của sản phẩm này.");
                }
                images.setImageColor(id, file, color);
            } else if ("delete".equals(action)) {
                images.delete(id, file);
            } else if ("cover".equals(action)) {
                images.setCover(id, file);
            } else {
                throw new IllegalArgumentException("Thao tác ảnh không hợp lệ.");
            }
            session.setAttribute(MESSAGE, "Đã cập nhật ảnh sản phẩm #" + id + ".");
        } catch (IllegalStateException e) {
            session.setAttribute(ERROR, "Ảnh vượt giới hạn tải lên. Tối đa 6 ảnh, mỗi ảnh 5 MB.");
        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR, e.getMessage());
        } catch (SQLException e) {
            getServletContext().log("Could not look up product for image update.", e);
            session.setAttribute(ERROR, "Chưa đọc được sản phẩm. Vui lòng thử lại sau.");
        } catch (ServletException e) {
            getServletContext().log("Could not read product image upload request.", e);
            session.setAttribute(ERROR, "Không đọc được dữ liệu ảnh tải lên. Hãy tải lại trang và chọn ảnh lại.");
        } catch (IOException e) {
            getServletContext().log("Could not store product image.", e);
            session.setAttribute(ERROR, "Không lưu được ảnh vào thư mục dữ liệu Tomcat. Vui lòng kiểm tra quyền ghi rồi thử lại.");
        }
        response.sendRedirect(request.getContextPath() + route(staff) + (id > 0 ? "?productId=" + id : ""));
    }

    private java.util.Set<String> productColors(int id) throws SQLException {
        java.util.Set<String> colors = new java.util.LinkedHashSet<>();
        for (ProductVariant variant : variants.getVariants(id)) {
            String color = variant.getColor();
            colors.add(color == null || color.isBlank() ? "Mặc định" : color.trim());
        }
        return colors;
    }

    private int positiveId(String value) {
        int id;
        try { id = Integer.parseInt(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Mã sản phẩm không hợp lệ."); }
        if (id <= 0) throw new IllegalArgumentException("Mã sản phẩm không hợp lệ.");
        return id;
    }

    private String route(Staff staff) {
        return "Warehouse".equalsIgnoreCase(staff.getRole()) ? "/staff/warehouse/product-images" : "/admin/product-images";
    }

    private Staff requireAccess(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("staff");
        if (!(value instanceof Staff) && session != null) value = session.getAttribute("STAFF");
        if (!(value instanceof Staff)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return null;
        }
        Staff staff = (Staff) value;
        if (!"Active".equalsIgnoreCase(staff.getStatus())
                || !("Warehouse".equalsIgnoreCase(staff.getRole()) || "Admin".equalsIgnoreCase(staff.getRole()))) {
            response.sendError(403);
            return null;
        }
        return staff;
    }
}
