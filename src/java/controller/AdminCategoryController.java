package controller;

import dao.AdminCategoryDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Staff;
import model.ProductCategory;

@WebServlet("/admin/categories")
public class AdminCategoryController extends HttpServlet {

    private static final String PAGE = "/admin/categories.jsp";
    private static final String MESSAGE_KEY = "ADMIN_CATEGORY_MESSAGE";
    private static final String ERROR_KEY = "ADMIN_CATEGORY_ERROR";

    private AdminCategoryDAO categoryDAO;

    @Override
    public void init() {
        categoryDAO = new AdminCategoryDAO();
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
            request.setAttribute(
                    "categories",
                    categoryDAO.getAllCategories()
            );
        } catch (SQLException e) {
            getServletContext().log("Could not load categories.", e);
            request.setAttribute("error", "Không tải được danh mục.");
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

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        String action = request.getParameter("action");

        try {
            if ("create".equals(action)) {
                categoryDAO.createCategory(
                        request.getParameter("categoryName"),
                        request.getParameter("description")
                );
                session.setAttribute(
                        MESSAGE_KEY,
                        "Đã thêm danh mục."
                );

            } else if ("update-all".equals(action)) {
                categoryDAO.updateCategories(parseCategoryUpdates(request));
                session.setAttribute(MESSAGE_KEY, "Đã lưu tất cả thay đổi danh mục.");

            } else if ("update".equals(action)) {
                int categoryID = parseCategoryID(request);

                categoryDAO.updateCategory(
                        categoryID,
                        request.getParameter("categoryName"),
                        request.getParameter("description"),
                        request.getParameter("status")
                );
                session.setAttribute(
                        MESSAGE_KEY,
                        "Đã cập nhật danh mục."
                );

            } else if ("toggle".equals(action)) {
                int categoryID = parseCategoryID(request);

                categoryDAO.updateCategoryStatus(
                        categoryID,
                        request.getParameter("status")
                );
                session.setAttribute(
                        MESSAGE_KEY,
                        "Đã cập nhật trạng thái danh mục."
                );

            } else {
                throw new IllegalArgumentException(
                        "Thao tác danh mục không hợp lệ."
                );
            }

        } catch (NumberFormatException e) {
            session.setAttribute(ERROR_KEY, "Mã danh mục không hợp lệ.");

        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, categoryErrorMessage(e));

        } catch (SQLException e) {
            getServletContext().log("Could not save category.", e);

            if ("CATEGORY_NOT_FOUND".equals(e.getMessage())) {
                session.setAttribute(ERROR_KEY, "Không tìm thấy danh mục.");
            } else {
                session.setAttribute(
                        ERROR_KEY,
                        "Không thể lưu danh mục."
                );
            }
        }

        response.sendRedirect(
                request.getContextPath() + "/admin/categories"
        );
    }

    private int parseCategoryID(HttpServletRequest request) {
        return Integer.parseInt(request.getParameter("categoryId"));
    }

    private List<ProductCategory> parseCategoryUpdates(HttpServletRequest request) {
        String[] ids = request.getParameterValues("categoryId");
        String[] names = request.getParameterValues("categoryName");
        String[] descriptions = request.getParameterValues("description");
        String[] statuses = request.getParameterValues("status");
        if (ids == null || names == null || descriptions == null || statuses == null
                || ids.length == 0 || ids.length != names.length
                || ids.length != descriptions.length || ids.length != statuses.length) {
            throw new IllegalArgumentException("Dữ liệu danh mục không đầy đủ. Hãy tải lại trang rồi thử lại.");
        }
        List<ProductCategory> updates = new ArrayList<>(ids.length);
        for (int i = 0; i < ids.length; i++) {
            ProductCategory category = new ProductCategory();
            category.setCategoryID(Integer.parseInt(ids[i]));
            category.setCategoryName(names[i]);
            category.setDescription(descriptions[i]);
            category.setStatus(statuses[i]);
            updates.add(category);
        }
        return updates;
    }

    private Staff requireAdmin(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        HttpSession session = request.getSession(false);
        Staff staff = getStaff(session);

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

    private String categoryErrorMessage(IllegalArgumentException e) {
        if ("CATEGORY_NAME_EXISTS".equals(e.getMessage())) {
            return "Tên danh mục đã tồn tại.";
        }

        return e.getMessage();
    }
}
