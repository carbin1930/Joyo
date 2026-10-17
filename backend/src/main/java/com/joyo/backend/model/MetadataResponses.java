package com.joyo.backend.model;

import java.util.List;

/**
 * 元数据接口响应 DTO（docs/30-api/rest-api.md 元数据节）。
 */
public final class MetadataResponses {

    private MetadataResponses() {
    }

    /** GET /connections/{id}/schemas */
    public record SchemasResponse(List<SchemaItem> schemas) {
        public record SchemaItem(String name, boolean isSystem) {
        }
    }

    /** GET /connections/{id}/schemas/{schema}/tables */
    public record TablesResponse(List<TableItem> tables) {
        /** type ∈ BASE_TABLE | VIEW */
        public record TableItem(String name, String type, String engine, String comment, Long rowsEstimate) {
        }
    }

    /** GET /connections/{id}/schemas/{schema}/tables/{table} */
    public record TableDetailResponse(List<TableColumn> columns, List<TableIndex> indexes, String ddl) {
        public record TableColumn(String name, String dataType, Long length, boolean nullable,
                                  String key, String defaultValue, String extra, String comment, int ordinal) {
        }

        public record TableIndex(String name, String type, boolean unique, List<String> columns) {
        }
    }
}
