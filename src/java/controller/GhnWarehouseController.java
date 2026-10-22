package controller;

import dao.GhnDeliveryDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import model.Staff;

@WebServlet("/staff/warehouse/ghn")
public class GhnWarehouseController extends HttpServlet {
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws IOException {
        req.setCharacterEncoding("UTF-8");res.setHeader("Cache-Control","no-store");
        HttpSession session=req.getSession(false);
        Object account=session==null?null:session.getAttribute("staff");
        if(!(account instanceof Staff) && session!=null) account=session.getAttribute("STAFF");
        if(!(account instanceof Staff)){res.sendRedirect(req.getContextPath()+"/login");return;}
        Staff staff=(Staff)account;
        if(!"Active".equalsIgnoreCase(staff.getStatus()) || !("Warehouse".equalsIgnoreCase(staff.getRole()) || "Admin".equalsIgnoreCase(staff.getRole()))) {
            res.sendError(403);return;
        }
        Object token=session.getAttribute("ghn.csrf");
        if(!(token instanceof String) || !token.equals(req.getParameter("csrfToken"))) {res.sendError(403);return;}
        int order=0;
        try {
            order=Integer.parseInt(req.getParameter("orderId"));if(order<1)throw new NumberFormatException();
            GhnDeliveryDAO dao=new GhnDeliveryDAO();
            if("create".equals(req.getParameter("action"))) {
                dao.book(order,number(req,"weight"),number(req,"length"),number(req,"width"),number(req,"height"));
                session.setAttribute("WAREHOUSE_MESSAGE","Đã tạo vận đơn GHN test. Kho có thể cập nhật trạng thái từ GHN.");
            } else if("sync".equals(req.getParameter("action"))) {
                session.setAttribute("WAREHOUSE_MESSAGE","Trạng thái GHN test: "+dao.sync(order));
            } else throw new IllegalArgumentException("Thao tác GHN không hợp lệ.");
        } catch(NumberFormatException e) {
            session.setAttribute("WAREHOUSE_ERROR","Mã đơn, cân nặng hoặc kích thước kiện không hợp lệ.");
        } catch(IllegalArgumentException e) {
            session.setAttribute("WAREHOUSE_ERROR",e.getMessage());
        } catch(SQLException e) {
            getServletContext().log("Could not persist GHN shipment for order "+order+" (SQLState "+e.getSQLState()+").");
            session.setAttribute("WAREHOUSE_ERROR","Chưa lưu được dữ liệu GHN. Nếu vừa tạo vận đơn, bấm cập nhật trạng thái để đối soát trước khi gửi lại.");
        }
        res.sendRedirect(req.getContextPath()+(order>0?"/staff/warehouse/order-detail?orderId="+order:"/staff/warehouse/orders"));
    }
    private int number(HttpServletRequest r,String key) {return Integer.parseInt(r.getParameter(key));}
}
