package service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import model.CustomerAddress;
import util.ShippingJson;

/** Matches only a unique address from GHN's district-based catalogue used by the current address book. */
public class GhnShippingService {
    private final GhnClient api = new GhnClient();
    public Map<String,Object> destination(CustomerAddress a) {
        if(a==null || a.getAddressLine()==null || a.getAddressLine().isBlank()) throw new IllegalArgumentException("Chọn địa chỉ giao hàng trước khi tính phí GHN.");
        Map<String,Object> p=match(api.cached("master-data/province",null,3_600_000),"ProvinceName",a.getProvince(),"tỉnh/thành");
        Map<String,Object> d=match(api.cached("master-data/district?province_id="+ShippingJson.number(p,"ProvinceID"),null,3_600_000),"DistrictName",a.getDistrict(),"quận/huyện");
        Map<String,Object> w=match(api.cached("master-data/ward?district_id="+ShippingJson.number(d,"DistrictID"),null,3_600_000),"WardName",a.getWard(),"phường/xã");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("to_district_id",ShippingJson.number(d,"DistrictID")); result.put("to_ward_code",ShippingJson.string(w,"WardCode"));
        result.put("to_province_name",ShippingJson.string(p,"ProvinceName")); result.put("to_district_name",ShippingJson.string(d,"DistrictName"));
        result.put("to_ward_name",ShippingJson.string(w,"WardName")); result.put("is_new_to_address",false);
        result.put("to_name",a.getRecipientName()); result.put("to_phone",a.getPhone()); result.put("to_address",a.getAddressLine());
        return result;
    }
    public BigDecimal fee(Map<String,Object> destination,int quantity) {
        int weight=weight(quantity);
        Map<String,Object> body=new LinkedHashMap<>();
        Map<String,Object> shop=shop();
        int fromDistrict=0;
        try { fromDistrict=ShippingJson.number(shop,"district_id"); }
        catch(IllegalArgumentException | ArithmeticException ignored) { }
        String fromWard=ShippingJson.string(shop,"ward_code");
        // These fields are optional. The shop catalogue may omit legacy district codes,
        // e.g. for a newer address profile. In that case GHN resolves its own shop address.
        // Never substitute made-up IDs or assume the recipient is also the pickup address.
        if(fromDistrict>0 && !fromWard.isBlank() && !"0".equals(fromWard)) {
            body.put("from_district_id",fromDistrict);body.put("from_ward_code",fromWard);
        }
        body.put("to_district_id",destination.get("to_district_id")); body.put("to_ward_code",destination.get("to_ward_code"));
        body.put("service_type_id",2); body.put("weight",weight); body.put("length",30);body.put("width",20);body.put("height",10);
        Map<String,Object> data=ShippingJson.object(api.cached("v2/shipping-order/fee",body,120_000));
        int fee=ShippingJson.number(data,"total");
        if(fee<0 || fee>5_000_000) throw new IllegalArgumentException("GHN trả về phí không hợp lệ.");
        return BigDecimal.valueOf(fee);
    }
    private Map<String,Object> shop() {
        Map<String,Object> data=ShippingJson.object(api.cached("v2/shop/all?offset=0&limit=100",null,300_000));
        for(Object value:ShippingJson.array(data.get("shops"))) {
            Map<String,Object> shop=ShippingJson.object(value);
            if(ShippingJson.number(shop,"_id")==GhnClient.shopId()) return shop;
        }
        throw new IllegalArgumentException("Không tìm thấy GHN_SHOP_ID trong danh sách cửa hàng của Token test. Kiểm tra mã tại Quản lý cửa hàng GHN (khác mã tài khoản). Nếu tài khoản có hơn 100 shop, cần bổ sung phân trang.");
    }
    public static int weight(int quantity) {
        if(quantity<1 || quantity>39) throw new IllegalArgumentException("GHN hiện hỗ trợ kiện dưới 20 kg; vui lòng chia đơn hoặc liên hệ shop.");
        return quantity*500;
    }
    private static Map<String,Object> match(Object data,String key,String name,String level) {
        String wanted=normalize(name);
        if(wanted.isEmpty()) throw new IllegalArgumentException("Cần nhập "+level+" để đối chiếu địa chỉ GHN.");
        List<Map<String,Object>> matches=new ArrayList<>();
        for(Object value:ShippingJson.array(data)) {
            Map<String,Object> row=ShippingJson.object(value);
            boolean found=normalize(ShippingJson.string(row,key)).equals(wanted);
            Object aliases=row.get("NameExtension");
            if(aliases instanceof List) for(Object alias:(List<?>)aliases) found|=normalize(String.valueOf(alias)).equals(wanted);
            if(found) matches.add(row);
        }
        if(matches.size()!=1) throw new IllegalArgumentException("Không xác định duy nhất "+level+" '"+name+"' trong danh mục GHN. Sửa Sổ địa chỉ theo tên phường/xã, quận/huyện và tỉnh/thành tương ứng.");
        return matches.get(0);
    }
    static String normalize(String value) {
        String s=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}","")
                .replace('đ','d').replace('Đ','D').toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]"," ").trim().replaceAll("\\s+"," ");
        if(s.matches("(TP ?HCM|HCM|HCMC|SAI ?GON|TP HO CHI MINH|THANH PHO HO CHI MINH|TP HO CHI MINH CITY)")) return "HO CHI MINH";
        s=s.replaceFirst("^(THANH PHO|TINH|TP|QUAN|HUYEN|THI XA|THI TRAN|PHUONG|XA)\\s+","");
        return s.replaceFirst("^0+([0-9])","$1");
    }
}
