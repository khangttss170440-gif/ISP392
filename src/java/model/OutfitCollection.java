package model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** A staff-curated outfit. Prices and stock are resolved from the live catalog. */
public final class OutfitCollection {
    private int id;
    private String title="",description="",stylingNotes="",style="everyday",status="Draft",revision="",author="";
    private Timestamp updatedAt;
    private final List<Piece> pieces=new ArrayList<>();
    public int getId(){return id;}
    public void setId(int value){id=value;}
    public String getTitle(){return title;}
    public void setTitle(String value){title=value;}
    public String getDescription(){return description;}
    public void setDescription(String value){description=value;}
    public String getStylingNotes(){return stylingNotes;}
    public void setStylingNotes(String value){stylingNotes=value;}
    public String getStyle(){return style;}
    public void setStyle(String value){style=value;}
    public String getStatus(){return status;}
    public void setStatus(String value){status=value;}
    public String getRevision(){return revision;}
    public void setRevision(String value){revision=value;}
    public String getAuthor(){return author;}
    public void setAuthor(String value){author=value;}
    public Timestamp getUpdatedAt(){return updatedAt;}
    public void setUpdatedAt(Timestamp value){updatedAt=value;}
    public List<Piece> getPieces(){return pieces;}
    public String getStatusLabel(){return "Published".equals(status)?"Công khai":"Archived".equals(status)?"Đã ẩn":"Bản nháp";}
    public static final class Piece {
        private final int productId;
        private final String color,name;
        private final OutfitItem.Role role;
        public Piece(int productId,String color,OutfitItem.Role role,String name){
            this.productId=productId;this.color=color;this.role=role;this.name=name;
        }
        public int getProductId(){return productId;}
        public String getColor(){return color;}
        public OutfitItem.Role getRole(){return role;}
        public String getName(){return name;}
        public String getKey(){return productId+":"+color;}
    }
}
