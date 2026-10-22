package controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import service.GhnClient;
import util.ShippingJson;

@WebServlet("/webhooks/ghn")
public final class GhnWebhookController extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res) throws IOException {
        reply(res,200,"GHN webhook endpoint. Send authenticated POST JSON.");
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws IOException {
        String secret=GhnClient.config("GHN_WEBHOOK_SECRET");
        if(secret.length()<32 || !GhnClient.configured()){reply(res,503,"Webhook configuration unavailable.");return;}
        String supplied=req.getHeader("X-GHN-Webhook-Secret");
        if(supplied==null || supplied.length()>512 || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
            reply(res,401,"Invalid webhook credential.");return;
        }
        String contentType=req.getContentType();
        if(contentType==null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")){reply(res,415,"Expected application/json.");return;}
        if(req.getContentLengthLong()>65_536){reply(res,413,"Payload too large.");return;}
        byte[] bytes=req.getInputStream().readNBytes(65_537);
        if(bytes.length>65_536){reply(res,413,"Payload too large.");return;}
        try {
            Map<String,Object> data=ShippingJson.object(ShippingJson.read(new String(bytes,StandardCharsets.UTF_8)));
            int shop=ShippingJson.number(data,"ShopID");
            if(shop!=GhnClient.shopId()){reply(res,200,"Ignored unrelated shop.");return;}
            String code=ShippingJson.string(data,"OrderCode"),client=ShippingJson.string(data,"ClientOrderCode"),type=ShippingJson.string(data,"Type");
            if(!code.matches("[A-Za-z0-9_-]{1,100}") || !client.matches("MW-[A-Za-z0-9-]{1,47}")) {reply(res,200,"Ignored unrelated order.");return;}
            if(!Set.of("create","switch_status","update_weight","update_cod","update_fee","update_payment_type","cod","update_partial_return").contains(type)) {
                reply(res,200,"Ignored unsupported event.");return;
            }
            Instant time=Instant.parse(ShippingJson.string(data,"Time"));
            byte[] digest=MessageDigest.getInstance("SHA-256").digest((code+"|"+type+"|"+time).getBytes(StandardCharsets.UTF_8));
            String eventKey=java.util.HexFormat.of().formatHex(digest);
            new dao.GhnWebhookDAO().receive(code,client,type,time,eventKey);
            // Status, COD and fees from the webhook are never written directly to business tables.
            reply(res,200,"Received. Authoritative carrier synchronization queued.");
        } catch(IllegalArgumentException|DateTimeParseException e){reply(res,400,"Invalid callback fields.");}
        catch(SQLException e) {
            getServletContext().log("GHN webhook storage unavailable (SQLState "+e.getSQLState()+").");
            reply(res,503,"Temporarily unavailable. Please retry.");
        } catch(java.security.NoSuchAlgorithmException impossible) {reply(res,500,"Hash service unavailable.");}
    }
    private void reply(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");response.setHeader("Cache-Control","no-store");
        response.getWriter().write(ShippingJson.write(Collections.singletonMap("message",message)));
    }
}
