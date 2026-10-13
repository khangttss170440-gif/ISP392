package controller;

import dao.InventoryStocktakingDAO;
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
import model.Staff;

@WebServlet("/staff/warehouse/stocktaking")
public class InventoryStocktakingController extends HttpServlet {

    private static final String MESSAGE_KEY = "STOCKTAKING_MESSAGE";
    private static final String ERROR_KEY = "STOCKTAKING_ERROR";

    private InventoryStocktakingDAO stocktakingDAO;

    @Override
    public void init() {
        stocktakingDAO = new InventoryStocktakingDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if (requireWarehouseStaff(request, response) == null) {
            return;
        }

        HttpSession session = request.getSession(false);
        request.setAttribute("message", takeFlash(session, MESSAGE_KEY));
        request.setAttribute("error", takeFlash(session, ERROR_KEY));

        loadPage(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        Staff staff = requireWarehouseStaff(request, response);

        if (staff == null) {
            return;
        }

        HttpSession session = request.getSession(false);

        try {
            Map<Integer, Integer> quantities =
                    readActualQuantities(request);

            stocktakingDAO.submitStocktaking(
                    staff.getStaffID(),
                    request.getParameter("cycleType"),
                    request.getParameter("note"),
                    quantities
            );

            session.setAttribute(
                    MESSAGE_KEY,
                    "Đã lưu phiếu kiểm kê và cập nhật tồn kho."
            );

        } catch (NumberFormatException e) {
            session.setAttribute(
                    ERROR_KEY,
                    "Mã biến thể hoặc số lượng không hợp lệ."
            );

        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());

        } catch (SQLException e) {
            getServletContext().log("Could not submit stocktaking.", e);

            if ("ACTIVE_VARIANT_NOT_FOUND".equals(e.getMessage())) {
                session.setAttribute(
                        ERROR_KEY,
                        "Có biến thể không còn hoạt động; chưa lưu phiếu kiểm kê."
                );
            } else {
                session.setAttribute(
                        ERROR_KEY,
                        "Không thể lưu phiếu kiểm kê."
                );
            }
        }

        response.sendRedirect(
                request.getContextPath()
                + "/staff/warehouse/stocktaking"
        );
    }

    private void loadPage(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        try {
            request.setAttribute(
                    "variants",
                    stocktakingDAO.getActiveVariants()
            );
            request.setAttribute(
                    "history",
                    stocktakingDAO.getRecentHistory()
            );
        } catch (SQLException e) {
            getServletContext().log("Could not load stocktaking data.", e);
            request.setAttribute(
                    "error",
                    "Không tải được dữ liệu kiểm kê."
            );
        }

        request.getRequestDispatcher(
                "/staff/warehouse/stocktaking.jsp"
        ).forward(request, response);
    }

    private Map<Integer, Integer> readActualQuantities(
            HttpServletRequest request) {

        String[] variantIDs = request.getParameterValues("variantId");
        String[] quantities =
                request.getParameterValues("actualQuantity");

        if (variantIDs == null || quantities == null
                || variantIDs.length != quantities.length) {
            throw new IllegalArgumentException(
                    "Danh sách biến thể kiểm kê không hợp lệ."
            );
        }

        if (variantIDs.length > 50) {
            throw new IllegalArgumentException(
                    "Mỗi lần kiểm kê tối đa 50 biến thể."
            );
        }

        Map<Integer, Integer> result = new LinkedHashMap<>();

        for (int i = 0; i < variantIDs.length; i++) {
            if (variantIDs[i] == null
                    || variantIDs[i].trim().isEmpty()
                    || quantities[i] == null
                    || quantities[i].trim().isEmpty()) {
                continue;
            }

            int variantID = Integer.parseInt(variantIDs[i]);
            int quantity = Integer.parseInt(quantities[i]);

            if (result.put(variantID, quantity) != null) {
                throw new IllegalArgumentException(
                        "Không gửi trùng biến thể trong một phiếu kiểm kê."
                );
            }
        }

        return result;
    }

    private Staff requireWarehouseStaff(
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

        if (!"Active".equalsIgnoreCase(staff.getStatus())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        String role = staff.getRole();

        if (!"Warehouse".equalsIgnoreCase(role)
                && !"Admin".equalsIgnoreCase(role)) {
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