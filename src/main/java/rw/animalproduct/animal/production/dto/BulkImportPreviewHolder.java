package rw.animalproduct.animal.production.dto;

import java.io.Serializable;

/**
 * Wrapper around {@link BulkImportResult} so we can store it safely in
 * the HTTP session between "upload & preview" and "confirm import".
 * Kept Serializable in case you switch to a distributed session store
 * (Hazelcast, Redis, JDBC) later.
 */
public class BulkImportPreviewHolder implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SESSION_KEY = "BULK_IMPORT_PREVIEW";

    private BulkImportResult result;

    public BulkImportPreviewHolder() { }

    public BulkImportPreviewHolder(BulkImportResult result) {
        this.result = result;
    }

    public BulkImportResult getResult() { return result; }
    public void setResult(BulkImportResult result) { this.result = result; }
}