-- Run after 20261022_ghn_delivery.sql. Timestamps below are UTC.
IF OBJECT_ID(N'dbo.GhnDelivery',N'U') IS NULL
    THROW 51041, 'Install 20261022_ghn_delivery.sql first.', 1;
IF COL_LENGTH('dbo.GhnDelivery','LastSyncedAt') IS NULL
    ALTER TABLE dbo.GhnDelivery ADD LastSyncedAt datetime2 NULL;
IF COL_LENGTH('dbo.GhnDelivery','NextSyncAt') IS NULL
    ALTER TABLE dbo.GhnDelivery ADD NextSyncAt datetime2 NOT NULL
        CONSTRAINT DF_GhnDelivery_NextSync DEFAULT SYSUTCDATETIME() WITH VALUES;
IF COL_LENGTH('dbo.GhnDelivery','SyncLeaseToken') IS NULL
    ALTER TABLE dbo.GhnDelivery ADD SyncLeaseToken uniqueidentifier NULL;
IF COL_LENGTH('dbo.GhnDelivery','SyncLeaseUntil') IS NULL
    ALTER TABLE dbo.GhnDelivery ADD SyncLeaseUntil datetime2 NULL;
IF COL_LENGTH('dbo.GhnDelivery','SyncFailureCount') IS NULL
    ALTER TABLE dbo.GhnDelivery ADD SyncFailureCount int NOT NULL
        CONSTRAINT DF_GhnDelivery_SyncFailures DEFAULT 0 WITH VALUES;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.GhnDelivery') AND name='IX_GhnDelivery_NextSync')
    EXEC('CREATE INDEX IX_GhnDelivery_NextSync ON dbo.GhnDelivery(ShopID,NextSyncAt) INCLUDE(BookingState,CarrierStatus,SyncLeaseUntil)');
