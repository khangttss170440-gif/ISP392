-- Run against the existing SQL Server database. No sample outfits are published.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.OutfitCollection', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.OutfitCollection (
        CollectionID int IDENTITY(1,1) NOT NULL CONSTRAINT PK_OutfitCollection PRIMARY KEY,
        Title nvarchar(120) NOT NULL,
        Description nvarchar(1000) NOT NULL CONSTRAINT DF_OutfitCollection_Description DEFAULT N'',
        StylingNotes nvarchar(1000) NOT NULL CONSTRAINT DF_OutfitCollection_Notes DEFAULT N'',
        Style varchar(20) NOT NULL,
        Status varchar(12) NOT NULL CONSTRAINT DF_OutfitCollection_Status DEFAULT 'Draft',
        CreatedBy int NOT NULL CONSTRAINT FK_OutfitCollection_Creator REFERENCES dbo.Staff(StaffID),
        UpdatedBy int NOT NULL CONSTRAINT FK_OutfitCollection_Editor REFERENCES dbo.Staff(StaffID),
        CreatedAt datetime2 NOT NULL CONSTRAINT DF_OutfitCollection_Created DEFAULT SYSUTCDATETIME(),
        UpdatedAt datetime2 NOT NULL CONSTRAINT DF_OutfitCollection_Updated DEFAULT SYSUTCDATETIME(),
        Revision uniqueidentifier NOT NULL CONSTRAINT DF_OutfitCollection_Revision DEFAULT NEWID(),
        CONSTRAINT CK_OutfitCollection_Style CHECK (Style IN ('everyday','office','weekend','evening')),
        CONSTRAINT CK_OutfitCollection_Status CHECK (Status IN ('Draft','Published','Archived')),
        CONSTRAINT CK_OutfitCollection_Title CHECK (LEN(LTRIM(RTRIM(Title))) > 0)
    );
    CREATE INDEX IX_OutfitCollection_Status ON dbo.OutfitCollection(Status, UpdatedAt DESC);
END;
IF OBJECT_ID(N'dbo.OutfitCollectionItem', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.OutfitCollectionItem (
        CollectionID int NOT NULL CONSTRAINT FK_OutfitCollectionItem_Collection REFERENCES dbo.OutfitCollection(CollectionID) ON DELETE CASCADE,
        ProductID int NOT NULL CONSTRAINT FK_OutfitCollectionItem_Product REFERENCES dbo.Product(ProductID),
        Color nvarchar(100) NOT NULL,
        Role varchar(12) NOT NULL,
        SortOrder int NOT NULL,
        CONSTRAINT PK_OutfitCollectionItem PRIMARY KEY (CollectionID, ProductID),
        CONSTRAINT UQ_OutfitCollectionItem_Order UNIQUE (CollectionID, SortOrder),
        CONSTRAINT CK_OutfitCollectionItem_Role CHECK (Role IN ('TOP','BOTTOM','LAYER','SHOES','ACCESSORY')),
        CONSTRAINT CK_OutfitCollectionItem_Order CHECK (SortOrder BETWEEN 0 AND 5)
    );
END;
COMMIT TRANSACTION;
