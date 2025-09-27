package com.example.api.web;

import com.example.api.DTO.CardRef;
import com.example.api.service.CardIngestionService;
import com.example.api.service.interfaces.CardSecureService;
import com.example.api.web.request.CardCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Sobe apenas a camada web do CardController
@WebMvcTest(controllers = CardController.class)
@AutoConfigureMockMvc(addFilters = true)
// Evita subir o Springdoc no teste (corrige NoSuchMethodError)
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class CardControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    // Dependências do controller são mockadas
    @MockitoBean CardSecureService secureService;
    @MockitoBean CardIngestionService ingestionService;

    /* ---------------------- POST /cards (JSON) ---------------------- */

    @Test
    @DisplayName("POST /cards - cria ou retorna existente")
    void create_ok() throws Exception {
        var req = new CardCreateRequest("4456897999999999", "UNKNOWN", 12, 2099, null, null);
        var resp = new CardSecureService.PersistResult("11111111-2222-3333-4444-555555555555", "tok_abcd", "9999", false);

        when(secureService.createOrGet(eq(req), isNull(), isNull())).thenReturn(resp);

        mvc.perform(post("/cards")
                        .with(jwt()) // autenticação simulada
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(resp.id())))
                .andExpect(jsonPath("$.token", is(resp.token())))
                .andExpect(jsonPath("$.last4", is(resp.last4())))
                .andExpect(jsonPath("$.duplicate", is(resp.duplicate())));

        verify(secureService).createOrGet(eq(req), isNull(), isNull());
    }

    /* ---------------------- POST /cards/upload (multipart) ---------------------- */

    @Test
    @DisplayName("POST /cards/upload - processa TXT e retorna resumo")
    void upload_ok() throws Exception {
        // arquivo (conteúdo não importa pois ingestionService é mockado)
        var file = new MockMultipartFile(
                "file", "lote.txt", "text/plain",
                "HEADER\nC1  4456...\nLOTE0001".getBytes(StandardCharsets.UTF_8)
        );

        // Monta um UploadResult sintético mínimo
        var header = new com.example.api.util.UploadResult.HeaderInfo("DESAFIO", "2018-05-24", "LOTE0001", 2);
        var summary = new com.example.api.util.UploadResult.UploadSummary(2, 1, 1, 0);
        var items = List.of(
                com.example.api.util.UploadResult.ItemResult.created(2, "id-1", "tok_1", "1234"),
                com.example.api.util.UploadResult.ItemResult.duplicate(3, "id-2", "tok_2", "5678")
        );
        var result = new com.example.api.util.UploadResult(header, summary, items);

        when(ingestionService.ingestFixed(ArgumentMatchers.any())).thenReturn(result);

        mvc.perform(multipart("/cards/upload")
                        .file(file)
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.summary.received", is(2)))
                .andExpect(jsonPath("$.summary.created", is(1)))
                .andExpect(jsonPath("$.summary.duplicates", is(1)))
                .andExpect(jsonPath("$.summary.failed", is(0)));

        verify(ingestionService).ingestFixed(any());
    }

    /* ---------------------- GET /cards/lookup ---------------------- */

    @Test
    @DisplayName("GET /cards/lookup - 400 se header X-Card-Pan ausente")
    void lookup_missingHeader_400() throws Exception {
        mvc.perform(get("/cards/lookup").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("missing_pan")));
        verifyNoInteractions(secureService);
    }

    @Test
    @DisplayName("GET /cards/lookup - retorna exists=true com id/token/last4")
    void lookup_found_ok() throws Exception {
        String pan = "4456897999999999";
        var ref = new CardRef("uuid-1", "tok_abc", "9999");
        when(secureService.findByPan(pan)).thenReturn(Optional.of(ref));

        mvc.perform(get("/cards/lookup")
                        .with(jwt())
                        .header("X-Card-Pan", pan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists", is(true)))
                .andExpect(jsonPath("$.id", is(ref.id())))
                .andExpect(jsonPath("$.token", is(ref.token())))
                .andExpect(jsonPath("$.last4", is(ref.last4())));
    }

    @Test
    @DisplayName("GET /cards/lookup - retorna exists=false quando não encontrado")
    void lookup_notFound_ok() throws Exception {
        String pan = "4456897999999999";
        when(secureService.findByPan(pan)).thenReturn(Optional.empty());

        mvc.perform(get("/cards/lookup")
                        .with(jwt())
                        .header("X-Card-Pan", pan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists", is(false)));
    }

    @Test
    @DisplayName("GET /cards/lookup - 422 quando PAN inválido")
    void lookup_invalidPan_422() throws Exception {
        String bad = "12";
        when(secureService.findByPan(bad)).thenThrow(new IllegalArgumentException("invalid_pan_length"));

        mvc.perform(get("/cards/lookup")
                        .with(jwt())
                        .header("X-Card-Pan", bad))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("invalid_pan_length")));
    }
}
