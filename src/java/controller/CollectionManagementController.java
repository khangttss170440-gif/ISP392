package controller;

import dao.OutfitCatalogDAO;
import dao.OutfitCollectionDAO;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import model.OutfitCollection;
import model.OutfitCollection.Piece;
import model.OutfitItem;
import model.Staff;
import service.OutfitRecommendationService;

@WebServlet(urlPatterns={"/admin/collections","/admin/collection-form","/staff/warehouse/collections","/staff/warehouse/collection-form"})
public class CollectionManagementController extends HttpServlet {
    private static final String CSRF="collections.csrf",MESSAGE="collections.message";
    private final OutfitCollectionDAO collections=new OutfitCollectionDAO();
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws ServletException,IOException {
        Staff staff=access(request,response);if(staff==null)return;
        prepare(request,staff);
        try {
            if(request.getServletPath().endsWith("collection-form")) {
                int id=id(request.getParameter("id"));
                OutfitCollection draft=id==0?new OutfitCollection():collections.find(id);
                if(draft==null){response.sendError(404);return;}
                form(request,response,draft);
            } else {
                request.setAttribute("managedCollections",collections.list(false));
                request.setAttribute("availableKeys",new OutfitCatalogDAO().availableItems().stream().map(OutfitItem::getKey).collect(Collectors.toSet()));
                request.setAttribute("message",request.getSession().getAttribute(MESSAGE));request.getSession().removeAttribute(MESSAGE);
                request.getRequestDispatcher("/WEB-INF/views/collection-list.jsp").forward(request,response);
            }
        } catch(IllegalArgumentException error){response.sendError(400,"Mã bộ sưu tập không hợp lệ.");}
        catch(SQLException error){unavailable(response,error);}
    }
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response) throws ServletException,IOException {
        request.setCharacterEncoding("UTF-8");
        Staff staff=access(request,response);if(staff==null)return;
        if(!request.getServletPath().endsWith("collection-form")){response.sendError(405);return;}
        Object token=request.getSession().getAttribute(CSRF);
        if(!(token instanceof String) || !token.equals(request.getParameter("csrfToken"))){response.sendError(403,"Phiên làm việc không hợp lệ. Tải lại trang rồi thử lại.");return;}
        prepare(request,staff);
        OutfitCollection draft=new OutfitCollection();
        try {
            draft.setId(id(request.getParameter("id")));
            draft.setRevision(value(request,"revision"));draft.setTitle(value(request,"title"));
            draft.setDescription(value(request,"description"));draft.setStylingNotes(value(request,"stylingNotes"));
            draft.setStyle(value(request,"style"));draft.setStatus(value(request,"status"));
            String[] pieces=request.getParameterValues("piece"),roles=request.getParameterValues("role");
            if(pieces==null || roles==null || pieces.length!=roles.length || pieces.length>6)
                throw new IllegalArgumentException("Danh sách món phối không hợp lệ. Tải lại trang.");
            for(int i=0;i<pieces.length;i++) {
                if(pieces[i].isBlank())continue;
                String[] parts=pieces[i].split("\\|",2);
                if(parts.length!=2)throw new IllegalArgumentException("Sản phẩm không hợp lệ.");
                OutfitItem.Role role;
                try{role=OutfitItem.Role.valueOf(roles[i]);}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Hãy chọn vai trò cho từng món.");}
                draft.getPieces().add(new Piece(id(parts[0]),URLDecoder.decode(parts[1],StandardCharsets.UTF_8),role,""));
            }
            collections.save(draft,staff.getStaffID());
            request.getSession().setAttribute(MESSAGE,"Đã lưu bộ sưu tập. Trạng thái: "+draft.getStatusLabel()+".");
            response.sendRedirect(request.getContextPath()+base(staff)+"/collections");
        } catch(IllegalArgumentException error) {
            response.setStatus(400);request.setAttribute("error",error.getMessage());
            try{form(request,response,draft);}catch(SQLException ex){unavailable(response,ex);}
        } catch(SecurityException error){response.sendError(403);}
        catch(SQLException error){
            getServletContext().log("Could not save collection (SQLState "+error.getSQLState()+").");
            response.setStatus(503);request.setAttribute("error","Chưa lưu được bộ sưu tập. Dữ liệu bạn nhập vẫn ở đây; hãy thử lại sau.");
            try{form(request,response,draft);}catch(SQLException ex){unavailable(response,ex);}
        }
    }
    private void form(HttpServletRequest request,HttpServletResponse response,OutfitCollection draft) throws SQLException,ServletException,IOException {
        request.setAttribute("collectionDraft",draft);
        request.setAttribute("catalogOptions",new OutfitCatalogDAO().allItems());
        request.setAttribute("availableKeys",new OutfitCatalogDAO().availableItems().stream().map(OutfitItem::getKey).collect(Collectors.toSet()));
        request.setAttribute("outfitStyles",OutfitRecommendationService.Style.values());
        request.getRequestDispatcher("/WEB-INF/views/collection-form.jsp").forward(request,response);
    }
    private Staff access(HttpServletRequest request,HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control","no-store");HttpSession session=request.getSession(false);
        Object value=session==null?null:session.getAttribute("staff");
        if(!(value instanceof Staff) && session!=null)value=session.getAttribute("STAFF");
        if(!(value instanceof Staff)){response.sendRedirect(request.getContextPath()+"/login");return null;}
        Staff staff=(Staff)value;
        try {
            String role=collections.activeRole(staff.getStaffID());
            if(role==null){response.sendError(403);return null;}
            Staff current=new Staff();current.setStaffID(staff.getStaffID());current.setRole(role);return current;
        } catch(SQLException error){unavailable(response,error);return null;}
    }
    private void prepare(HttpServletRequest request,Staff staff) {
        HttpSession session=request.getSession();
        synchronized(session){if(session.getAttribute(CSRF)==null)session.setAttribute(CSRF,UUID.randomUUID().toString());}
        request.setAttribute("csrfToken",session.getAttribute(CSRF));
        request.setAttribute("warehouseMode","Warehouse".equalsIgnoreCase(staff.getRole()));
        request.setAttribute("collectionBase",request.getContextPath()+base(staff));
    }
    private String base(Staff staff){return "Warehouse".equalsIgnoreCase(staff.getRole())?"/staff/warehouse":"/admin";}
    private String value(HttpServletRequest request,String name){String v=request.getParameter(name);return v==null?"":v.trim();}
    private int id(String value){
        if(value==null || value.isBlank() || "0".equals(value))return 0;
        if(!value.matches("[1-9][0-9]{0,9}"))throw new IllegalArgumentException("Mã không hợp lệ.");
        return Integer.parseInt(value);
    }
    private void unavailable(HttpServletResponse response,SQLException error) throws IOException {
        getServletContext().log("Could not load collection manager (SQLState "+error.getSQLState()+").");
        response.sendError(503,"Chưa truy cập được bộ sưu tập. Kiểm tra kết nối và migration database rồi thử lại.");
    }
}
