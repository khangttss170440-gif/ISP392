package controller;

import dao.PromotionDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/promotions")
public class PromotionController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            request.setAttribute("promotions", new PromotionDAO().getAvailablePromotions());
        } catch (SQLException e) {
            getServletContext().log("Could not load available promotions.", e);
            request.setAttribute("promotions", Collections.emptyList());
            request.setAttribute("error", "Chưa tải được ưu đãi. Vui lòng thử lại sau.");
        }
        request.getRequestDispatcher("/promotions.jsp").forward(request, response);
    }
}
