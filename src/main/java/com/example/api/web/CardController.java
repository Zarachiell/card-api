package com.example.api.web;

import com.example.api.service.CardIngestionService;
import com.example.api.service.interfaces.CardSecureService;
import com.example.api.util.UploadResult;
import com.example.api.web.request.CardCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardSecureService secureService;
    private final CardIngestionService ingestionService;


    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CardSecureService.PersistResult create(@Valid @RequestBody CardCreateRequest req) {
        return secureService.createOrGet(req, null, null);
    }

    @PostMapping(value="/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public UploadResult uploadFixed(@RequestPart("file") MultipartFile file) throws IOException {
        return ingestionService.ingestFixed(file.getInputStream());
    }

    @GetMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookup(
            @RequestHeader(name = "X-Card-Pan", required = false) String panHeader
    ) {
        if (panHeader == null || panHeader.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_pan"
            ));
        }

        try {
            return secureService.findByPan(panHeader)
                    .<ResponseEntity<Map<String, Object>>>map(ref -> ResponseEntity.ok(Map.of(
                            "exists", true,
                            "id", ref.id(),           // identificador único do sistema (UUID)
                            // opcional expor também:
                            "token", ref.token(),     // remova se não quiser devolver ao cliente
                            "last4", ref.last4()
                    )))
                    .orElseGet(() -> ResponseEntity.ok(Map.of("exists", false)));
        } catch (IllegalArgumentException e) {
            // ex.: invalid_length / invalid_luhn (se Luhn estiver ligado)
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        }
    }
}
