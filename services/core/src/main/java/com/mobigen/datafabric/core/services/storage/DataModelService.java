package com.mobigen.datafabric.core.services.storage;

import com.mobigen.datafabric.core.model.*;
import com.mobigen.datafabric.core.util.DataLayerConnection;
import com.mobigen.datafabric.share.protobuf.DataLayer;
import com.mobigen.datafabric.share.protobuf.DataModelOuterClass;
import com.mobigen.datafabric.share.protobuf.UserOuterClass;
import com.mobigen.datafabric.share.protobuf.Utilities;
import com.mobigen.libs.configuration.Config;
import com.mobigen.sqlgen.where.conditions.Equal;
import lombok.extern.slf4j.Slf4j;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;

import static com.mobigen.sqlgen.SqlBuilder.insert;
import static com.mobigen.sqlgen.SqlBuilder.select;

/**
 * <p>
 * Created by fwani.
 *
 * @version 0.0.1
 * @since 0.0.1
 */
@Slf4j
public class DataModelService {

    static Config config = new Config();
    DataLayerConnection dataLayerConnection;

    public DataModelService() {
        this(new DataLayerConnection(
                config.getConfig().getBoolean("data-layer.test", false)
        ));
    }

    public DataModelService(DataLayerConnection dataLayerConnection) {
        this.dataLayerConnection = dataLayerConnection;
    }

    public void NewDataModels(List<DataModelOuterClass.DataModel.Builder> dataModels) {
        List<String> sqlList = new ArrayList<>();
        // Data Model
        dataModels.forEach(data -> {
            var id = UUID.randomUUID().toString();
            OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

            var insertDataModel = insert(DataModel.table)
                    .columns(
                            DataModel.id,
                            DataModel.name,
                            DataModel.description,
                            DataModel.type,
                            DataModel.format,
                            DataModel.status,
                            DataModel.createdAt,
                            DataModel.createdBy
                    )
                    .values(
                            id,
                            data.getName(),
                            data.getDescription() == null ? "" : data.getDescription(),
                            data.getDataType(),
                            data.getDataFormat(),
                            data.getStatus(),
                            createdAt.format(dateTimeFormatter),
                            data.getCreator().getId()
                    )
                    .generate()
                    .getStatement();
            sqlList.add(insertDataModel);
            for (DataModelOuterClass.DataLocation dataLocation : data.getDataLocationList()) {
                var insertSql = insert(DataModelLocation.table)
                        .columns(
                                DataModelLocation.id,
                                DataModelLocation.storageId,
                                DataModelLocation.path,
                                DataModelLocation.name
                        ).values(
                                id,
                                dataLocation.getStorageId(),
                                dataLocation.getDatabaseName(),
                                dataLocation.getTableName()
                        ).generate().getStatement();
                sqlList.add(insertSql);
            }
            for (Utilities.Meta meta : data.getSystemMetaList()) {
                var insertSql = insert(DataModelMetadata.table)
                        .columns(
                                DataModelMetadata.id,
                                DataModelMetadata.isSystem,
                                DataModelMetadata.key,
                                DataModelMetadata.value
                        ).values(
                                id,
                                true,
                                meta.getKey(),
                                meta.getValue()
                        ).generate().getStatement();
                sqlList.add(insertSql);
            }
//            if( data.getDataRefine() != null ) {
//
//            }
            for (DataModelOuterClass.DataStructure dataStructure : data.getDataStructureList()) {
                var insertSql = insert(DataModelSchema.table)
                        .columns(
                                DataModelSchema.id,
                                DataModelSchema.ordinalPosition,
                                DataModelSchema.columnName,
                                DataModelSchema.dataType,
                                DataModelSchema.length,
                                DataModelSchema.defaultValue,
                                DataModelSchema.description
                        ).values(
                                id,
                                dataStructure.getOrder(),
                                dataStructure.getName(),
                                dataStructure.getColType(),
                                dataStructure.getLength(),
                                dataStructure.getDefaultValue(),
                                dataStructure.getDescription()
                        ).generate().getStatement();
                sqlList.add(insertSql);
            }
//            if( data.getTagList(  ) != null && data.getTagList().size() > 0 ) {
//
//            }
        });
        var result = dataLayerConnection.executeBatch(sqlList);
        log.info("[ Data-Layer ] Data Model Insert Result : " + result.getDataList());
    }

    public DataModelOuterClass.DataModel.Builder getDataModel(String id) {
        var selectSql = select().from(DataModel.table)
                .where(Equal.of(DataModel.id, id))
                .generate().getStatement();
        var rowDataModel = dataLayerConnection.execute(selectSql).getData().getTable();
        if (rowDataModel.getRowsCount() <= 0) {
            log.error("Error. Not Found Data Model. Input ID[ {} ]", id);
            return null;
        }
        List<DataModelOuterClass.DataModel.Builder> dataModelBuilder = new ArrayList<>();
        // Set DataModel Info( name, desc, ... )
        parseAndInsertToDataModel(dataModelBuilder, rowDataModel);
        return dataModelBuilder.get(0);
    }

    public DataModelOuterClass.DataModel.Builder getDataModelPreview(String id) {
        DataModelOuterClass.DataModel.Builder dataModelBuilder = getDataModel(id);
        if (dataModelBuilder == null) return null;

        // Hard Coding : TODO : 삭제 필요
        dataModelBuilder.setPermission(DataModelOuterClass.Permission.newBuilder().setRead(true).setWrite(true).build());

        // Set DataModel Metadata ( System / User )
        var selectMetadataSql = select().from(DataModelMetadata.table)
                .where(Equal.of(DataModelMetadata.id, id))
                .generate().getStatement();
        var rowDataModelMetadata = dataLayerConnection.execute(selectMetadataSql).getData().getTable();
        if (rowDataModelMetadata.getRowsCount() > 0) {
            parseAndInsertToMetadata(dataModelBuilder, rowDataModelMetadata);
        }
        // Set DataModel Tag
        var selectTagSql = select().from(DataModelTag.table)
                .where(Equal.of(DataModelTag.id, id))
                .generate().getStatement();
        var rowDataModelTag = dataLayerConnection.execute(selectTagSql).getData().getTable();
        if (rowDataModelTag.getRowsCount() > 0) {
            parseAndInsertToTag(dataModelBuilder, rowDataModelTag);
        }
        // Set DataModel Location
        var selectLocationSql = select().from(DataModelLocation.table)
                .where(Equal.of(DataModelLocation.id, id))
                .generate().getStatement();
        var rowDataModelLocation = dataLayerConnection.execute(selectLocationSql).getData().getTable();
        if (rowDataModelLocation.getRowsCount() > 0) {
            parseAndInsertToLocation(dataModelBuilder, rowDataModelLocation);
        }
        // Set DataModel Data Structure
        var selectSchemaSql = select().from(DataModelSchema.table)
                .where(Equal.of(DataModelSchema.id, id))
                .generate().getStatement();
        var rowDataModelSchema = dataLayerConnection.execute(selectSchemaSql).getData().getTable();
        if (rowDataModelSchema.getRowsCount() > 0) {
            parseAndInsertToSchema(dataModelBuilder, rowDataModelSchema);
        }
        return dataModelBuilder;
    }

    public DataModelOuterClass.DataModel.Builder getDataModelDefault(String id) {
        DataModelOuterClass.DataModel.Builder dataModelBuilder = getDataModelPreview(id);
        if (dataModelBuilder == null) return null;
        // Set Download Info
        dataModelBuilder.setDownloadInfo(
                DataModelOuterClass.DownloadInfo.newBuilder()
                        .setStatus(DataModelOuterClass.DownloadInfo.DownloadStatus.READY)
                        .setUrl("http://data-fabric.com/download/data")
                        .build());

        // Set DataModel User Comment(Rating)
        var selectCommentSql = select().from(DataModelUserComment.table)
                .where(Equal.of(DataModelUserComment.dataModelId, id))
                .generate().getStatement();
        var rowDataModelComment = dataLayerConnection.execute(selectCommentSql).getData().getTable();
        if (rowDataModelComment.getRowsCount() > 0) {
            parseAndInsertToComment(dataModelBuilder, rowDataModelComment);
        }
        // TODO : Set Statistics

        return dataModelBuilder;
    }

    private void parseAndInsertToDataModel(
            List<DataModelOuterClass.DataModel.Builder> dataModelbuilder, DataLayer.Table rowDataModel) {
        for (int rowIdx = 0; rowIdx < rowDataModel.getRowsCount(); rowIdx++) {
            DataModelOuterClass.DataModel.Builder builder = DataModelOuterClass.DataModel.newBuilder();
            for (int colIdx = 0; colIdx < rowDataModel.getColumnsList().size(); colIdx++) {
                var column = rowDataModel.getColumnsList().get(colIdx);
                if (column.getColumnName().equalsIgnoreCase(DataModel.id.getName())) {
                    builder.setId(
                            rowDataModel.getRows(rowIdx).getCell(colIdx).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModel.name.getName())) {
                    builder.setName(
                            rowDataModel.getRows(rowIdx).getCell(colIdx).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModel.description.getName())) {
                    builder.setDescription(
                            rowDataModel.getRows(rowIdx).getCell(colIdx).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModel.type.getName())) {
                    builder.setDataType(
                            rowDataModel.getRows(rowIdx).getCell(colIdx).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModel.format.getName())) {
                    builder.setDataFormat(
                            rowDataModel.getRows(rowIdx).getCell(colIdx).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModel.status.getName())) {
                    builder.setStatus(
                            rowDataModel.getRows(rowIdx).getCell(colIdx).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModel.createdAt.getName())) {
                    builder.setCreatedAt(
                            rowDataModel.getRows(rowIdx).getCell(colIdx).getTimeValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModel.createdBy.getName())) {
                    builder.setCreator(UserOuterClass.User.newBuilder()
                            .setId(rowDataModel.getRows(rowIdx).getCell(colIdx).getStringValue())
                            .setName("mobigen")
                            .setNickname("mobigen.platform")
                            .build());
                } else if (column.getColumnName().equalsIgnoreCase(DataModel.lastModifiedAt.getName())) {
                    builder.setLastModifiedAt(
                            rowDataModel.getRows(rowIdx).getCell(colIdx).getTimeValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModel.lastModifiedBy.getName())) {
                    builder.setLastModifier(UserOuterClass.User.newBuilder().setId(
                            rowDataModel.getRows(rowIdx).getCell(colIdx).getStringValue()).build());
                }
            }
            dataModelbuilder.add(builder);
        }
    }

    private void parseAndInsertToMetadata(
            DataModelOuterClass.DataModel.Builder modelBuilder, DataLayer.Table rowDataModel) {

        for (int rowIdx = 0; rowIdx < rowDataModel.getRowsCount(); rowIdx++) {
            boolean isSystem = false;
            Utilities.Meta.Builder builder = Utilities.Meta.newBuilder();
            for (int colIdx = 0; colIdx < rowDataModel.getColumnsList().size(); colIdx++) {
                var column = rowDataModel.getColumnsList().get(colIdx);
                if (column.getColumnName().equalsIgnoreCase(DataModelMetadata.isSystem.getName())) {
                    isSystem = rowDataModel.getRows(rowIdx).getCell(colIdx).getBoolValue();
                } else if (column.getColumnName().equalsIgnoreCase(DataModelMetadata.key.getName())) {
                    builder.setKey(rowDataModel.getRows(rowIdx).getCell(colIdx).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModelMetadata.value.getName())) {
                    builder.setValue(rowDataModel.getRows(rowIdx).getCell(colIdx).getStringValue());
                }
            }
            if (isSystem) {
                modelBuilder.addSystemMeta(builder.build());
            } else {
                modelBuilder.addUserMeta(builder.build());
            }
        }
    }

    private void parseAndInsertToTag(
            DataModelOuterClass.DataModel.Builder dataModelBuilder, DataLayer.Table rowDataModel) {
        for (int rowIdx = 0; rowIdx < rowDataModel.getRowsCount(); rowIdx++) {
            for (int i = 0; i < rowDataModel.getColumnsList().size(); i++) {
                var column = rowDataModel.getColumnsList().get(i);
                if (column.getColumnName().equalsIgnoreCase(DataModelTag.tag.getName())) {
                    String tag = rowDataModel.getRows(rowIdx).getCell(i).getStringValue();
                    if (!tag.isEmpty()) {
                        dataModelBuilder.addTag(tag);
                    }
                }
            }
        }
    }

    private void parseAndInsertToLocation(
            DataModelOuterClass.DataModel.Builder builder, DataLayer.Table rowDataModel) {
        for (int rowIdx = 0; rowIdx < rowDataModel.getRowsCount(); rowIdx++) {
            DataModelOuterClass.DataLocation.Builder locationBuilder = DataModelOuterClass.DataLocation.newBuilder();
            for (int i = 0; i < rowDataModel.getColumnsList().size(); i++) {
                var column = rowDataModel.getColumnsList().get(i);
                if (column.getColumnName().equalsIgnoreCase(DataModelLocation.storageId.getName())) {
                    locationBuilder.setStorageId(rowDataModel.getRows(rowIdx).getCell(i).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModelLocation.path.getName())) {
                    locationBuilder.setDatabaseName(rowDataModel.getRows(rowIdx).getCell(i).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModelLocation.name.getName())) {
                    locationBuilder.setTableName(rowDataModel.getRows(rowIdx).getCell(i).getStringValue());
                }
            }
            builder.addDataLocation(locationBuilder.build());
        }
    }

    private void parseAndInsertToSchema(
            DataModelOuterClass.DataModel.Builder builder, DataLayer.Table rowDataModel) {
        for (int rowIdx = 0; rowIdx < rowDataModel.getRowsCount(); rowIdx++) {
            DataModelOuterClass.DataStructure.Builder structBuilder = DataModelOuterClass.DataStructure.newBuilder();
            for (int i = 0; i < rowDataModel.getColumnsList().size(); i++) {
                var column = rowDataModel.getColumnsList().get(i);
                if (column.getColumnName().equalsIgnoreCase(DataModelSchema.ordinalPosition.getName())) {
                    structBuilder.setOrder(rowDataModel.getRows(rowIdx).getCell(i).getInt32Value());
                } else if (column.getColumnName().equalsIgnoreCase(DataModelSchema.columnName.getName())) {
                    structBuilder.setName(rowDataModel.getRows(rowIdx).getCell(i).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModelSchema.dataType.getName())) {
                    structBuilder.setColType(rowDataModel.getRows(rowIdx).getCell(i).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModelSchema.length.getName())) {
                    structBuilder.setLength((int) rowDataModel.getRows(rowIdx).getCell(i).getInt64Value());
                } else if (column.getColumnName().equalsIgnoreCase(DataModelSchema.defaultValue.getName())) {
                    structBuilder.setDefaultValue(
                            URLDecoder.decode(rowDataModel.getRows(rowIdx).getCell(i).getStringValue(), StandardCharsets.UTF_8));
                } else if (column.getColumnName().equalsIgnoreCase(DataModelSchema.description.getName())) {
                    structBuilder.setDescription(rowDataModel.getRows(rowIdx).getCell(i).getStringValue());
                }
            }
            builder.addDataStructure(structBuilder.build());
        }
    }

    private void parseAndInsertToComment(
            DataModelOuterClass.DataModel.Builder modelBuilder, DataLayer.Table rowDataModel) {
        DataModelOuterClass.DataModel.RatingAndComments.Builder ratingAndCommentsBuilder =
                DataModelOuterClass.DataModel.RatingAndComments.newBuilder();
        for (int rowIdx = 0; rowIdx < rowDataModel.getRowsCount(); rowIdx++) {
            DataModelOuterClass.RatingAndComment.Builder builder = DataModelOuterClass.RatingAndComment.newBuilder();
            for (int i = 0; i < rowDataModel.getColumnsList().size(); i++) {
                var column = rowDataModel.getColumnsList().get(i);
                if (column.getColumnName().equalsIgnoreCase(DataModelUserComment.id.getName())) {
                    builder.setId(rowDataModel.getRows(rowIdx).getCell(i).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModelUserComment.userId.getName())) {
                    builder.setUser(
                            UserOuterClass.User.newBuilder()
                                    .setId(rowDataModel.getRows(rowIdx).getCell(i).getStringValue())
                                    .build());
                } else if (column.getColumnName().equalsIgnoreCase(DataModelUserComment.rating.getName())) {
                    builder.setRating(rowDataModel.getRows(rowIdx).getCell(i).getInt32Value());
                } else if (column.getColumnName().equalsIgnoreCase(DataModelUserComment.comment.getName())) {
                    builder.setComment(rowDataModel.getRows(rowIdx).getCell(i).getStringValue());
                } else if (column.getColumnName().equalsIgnoreCase(DataModelUserComment.time.getName())) {
                    builder.setLastModifiedAt(
                            rowDataModel.getRows(rowIdx).getCell(i).getTimeValue());
                }
            }
            ratingAndCommentsBuilder.addRatingAndComment(builder.build());
        }
        // Calc Avg Rating
        for (DataModelOuterClass.RatingAndComment ratingAndComment :
                ratingAndCommentsBuilder.getRatingAndCommentList()) {
            ratingAndCommentsBuilder.setAvgRating(
                    ratingAndCommentsBuilder.getAvgRating() + ratingAndComment.getRating());
        }
        if (ratingAndCommentsBuilder.getRatingAndCommentCount() == 0) {
            ratingAndCommentsBuilder.setAvgRating(0);
        } else {
            ratingAndCommentsBuilder.setAvgRating(
                    ratingAndCommentsBuilder.getAvgRating() /
                            ratingAndCommentsBuilder.getRatingAndCommentCount());
        }
        modelBuilder.setRatingAndComments(ratingAndCommentsBuilder.build());
    }

    public boolean updateDataModelMetadata(DataModelOuterClass.ReqMetaUpdate request) {
        DataModelOuterClass.DataModel.Builder dataModelBuilder = getDataModel(request.getId());
        if (dataModelBuilder == null) return false;

        List<String> sqlList = new ArrayList<>();
        for (Utilities.Meta meta : request.getUserMetaList()) {
            var insertSql = String.format("INSERT INTO data_metadata (%s, %s, %s, %s) VALUES('%s','false','%s','%s')" +
                            "ON CONFLICT ON CONSTRAINT data_metadata_un DO UPDATE SET %s='%s'",
                    DataModelMetadata.id.getName(),
                    DataModelMetadata.isSystem.getName(),
                    DataModelMetadata.key.getName(),
                    DataModelMetadata.value.getName(),
                    request.getId(),
                    meta.getKey(),
                    meta.getValue(),
                    DataModelMetadata.value.getName(),
                    meta.getValue());
            sqlList.add(insertSql);
        }
        var result = dataLayerConnection.executeBatch(sqlList);
        log.info("[ Data-Model ] User Metadata Update Result : " + result.getDataList());
        return true;
    }

    public boolean updateDataModelTag(DataModelOuterClass.ReqTagUpdate request) {
        DataModelOuterClass.DataModel.Builder dataModelBuilder = getDataModel(request.getId());
        if (dataModelBuilder == null) return false;

        List<String> sqlList = new ArrayList<>();
        sqlList.add("DELETE FROM data_tag WHERE " + DataModelTag.id.getName() + "='" + request.getId() + "'");
        for (String tag : request.getTagList()) {
            var insertSql = String.format("INSERT INTO data_tag (%s, %s) VALUES('%s','%s')" +
                            "ON CONFLICT ON CONSTRAINT data_tag_un DO UPDATE SET %s='%s'",
                    DataModelTag.id.getName(),
                    DataModelTag.tag.getName(),
                    request.getId(),
                    tag,
                    DataModelTag.tag.getName(),
                    tag);
            sqlList.add(insertSql);
        }
        var result = dataLayerConnection.executeBatch(sqlList);
        log.info("[ Data-Model ] Tag Update Result : " + result.getDataList());
        return true;
    }

    public boolean addComment(DataModelOuterClass.ReqRatingAndComment request) {
        DataModelOuterClass.DataModel.Builder dataModelBuilder = getDataModel(request.getId());
        if (dataModelBuilder == null) return false;

        var insertSql = String.format("INSERT INTO data_user_comment (%s, %s, %s, %s, %s, %s) VALUES('%s', '%s', '%s', '%d', '%s', '%s')",
                DataModelUserComment.id.getName(),
                DataModelUserComment.dataModelId.getName(),
                DataModelUserComment.userId.getName(),
                DataModelUserComment.rating.getName(),
                DataModelUserComment.comment.getName(),
                DataModelUserComment.time.getName(),
                UUID.randomUUID().toString(),
                request.getId(),            // Data Model ID
                "336c8550-a7f8-4c96-9d17-cd10770ace87",                  // User ID
                request.getRatingAndComment().getRating(),
                request.getRatingAndComment().getComment(),
                OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        );
        var result = dataLayerConnection.execute(insertSql);
        log.info("[ Data-Model ] Add Comment Result : " + result.getData());
        return true;
    }

    public boolean updateComment(DataModelOuterClass.ReqRatingAndComment request) {
        DataModelOuterClass.DataModel.Builder dataModelBuilder = getDataModel(request.getId());
        if (dataModelBuilder == null) return false;

        var updateSql = String.format("UPDATE data_user_comment SET %s='%d', %s='%s', %s='%s' WHERE %s='%s'",
                DataModelUserComment.rating.getName(),
                request.getRatingAndComment().getRating(),
                DataModelUserComment.comment.getName(),
                request.getRatingAndComment().getComment(),
                DataModelUserComment.time.getName(),
                OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                DataModelUserComment.id.getName(),
                request.getRatingAndComment().getId());
        var result = dataLayerConnection.execute(updateSql);
        log.info("[ Data-Model ] Update Comment Result : " + result.getData());
        return true;
    }

    public boolean deleteComment(DataModelOuterClass.ReqRatingAndComment request) {
        var deleteSql = String.format("DELETE FROM data_user_comment WHERE %s='%s'",
                DataModelUserComment.id.getName(),
                request.getRatingAndComment().getId());
        var result = dataLayerConnection.execute(deleteSql);
        log.info("[ Data-Model ] Delete Comment Result : " + result.getData());
        return true;
    }

    public DataModelOuterClass.ResDataModels.Data getDataModelSummary(DataModelOuterClass.DataModelSearch request) {
        StringBuilder sql = new StringBuilder();
        StringBuilder selectSql = new StringBuilder();

        // Select
        selectSql.append("SELECT id, name, type, format, status, last_modified_at FROM data_model");
        sql.append(selectSql.toString());

        // Where
        StringBuilder where = new StringBuilder();
        boolean and = false;
        where.append(" WHERE ");
        if( !request.getFilter().getKeyword().isEmpty() ) {
            where.append("name='").append(request.getFilter().getKeyword()).append("'");
            and = true;
        }
        if( !request.getFilter().getDataType().isEmpty() ) {
            if( and ) sql.append(" AND ");
            where.append("type='").append(request.getFilter().getDataType()).append("'");
            and = true;
        }
        if( !request.getFilter().getDataFormat().isEmpty() ) {
            if( and ) where.append(" AND ");
            where.append("format='").append(request.getFilter().getDataFormat()).append("'");
            and = true;
        }

        if( !request.getFilter().getDateRange().getFrom().isEmpty() ) {
            if( and ) where.append(" AND ");
            where.append("created_at > '").append(request.getFilter().getDateRange().getFrom()).append("'");
            and = true;
        }
        if( !request.getFilter().getDateRange().getTo().isEmpty() ) {
            if( and ) sql.append(" AND ");
            sql.append("created_at < '").append(request.getFilter().getDateRange().getTo()).append("'");
            and = true;
        }

        if( !where.toString().equals( " WHERE " ) ) {
            sql.append(where.toString());
        } else {
            where = null;
        }

        // order by
        StringBuilder orderby = new StringBuilder();
        if(!request.getPageable().getSortList().isEmpty()) {
            orderby.append(" ORDER BY ");
            for( int i =0; i < request.getPageable().getSortList().size(); i++ ) {
                int minOrder = -1;
                for( int j = 0; j < request.getPageable().getSortList().size(); j++ ) {
                    if( minOrder == -1 ) {
                        minOrder = request.getPageable().getSortList().get(j).getOrder();
                    }
                    if( request.getPageable().getSortList().get(j).getOrder() < minOrder ) {
                        minOrder = request.getPageable().getSortList().get(j).getOrder();
                    }
                }
                var sort = request.getPageable().getSortList().get(i);
                orderby.append(sort.getField()).append(" ").append( sort.getDirection().name());

                if( i + 1 < request.getPageable().getSortList().size() ) {
                    orderby.append(", ");
                }
            }
        }
        if(!orderby.isEmpty()) {
            sql.append(orderby.toString());
        }

        // 페이지
        var size = request.getPageable().getPage().getSize() <= 0 ? 100 : request.getPageable().getPage().getSize();
        var selectPage = request.getPageable().getPage().getSelectPage() <= 1 ? 1 : request.getPageable().getPage().getSelectPage();
        sql.append(" LIMIT ").append( size );
        if( selectPage > 1) {
            sql.append(" OFFSET ").append( (selectPage - 1) * size );
        }

        var rowDataModels = dataLayerConnection.execute(sql.toString());
        List<DataModelOuterClass.DataModel.Builder> dataModelBuilder = new ArrayList<>();
        if (rowDataModels.getData().getTable().getRowsCount() > 0) {
            parseAndInsertToDataModel(dataModelBuilder, rowDataModels.getData().getTable());
        }
        List<DataModelOuterClass.DataModel> dataModels = new ArrayList<>();
        dataModelBuilder.forEach( builder -> {
            dataModels.add( builder.build() );
        });

        String totalSizeSql = null;
        if(where != null && !where.isEmpty()) {
            totalSizeSql = "SELECT COUNT(*) FROM data_model" + where.toString();
        } else {
            totalSizeSql = "SELECT COUNT(*) FROM data_model";
        }
        var pageableData = dataLayerConnection.execute(totalSizeSql );
        var total = pageableData.getData().getTable().getRows(0).getCell(0).getInt64Value();
        var totalPage = total/size + (total % size == 0 ? 0 : 1);
        return DataModelOuterClass.ResDataModels.Data.newBuilder()
                .addAllDataModels(dataModels)
                .setPageable(Utilities.Pageable.newBuilder()
                        .setPage(Utilities.Page.newBuilder()
                                .setSize(size)
                                .setSelectPage(selectPage)
                                .setTotalSize((int) total)
                                .setTotalPage((int) (totalPage))
                                .build())
                        .addAllSort(request.getPageable().getSortList())
                        .build())
                .build();
    }

    public DataModelOuterClass.ResDataModels.Data getAllDataModel( DataModelOuterClass.DataModelSearch request ) {
        DataModelOuterClass.ResDataModels.Data dataModels = getDataModelSummary( request );
        if( dataModels == null ) return null;
        if( dataModels.getDataModelsCount() <= 0 ) return dataModels;
        DataModelOuterClass.ResDataModels.Data.Builder resBuilder = DataModelOuterClass.ResDataModels.Data.newBuilder();
        resBuilder.setPageable( dataModels.getPageable() );
        for( DataModelOuterClass.DataModel dataModel : dataModels.getDataModelsList() ) {
            DataModelOuterClass.DataModel.Builder dataModelBuilder = getDataModelDefault( dataModel.getId() );
            resBuilder.addDataModels( dataModelBuilder.build() );
        }
        return resBuilder.build();
    }
}
