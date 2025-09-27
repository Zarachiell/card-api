package com.example.api.service;

import com.example.api.service.interfaces.CardSecureService;
import com.example.api.util.FixedLayoutParser;
import com.example.api.util.UploadResult;
import com.example.api.web.request.CardCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class CardIngestionService {

    private final FixedLayoutParser parser;
    private final CardSecureService secureService;

    public UploadResult ingestFixed(InputStream in) {
        FixedLayoutParser.FixedBatch batch = parser.parse(in); // header + details + valida trailer
        var items = new ArrayList<UploadResult.ItemResult>();
        int created = 0, dup = 0, failed = 0;

        for (var d : batch.details()) {
            try {
                var req = new CardCreateRequest(d.pan(), "UNKNOWN", 12, 2099, null, null);
                var pr  = secureService.createOrGet(req, batch.header().lot(), d.seq());
                if (pr.duplicate()) { dup++; items.add(UploadResult.ItemResult.duplicate(d.line(), pr.id(), pr.token(), pr.last4())); }
                else                { created++; items.add(UploadResult.ItemResult.created(d.line(),   pr.id(), pr.token(), pr.last4())); }
            } catch (Exception e) {
                failed++;
                items.add(UploadResult.ItemResult.invalid(d.line(), e.getMessage() == null ? "invalid" : e.getMessage()));
            }
        }

        var summary = new UploadResult.UploadSummary(batch.header().qty(), created, dup, failed);
        var header  = new UploadResult.HeaderInfo(batch.header().name(), batch.header().date().toString(),
                batch.header().lot(), batch.header().qty());
        return new UploadResult(header, summary, items);
    }
}