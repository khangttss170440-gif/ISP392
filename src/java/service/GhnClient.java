package service;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import util.ShippingJson;

/** GHN staging only. Production needs a separate explicit rollout. */
public class GhnClient {
    private static final String BASE = "https://dev-online-gateway.ghn.vn/shiip/public-api/";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
    public static String config(String key) {
        String v=System.getenv(key); if(v==null || v.isBlank()) v=System.getProperty(key, "");
        return v.trim();
    }
    public static boolean configured() { return !config("GHN_TOKEN").isEmpty() && config("GHN_SHOP_ID").matches("[1-9][0-9]{0,9}"); }
    public static int shopId() {
        if(!configured()) throw new IllegalArgumentException("Chưa cấu hình GHN test. Lưu GHN_TOKEN, GHN_SHOP_ID rồi mở lại NetBeans/Tomcat.");
        return Integer.parseInt(config("GHN_SHOP_ID"));
    }
    public Object get(String path) { return call("GET",path,null); }
    public Object post(String path, Map<String,Object> body) { return call("POST",path,body); }
    public Object cached(String path, Map<String,Object> body, long ttlMillis) {
        String key=shopId()+":"+config("GHN_TOKEN")+":"+path+":"+ShippingJson.write(body);
        Cached c=CACHE.get(key); if(c!=null && c.until>System.currentTimeMillis()) return c.value;
        Object value=body==null?get(path):post(path,body);
        if(CACHE.size()>256) CACHE.clear(); CACHE.put(key,new Cached(value,System.currentTimeMillis()+ttlMillis)); return value;
    }
    private Object call(String method,String path,Map<String,Object> body) {
        int shop=shopId();
        if(path.startsWith("/") || path.contains(":") || path.contains("..")) throw new IllegalArgumentException("Invalid GHN path.");
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(BASE+path)).timeout(Duration.ofSeconds(8))
                .header("Token",config("GHN_TOKEN")).header("ShopId",Integer.toString(shop)).header("Content-Type","application/json");
        if("POST".equals(method)) b.POST(HttpRequest.BodyPublishers.ofString(ShippingJson.write(body),StandardCharsets.UTF_8)); else b.GET();
        try {
            // With a buffered body the request deadline covers receiving the response body too.
            HttpResponse<String> r=HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String json=r.body();
            if(json.length()>2_000_000) throw new IOException("Oversized GHN response.");
            if(r.statusCode()==401 || r.statusCode()==403)
                throw rejected(path,r.statusCode(),json);
            if(r.statusCode()>=500) throw new IllegalArgumentException("GHN test đang bận. Vui lòng thử lại sau.");
            Map<String,Object> result;
            try { result=ShippingJson.object(ShippingJson.read(json)); }
            catch(IllegalArgumentException invalidJson) { throw rejected(path,r.statusCode(),""); }
            if(r.statusCode()!=200 || !"200".equals(ShippingJson.string(result,"code")))
                throw rejected(path,r.statusCode(),json);
            return result.get("data");
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalArgumentException("Yêu cầu GHN đã bị gián đoạn.");
        } catch(IOException e) {
            throw new IllegalArgumentException("Không kết nối được GHN test hoặc quá thời gian chờ. Thử lại; nếu vừa tạo vận đơn, dùng lại đúng đơn này để đối soát.");
        }
    }
    private static IllegalArgumentException rejected(String path,int status,String json) {
        String endpoint=path.split("\\?",2)[0];
        String step;
        switch(endpoint) {
            case "master-data/province": step="lấy tỉnh/thành"; break;
            case "master-data/district": step="lấy quận/huyện"; break;
            case "master-data/ward": step="lấy phường/xã"; break;
            case "v2/shop/all": step="đọc cửa hàng lấy hàng"; break;
            case "v2/shipping-order/fee": step="tính phí giao hàng"; break;
            case "v2/shipping-order/create": step="tạo vận đơn"; break;
            case "v2/shipping-order/soc": step="đọc phí vận đơn"; break;
            default: step="tra cứu vận đơn";
        }
        String detail="";
        try {
            Map<String,Object> error=ShippingJson.object(ShippingJson.read(json));
            detail=ShippingJson.string(error,"message");
            if(detail.isBlank()) detail=ShippingJson.string(error,"code_message");
        } catch(IllegalArgumentException ignored) { }
        // Only the error message is shown, never response data, request payloads or headers.
        String token=config("GHN_TOKEN");
        if(!token.isEmpty()) detail=detail.replace(token,"[ẩn Token]");
        detail=detail.replaceAll("(?i)(token|authorization|api[_-]?key|secret)[\\\"']*\\s*[:=]\\s*[\\\"']?[^\\s,;\\\"}]+","$1: [ẩn]")
                .replaceAll("[\\p{Cntrl}<>]"," ").replaceAll("\\s+"," ").trim();
        if(detail.length()>400) detail=detail.substring(0,400)+"…";
        // The log contains no carrier message, token, address, phone or query parameters.
        java.util.logging.Logger.getLogger(GhnClient.class.getName()).warning("GHN request rejected: endpoint="+endpoint+", HTTP="+status);
        return new IllegalArgumentException("GHN lỗi ở bước "+step+" (HTTP "+status+"). "
                +(detail.isEmpty()?"Chưa có mô tả lỗi từ hãng; xem tên bước để đối chiếu cấu hình.":detail));
    }
    private static class Cached { final Object value; final long until; Cached(Object v,long u){value=v;until=u;} }
}
