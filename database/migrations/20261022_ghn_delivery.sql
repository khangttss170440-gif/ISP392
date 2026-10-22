-- SQL Server: immutable recipient snapshot and recoverable GHN staging booking.
-- Run once in the application's MenWearStore database before enabling GHN checkout.
IF OBJECT_ID(N'dbo.GhnDelivery', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.GhnDelivery (
        OrderID int NOT NULL CONSTRAINT PK_GhnDelivery PRIMARY KEY,
        ShopID int NOT NULL,
        Environment varchar(10) NOT NULL CONSTRAINT DF_GhnDelivery_Environment DEFAULT 'staging',
        ClientOrderCode varchar(50) NOT NULL CONSTRAINT UQ_GhnDelivery_ClientOrderCode UNIQUE,
        RecipientJson nvarchar(max) NOT NULL,
        TrackingNumber varchar(100) NULL,
        CarrierStatus varchar(50) NULL,
        BookingState varchar(20) NOT NULL CONSTRAINT DF_GhnDelivery_State DEFAULT 'Ready',
        BookingPayload nvarchar(max) NULL,
        CarrierFee decimal(18,2) NULL,
        UpdatedAt datetime2 NOT NULL CONSTRAINT DF_GhnDelivery_UpdatedAt DEFAULT SYSDATETIME(),
        CONSTRAINT FK_GhnDelivery_Order FOREIGN KEY (OrderID) REFERENCES dbo.SalesOrder(OrderID),
        CONSTRAINT CK_GhnDelivery_Environment CHECK (Environment = 'staging'),
        CONSTRAINT CK_GhnDelivery_State CHECK (BookingState IN ('Ready','Booking','Unknown','Booked'))
    );
END;

-- A local cancellation must not leave a live carrier order collecting COD.
IF OBJECT_ID(N'dbo.TR_GhnDelivery_CancellationGuard', N'TR') IS NULL
    EXEC(N'CREATE TRIGGER dbo.TR_GhnDelivery_CancellationGuard ON dbo.SalesOrder AFTER UPDATE AS BEGIN SET NOCOUNT ON; END');
EXEC(N'ALTER TRIGGER dbo.TR_GhnDelivery_CancellationGuard ON dbo.SalesOrder AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON d.OrderID=i.OrderID
        JOIN dbo.GhnDelivery g ON g.OrderID=i.OrderID
        WHERE i.OrderStatus=''Cancelled'' AND d.OrderStatus<>''Cancelled''
          AND g.BookingState<>''Ready'' AND ISNULL(g.CarrierStatus,'''')<>''cancel'')
    BEGIN
        ;THROW 51040, N''Đơn đã gửi GHN. Hủy vận đơn trên GHN test và đồng bộ trạng thái trước khi hủy đơn tại shop.'', 1;
    END
END');
