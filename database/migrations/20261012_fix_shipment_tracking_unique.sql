-- Fix SQL Server UNIQUE semantics for optional shipment tracking numbers.
-- Run this script in the MenWearStore database (for example, in SSMS).
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.Shipment', N'U') IS NULL
        THROW 51000, 'dbo.Shipment does not exist in the current database.', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.columns
        WHERE object_id = OBJECT_ID(N'dbo.Shipment')
          AND name = N'TrackingNumber'
    )
        THROW 51001, 'dbo.Shipment.TrackingNumber does not exist.', 1;

    IF EXISTS (
        SELECT 1
        FROM sys.key_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.Shipment')
          AND name = N'UQ_Shipment_Tracking'
          AND type = N'UQ'
    )
    BEGIN
        ALTER TABLE dbo.Shipment
            DROP CONSTRAINT UQ_Shipment_Tracking;
    END
    ELSE IF EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.Shipment')
          AND name = N'UQ_Shipment_Tracking'
    )
    BEGIN
        DROP INDEX UQ_Shipment_Tracking ON dbo.Shipment;
    END;

    CREATE UNIQUE NONCLUSTERED INDEX UQ_Shipment_Tracking
        ON dbo.Shipment (TrackingNumber)
        WHERE TrackingNumber IS NOT NULL;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
