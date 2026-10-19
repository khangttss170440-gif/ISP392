IF OBJECT_ID(N'dbo.CustomerNotification', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.CustomerNotification (
        NotificationID INT IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_CustomerNotification PRIMARY KEY,
        CustomerID INT NOT NULL,
        OrderID INT NULL,
        Title NVARCHAR(200) NOT NULL,
        Message NVARCHAR(2000) NOT NULL,
        CreatedAt DATETIME2(0) NOT NULL
            CONSTRAINT DF_CustomerNotification_CreatedAt DEFAULT SYSDATETIME(),
        ReadAt DATETIME2(0) NULL,
        CONSTRAINT FK_CustomerNotification_Customer
            FOREIGN KEY (CustomerID) REFERENCES dbo.Customer(CustomerID),
        CONSTRAINT FK_CustomerNotification_SalesOrder
            FOREIGN KEY (OrderID) REFERENCES dbo.SalesOrder(OrderID)
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.CustomerNotification')
      AND name = N'IX_CustomerNotification_Unread'
)
BEGIN
    CREATE INDEX IX_CustomerNotification_Unread
        ON dbo.CustomerNotification(CustomerID, ReadAt, CreatedAt DESC, NotificationID DESC);
END;
GO
