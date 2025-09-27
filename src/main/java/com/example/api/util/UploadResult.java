package com.example.api.util;

import java.util.List;

public record UploadResult(HeaderInfo header, UploadSummary summary, List<ItemResult> items) {
    public record HeaderInfo(String name, String date, String lot, int qty) {}
    public record UploadSummary(int received, int created, int duplicates, int failed) {}
    public record ItemResult(int line, String status, String id, String token, String last4, String error) {
        public static ItemResult created(int line, String id, String token, String last4)   { return new ItemResult(line, "created",   id, token, last4, null); }
        public static ItemResult duplicate(int line, String id, String token, String last4) { return new ItemResult(line, "duplicate", id, token, last4, null); }
        public static ItemResult invalid(int line, String error)                            { return new ItemResult(line, "invalid",   null, null, null, error); }
    }
}
