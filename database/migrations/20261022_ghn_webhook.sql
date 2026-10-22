-- Run after the GHN delivery and auto-sync migrations. Receipts contain no recipient/token data.
IF OBJECT_ID('dbo.GhnDelivery','U') IS NULL
    THROW 51042, 'Install GHN delivery migrations first.', 1;
IF COL_LENGTH('dbo.GhnDelivery','WebhookGeneration') IS NULL
    ALTER TABLE dbo.GhnDelivery ADD WebhookGeneration bigint NOT NULL CONSTRAINT DF_GhnDelivery_WebhookGeneration DEFAULT 0 WITH VALUES;
IF COL_LENGTH('dbo.GhnDelivery','LastWebhookGenerationSynced') IS NULL
    ALTER TABLE dbo.GhnDelivery ADD LastWebhookGenerationSynced bigint NOT NULL CONSTRAINT DF_GhnDelivery_WebhookSynced DEFAULT 0 WITH VALUES;
IF OBJECT_ID('dbo.GhnWebhookReceipt','U') IS NULL
BEGIN
    CREATE TABLE dbo.GhnWebhookReceipt (
        EventKey char(64) NOT NULL CONSTRAINT PK_GhnWebhookReceipt PRIMARY KEY,
        OrderID int NOT NULL CONSTRAINT FK_GhnWebhookReceipt_Order REFERENCES dbo.SalesOrder(OrderID),
        EventType varchar(50) NOT NULL,
        EventTime datetime2 NOT NULL,
        ReceivedAt datetime2 NOT NULL CONSTRAINT DF_GhnWebhookReceipt_Received DEFAULT SYSUTCDATETIME()
    );
END;
