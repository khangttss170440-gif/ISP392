-- Mark previously delivered COD orders as paid. New deliveries are handled by
-- WarehouseOrderDAO in the same transaction as the shipment/order updates.
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    UPDATE payment
    SET payment.PaymentStatus = 'Paid',
        payment.PaymentDate = COALESCE(payment.PaymentDate, SYSDATETIME())
    FROM dbo.Payment AS payment
    INNER JOIN dbo.SalesOrder AS salesOrder
        ON salesOrder.OrderID = payment.OrderID
    WHERE UPPER(LTRIM(RTRIM(payment.PaymentMethod))) IN ('COD', 'CASH ON DELIVERY')
      AND UPPER(LTRIM(RTRIM(payment.PaymentStatus))) = 'PENDING'
      AND UPPER(LTRIM(RTRIM(salesOrder.OrderStatus))) IN ('DELIVERED', 'COMPLETED')
      AND EXISTS (
          SELECT 1
          FROM dbo.Shipment AS shipment
          WHERE shipment.OrderID = salesOrder.OrderID
            AND UPPER(LTRIM(RTRIM(shipment.ShipmentStatus))) = 'DELIVERED'
      );

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
