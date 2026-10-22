package dao;

import java.sql.*;
import java.util.*;
import model.OutfitItem;
import util.DBConnection;

/** One query for all color/size options; excludes inactive and unavailable inventory. */
public final class OutfitCatalogDAO {
    public List<OutfitItem> availableItems() throws SQLException {
        try(Connection c=DBConnection.getConnection()){return items(c,true);}
    }
    public List<OutfitItem> allItems() throws SQLException {
        try(Connection c=DBConnection.getConnection()){return items(c,false);}
    }
    List<OutfitItem> items(Connection c,boolean availableOnly) throws SQLException {
        String sql="SELECT p.ProductID,p.ProductName,c.CategoryName,p.Material,v.Color,v.Size,v.Price "
                +"FROM dbo.Product p JOIN dbo.ProductCategory c ON c.CategoryID=p.CategoryID "
                +"JOIN dbo.ProductVariant v ON v.ProductID=p.ProductID "
                +(availableOnly?"WHERE p.Status='Active' AND c.Status='Active' AND v.Status='Active' AND v.Quantity>0 AND v.Price>=0 ":"")
                +"ORDER BY p.ProductID,v.Color,v.Size,v.VariantID";
        Map<String,OutfitItem> result=new LinkedHashMap<>();
        try(PreparedStatement s=c.prepareStatement(sql)) {
            s.setQueryTimeout(8);
            try(ResultSet r=s.executeQuery()) {
                while(r.next()) {
                    OutfitItem candidate=new OutfitItem(r.getInt("ProductID"),r.getString("ProductName"),
                            r.getString("CategoryName"),r.getString("Material"),r.getString("Color"));
                    OutfitItem item=result.computeIfAbsent(candidate.getKey(),ignored->candidate);
                    item.addSize(r.getString("Size"),r.getBigDecimal("Price"));
                }
            }
        }
        return new ArrayList<>(result.values());
    }
}
