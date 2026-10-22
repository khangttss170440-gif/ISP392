package dao;

import java.sql.*;
import java.time.Instant;
import service.GhnClient;
import util.DBConnection;

/** Persist notifications as a durable wake-up, then verify actual status through GHN's API. */
public final class GhnWebhookDAO {
    public void receive(String code,String clientCode,String type,Instant time,String eventKey) throws SQLException {
        try(Connection c=DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                Integer order=null;
                String lookup="SELECT OrderID FROM dbo.GhnDelivery WITH(UPDLOCK,ROWLOCK) WHERE ShopID=? AND Environment='staging' "
                        +"AND ClientOrderCode=? AND (TrackingNumber IS NULL OR TrackingNumber=?) AND BookingState<>'Ready'";
                try(PreparedStatement s=c.prepareStatement(lookup)) {
                    s.setQueryTimeout(3);s.setInt(1,GhnClient.shopId());s.setString(2,clientCode);s.setString(3,code);
                    try(ResultSet r=s.executeQuery()){if(r.next())order=r.getInt(1);}
                }
                // GHN callbacks are per client, so callbacks for shops/orders outside this app are acknowledged.
                if(order==null){c.commit();return;}
                int inserted;
                try(PreparedStatement s=c.prepareStatement("INSERT dbo.GhnWebhookReceipt(EventKey,OrderID,EventType,EventTime) SELECT ?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM dbo.GhnWebhookReceipt WHERE EventKey=?)")) {
                    s.setQueryTimeout(3);s.setString(1,eventKey);s.setInt(2,order);s.setString(3,type);
                    s.setTimestamp(4,Timestamp.valueOf(java.time.LocalDateTime.ofInstant(time,java.time.ZoneOffset.UTC)));s.setString(5,eventKey);
                    inserted=s.executeUpdate();
                }
                if(inserted==1)try(PreparedStatement s=c.prepareStatement("UPDATE dbo.GhnDelivery SET WebhookGeneration=WebhookGeneration+1,NextSyncAt=CASE WHEN LastSyncedAt>DATEADD(second,-5,SYSUTCDATETIME()) THEN DATEADD(second,5,LastSyncedAt) ELSE SYSUTCDATETIME() END WHERE OrderID=?")) {
                    s.setQueryTimeout(3);s.setInt(1,order);s.executeUpdate();
                }
                c.commit();
            } catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
}
