package com.example.api.service;

import com.example.api.service.interfaces.CardSecureService;
import com.example.api.util.FixedLayoutParser;
import com.example.api.util.UploadResult;
import com.example.api.web.request.CardCreateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardIngestionServiceTest {

    @Mock FixedLayoutParser parser;
    @Mock CardSecureService secureService;

    @InjectMocks CardIngestionService service;

    @Test
    void ingestFixed_summarizes_created_duplicate_failed_and_builds_items() {
        // Header/Details reais do parser (records)
        var header = new FixedLayoutParser.Header(
                "DESAFIO-HYPERATIVA", LocalDate.of(2018,5,24), "LOTE0001000010", 3);

        var d1 = new FixedLayoutParser.Detail(2, 1, "4456897999999999");
        var d2 = new FixedLayoutParser.Detail(3, 2, "4456897919999999");
        var d3 = new FixedLayoutParser.Detail(4, 3, "INVALIDO");

        var batch = new FixedLayoutParser.FixedBatch(header, List.of(d1,d2,d3));
        when(parser.parse(any())).thenReturn(batch);

        // Mock do serviço seguro: 1 criado, 1 duplicado, 1 falha
        var createdId = UUID.randomUUID().toString();
        var dupId     = UUID.randomUUID().toString();

        when(secureService.createOrGet(any(CardCreateRequest.class), eq("LOTE0001000010"), eq(1)))
                .thenReturn(new CardSecureService.PersistResult(createdId, "tok_created", "9999", false));

        when(secureService.createOrGet(any(CardCreateRequest.class), eq("LOTE0001000010"), eq(2)))
                .thenReturn(new CardSecureService.PersistResult(dupId, "tok_dup", "9999", true));

        when(secureService.createOrGet(any(CardCreateRequest.class), eq("LOTE0001000010"), eq(3)))
                .thenThrow(new IllegalArgumentException("invalid pan"));

        var in = new ByteArrayInputStream("qualquer".getBytes(StandardCharsets.UTF_8)); // o conteúdo não importa; o parser é mockado

        // Act
        UploadResult result = service.ingestFixed(in);

        // Assert — resumo
        assertNotNull(result);
        assertEquals(1, result.summary().created());
        assertEquals(1, result.summary().duplicates());
        assertEquals(1, result.summary().failed());

        // Assert — itens (ordem segue a leitura)
        assertEquals(3, result.items().size());
        var it1 = result.items().get(0); // created
        assertEquals(d1.line(), it1.line());
        assertEquals(createdId, it1.id());
        assertEquals("tok_created", it1.token());
        assertEquals("9999", it1.last4());
        assertNull(it1.error());

        var it2 = result.items().get(1); // duplicate
        assertEquals(d2.line(), it2.line());
        assertEquals(dupId, it2.id());
        assertEquals("tok_dup", it2.token());
        assertEquals("9999", it2.last4());
        assertNull(it2.error());

        var it3 = result.items().get(2); // failed
        assertEquals(d3.line(), it3.line());
        assertNull(it3.id());
        assertNull(it3.token());
        assertNotNull(it3.error());
        assertTrue(it3.error().toLowerCase().contains("invalid"));

        // Verifica chamadas com seq corretas
        verify(secureService).createOrGet(any(CardCreateRequest.class), eq("LOTE0001000010"), eq(1));
        verify(secureService).createOrGet(any(CardCreateRequest.class), eq("LOTE0001000010"), eq(2));
        verify(secureService).createOrGet(any(CardCreateRequest.class), eq("LOTE0001000010"), eq(3));
        verifyNoMoreInteractions(secureService);
    }

    @Test
    void ingestFixed_handles_empty_details() {
        var header = new FixedLayoutParser.Header("DESAFIO", LocalDate.of(2020,1,1), "LOTE12345678", 0);
        var batch  = new FixedLayoutParser.FixedBatch(header, List.of());
        when(parser.parse(any())).thenReturn(batch);

        var in = new ByteArrayInputStream(new byte[0]);

        var result = service.ingestFixed(in);

        assertEquals(0, result.summary().created());
        assertEquals(0, result.summary().duplicates());
        assertEquals(0, result.summary().failed());
        assertEquals(0, result.items().size());
        assertEquals("LOTE12345678", result.header().lot());
        verifyNoInteractions(secureService);
    }

    @Test
    void ingestFixed_allows_null_sequence_to_flow_through() {
        var header = new FixedLayoutParser.Header("X", LocalDate.now(), "LOTEAAAA0001", 1);
        var d1     = new FixedLayoutParser.Detail(2, null, "4456897999999999"); // seq nula
        var batch  = new FixedLayoutParser.FixedBatch(header, List.of(d1));
        when(parser.parse(any())).thenReturn(batch);

        var id = UUID.randomUUID().toString();
        when(secureService.createOrGet(any(CardCreateRequest.class), eq("LOTEAAAA0001"), isNull()))
                .thenReturn(new CardSecureService.PersistResult(id, "tok", "9999", false));

        var result = service.ingestFixed(new ByteArrayInputStream(new byte[0]));
        assertEquals(1, result.summary().created());
        assertEquals(id, result.items().get(0).id());
        verify(secureService).createOrGet(any(CardCreateRequest.class), eq("LOTEAAAA0001"), isNull());
    }
}