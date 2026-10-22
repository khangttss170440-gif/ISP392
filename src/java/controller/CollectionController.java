package controller;

import dao.OutfitCatalogDAO;
import dao.OutfitCollectionDAO;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import model.OutfitItem;
import service.OutfitRecommendationService;

@WebServlet("/collections")
public class CollectionController extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws ServletException,IOException {
        response.setHeader("Cache-Control","no-store");
        String style=parameter(request,"style","all");
        String source=parameter(request,"source","curated");
        BigDecimal budget=null;Integer anchor=null;
        try {
            if(!OutfitRecommendationService.validStyle(style))throw new IllegalArgumentException();
            if(!Set.of("curated","suggested").contains(source))throw new IllegalArgumentException();
            String value=parameter(request,"budget","");
            if(!value.isEmpty()) {
                if(!value.matches("[1-9][0-9]{0,8}"))throw new IllegalArgumentException();
                budget=new BigDecimal(value);
                if(budget.compareTo(new BigDecimal("50000000"))>0)throw new IllegalArgumentException();
            }
            String product=parameter(request,"productId","");
            if(!product.isEmpty()) {
                if(!product.matches("[1-9][0-9]{0,9}"))throw new IllegalArgumentException();
                anchor=Integer.valueOf(product);
            }
        } catch(IllegalArgumentException invalid) {
            response.sendError(400,"Bộ lọc bộ sưu tập không hợp lệ. Ngân sách tối đa 50.000.000₫.");return;
        }
        request.setAttribute("selectedStyle",style);
        request.setAttribute("selectedSource",source);
        request.setAttribute("collectionPage",Boolean.TRUE);
        request.setAttribute("selectedBudget",budget);
        request.setAttribute("selectedProductId",anchor);
        request.setAttribute("outfitStyles",OutfitRecommendationService.Style.values());
        try {
            List<OutfitItem> available=new OutfitCatalogDAO().availableItems();
            Map<Integer,OutfitItem> products=new LinkedHashMap<>();
            available.stream().sorted(Comparator.comparing(OutfitItem::getName).thenComparingInt(OutfitItem::getProductId))
                    .forEach(i->products.putIfAbsent(i.getProductId(),i));
            request.setAttribute("outfitProducts",products.values());
            boolean unavailable=anchor!=null && !products.containsKey(anchor);
            request.setAttribute("selectedProductUnavailable",unavailable);
            request.setAttribute("outfits",unavailable?Collections.emptyList():"curated".equals(source)
                    ?new OutfitCollectionDAO().published(available,style,budget,anchor)
                    :new OutfitRecommendationService().recommend(available,style,budget,anchor));
            request.setAttribute("availableProductCount",products.size());
            request.setAttribute("hasOutfitBasics",available.stream().anyMatch(i->i.getRole()==OutfitItem.Role.TOP)
                    && available.stream().anyMatch(i->i.getRole()==OutfitItem.Role.BOTTOM));
        } catch(SQLException error) {
            getServletContext().log("Could not load outfit catalog (SQLState "+error.getSQLState()+").");
            response.setStatus(503);
            request.setAttribute("collectionsUnavailable",true);
            request.setAttribute("outfits",Collections.emptyList());
            request.setAttribute("outfitProducts",Collections.emptyList());
        }
        request.getRequestDispatcher("/WEB-INF/views/collections.jsp").forward(request,response);
    }
    private String parameter(HttpServletRequest request,String name,String fallback) {
        String value=request.getParameter(name);
        return value==null || value.isBlank()?fallback:value.trim();
    }
}
