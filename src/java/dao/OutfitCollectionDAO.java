package dao;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import model.OutfitCollection;
import model.OutfitCollection.Piece;
import model.OutfitItem;
import model.OutfitItem.Role;
import model.OutfitSet;
import service.OutfitRecommendationService;
import util.DBConnection;

public final class OutfitCollectionDAO {
    private static PreparedStatement statement(Connection c,String sql) throws SQLException {
        PreparedStatement s=c.prepareStatement(sql);s.setQueryTimeout(10);return s;
    }
    public String activeRole(int staffId) throws SQLException {
        try(Connection c=DBConnection.getConnection()){return activeRole(c,staffId);}
    }
    private String activeRole(Connection c,int staffId) throws SQLException {
        try(PreparedStatement s=statement(c,"SELECT Role FROM dbo.Staff WHERE StaffID=? AND Status='Active' AND Role IN ('Admin','Warehouse')")) {
            s.setInt(1,staffId);
            try(ResultSet r=s.executeQuery()){return r.next()?r.getString(1):null;}
        }
    }
    public List<OutfitCollection> list(boolean publishedOnly) throws SQLException {
        return load(publishedOnly,null);
    }
    public OutfitCollection find(int id) throws SQLException {
        List<OutfitCollection> rows=load(false,id);return rows.isEmpty()?null:rows.get(0);
    }
    private List<OutfitCollection> load(boolean publishedOnly,Integer id) throws SQLException {
        String sql="SELECT o.*,s.FullName AS EditorName,i.ProductID,i.Color,i.Role AS ItemRole,p.ProductName "
                +"FROM dbo.OutfitCollection o JOIN dbo.Staff s ON s.StaffID=o.UpdatedBy "
                +"LEFT JOIN dbo.OutfitCollectionItem i ON i.CollectionID=o.CollectionID "
                +"LEFT JOIN dbo.Product p ON p.ProductID=i.ProductID "
                +(id!=null?"WHERE o.CollectionID=? ":publishedOnly?"WHERE o.Status='Published' ":"")
                +"ORDER BY o.UpdatedAt DESC,o.CollectionID DESC,i.SortOrder";
        Map<Integer,OutfitCollection> result=new LinkedHashMap<>();
        try(Connection c=DBConnection.getConnection();PreparedStatement s=statement(c,sql)) {
            if(id!=null)s.setInt(1,id);
            try(ResultSet r=s.executeQuery()) {
                while(r.next()) {
                    int key=r.getInt("CollectionID");OutfitCollection outfit=result.get(key);
                    if(outfit==null) {
                        outfit=new OutfitCollection();outfit.setId(key);outfit.setTitle(r.getString("Title"));
                        outfit.setDescription(r.getString("Description"));outfit.setStylingNotes(r.getString("StylingNotes"));
                        outfit.setStyle(r.getString("Style"));outfit.setStatus(r.getString("Status"));
                        outfit.setRevision(r.getString("Revision"));outfit.setUpdatedAt(r.getTimestamp("UpdatedAt"));
                        outfit.setAuthor(r.getString("EditorName"));result.put(key,outfit);
                    }
                    int productId=r.getInt("ProductID");
                    if(!r.wasNull())outfit.getPieces().add(new Piece(productId,r.getString("Color"),Role.valueOf(r.getString("ItemRole")),r.getString("ProductName")));
                }
            }
        }
        return new ArrayList<>(result.values());
    }
    public List<OutfitSet> published(List<OutfitItem> available,String style,BigDecimal budget,Integer anchor) throws SQLException {
        Map<String,OutfitItem> catalog=available.stream().collect(Collectors.toMap(OutfitItem::getKey,i->i));
        List<OutfitSet> result=new ArrayList<>();
        for(OutfitCollection row:list(true)) {
            if(!"all".equals(style) && !style.equals(row.getStyle()))continue;
            if(anchor!=null && row.getPieces().stream().noneMatch(p->p.getProductId()==anchor))continue;
            if(row.getPieces().size()<2 || row.getPieces().stream().anyMatch(p->!catalog.containsKey(p.getKey())))continue;
            if(row.getPieces().stream().noneMatch(p->p.getRole()==Role.TOP)
                    || row.getPieces().stream().noneMatch(p->p.getRole()==Role.BOTTOM))continue;
            List<OutfitItem> items=row.getPieces().stream().map(p->catalog.get(p.getKey()).asRole(p.getRole())).collect(Collectors.toList());
            List<String> notes=row.getStylingNotes().isBlank()?List.of("Bộ phối được cửa hàng lựa chọn theo dịp sử dụng.")
                    :Arrays.stream(row.getStylingNotes().split("\\R")).map(String::trim).filter(v->!v.isEmpty()).collect(Collectors.toList());
            OutfitSet set=new OutfitSet(row.getStyle(),row.getTitle(),row.getDescription(),items,notes,"");
            if(budget==null || set.getFromPrice().compareTo(budget)<=0)result.add(set);
        }
        return result;
    }
    public int save(OutfitCollection outfit,int staffId) throws SQLException {
        validate(outfit);
        try(Connection c=DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                if(activeRole(c,staffId)==null)throw new SecurityException("Bạn không còn quyền quản lý bộ sưu tập.");
                OutfitCatalogDAO inventory=new OutfitCatalogDAO();
                Set<String> all=inventory.items(c,false).stream().map(OutfitItem::getKey).collect(Collectors.toSet());
                if(outfit.getPieces().stream().anyMatch(p->!all.contains(p.getKey())))
                    throw new IllegalArgumentException("Một sản phẩm hoặc màu không còn tồn tại. Hãy chọn lại.");
                if("Published".equals(outfit.getStatus())) {
                    Set<String> available=inventory.items(c,true).stream().map(OutfitItem::getKey).collect(Collectors.toSet());
                    if(outfit.getPieces().stream().anyMatch(p->!available.contains(p.getKey())))
                        throw new IllegalArgumentException("Chỉ công khai khi tất cả sản phẩm và màu đã chọn đang bán, còn hàng. Bạn có thể lưu nháp.");
                }
                int id=outfit.getId();
                if(id==0) {
                    try(PreparedStatement s=statement(c,"INSERT dbo.OutfitCollection(Title,Description,StylingNotes,Style,Status,CreatedBy,UpdatedBy) OUTPUT INSERTED.CollectionID VALUES(?,?,?,?,?,?,?)")) {
                        fields(s,outfit,staffId);s.setInt(7,staffId);
                        try(ResultSet r=s.executeQuery()){r.next();id=r.getInt(1);}
                    }
                } else {
                    try(PreparedStatement s=statement(c,"UPDATE dbo.OutfitCollection SET Title=?,Description=?,StylingNotes=?,Style=?,Status=?,UpdatedBy=?,UpdatedAt=SYSUTCDATETIME(),Revision=NEWID() WHERE CollectionID=? AND Revision=CONVERT(uniqueidentifier,?)")) {
                        fields(s,outfit,staffId);s.setInt(7,id);s.setString(8,outfit.getRevision());
                        if(s.executeUpdate()!=1)throw new IllegalArgumentException("Bộ sưu tập đã được người khác sửa hoặc không còn tồn tại. Mở lại trang trước khi lưu.");
                    }
                    try(PreparedStatement s=statement(c,"DELETE dbo.OutfitCollectionItem WHERE CollectionID=?")){s.setInt(1,id);s.executeUpdate();}
                }
                try(PreparedStatement s=statement(c,"INSERT dbo.OutfitCollectionItem(CollectionID,ProductID,Color,Role,SortOrder) VALUES(?,?,?,?,?)")) {
                    int index=0;
                    for(Piece p:outfit.getPieces()) {
                        s.setInt(1,id);s.setInt(2,p.getProductId());s.setString(3,p.getColor());s.setString(4,p.getRole().name());s.setInt(5,index++);s.addBatch();
                    }
                    s.executeBatch();
                }
                c.commit();return id;
            } catch(SQLException|RuntimeException error) {c.rollback();throw error;}
        }
    }
    private void fields(PreparedStatement s,OutfitCollection outfit,int staffId) throws SQLException {
        s.setString(1,outfit.getTitle());s.setString(2,outfit.getDescription());s.setString(3,outfit.getStylingNotes());
        s.setString(4,outfit.getStyle());s.setString(5,outfit.getStatus());s.setInt(6,staffId);
    }
    private void validate(OutfitCollection o) {
        if(o.getId()<0 || o.getTitle().isBlank() || o.getTitle().length()>120
                || o.getDescription().length()>1000 || o.getStylingNotes().length()>1000)
            throw new IllegalArgumentException("Tên cần có 1–120 ký tự; mô tả và hướng dẫn phối tối đa 1.000 ký tự mỗi phần.");
        if("all".equals(o.getStyle()) || !OutfitRecommendationService.validStyle(o.getStyle())
                || !Set.of("Draft","Published","Archived").contains(o.getStatus()))
            throw new IllegalArgumentException("Dịp sử dụng hoặc trạng thái không hợp lệ.");
        if(o.getId()>0)try{UUID.fromString(o.getRevision());}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Phiên bản bộ sưu tập không hợp lệ. Mở lại trang.");}
        Set<Integer> products=new HashSet<>();
        if(o.getPieces().size()>6)throw new IllegalArgumentException("Mỗi bộ sưu tập tối đa 6 món.");
        for(Piece p:o.getPieces()) {
            if(p.getProductId()<=0 || p.getColor().isBlank() || p.getColor().length()>100 || p.getRole()==Role.UNKNOWN)
                throw new IllegalArgumentException("Sản phẩm, màu hoặc vai trò phối không hợp lệ.");
            if(!products.add(p.getProductId()))throw new IllegalArgumentException("Mỗi sản phẩm chỉ chọn một lần trong bộ sưu tập.");
        }
        if("Published".equals(o.getStatus()) && (o.getPieces().stream().noneMatch(p->p.getRole()==Role.TOP)
                || o.getPieces().stream().noneMatch(p->p.getRole()==Role.BOTTOM)))
            throw new IllegalArgumentException("Bộ công khai cần có ít nhất một áo và một quần. Hãy chọn vai trò cho từng món.");
    }
}
