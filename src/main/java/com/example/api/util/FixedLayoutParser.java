package com.example.api.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class FixedLayoutParser {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    public FixedBatch parse(InputStream in) {
        try (var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            // HEADER
            String h = requireLine(br, 1);
            Header header = parseHeader(h);

            // DETALHES + TRAILER
            List<Detail> details = new ArrayList<>();
            String line; int ln = 2;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) { ln++; continue; }

                // TRAILER
                if (line.startsWith("LOTE")) {
                    validateTrailer(line, header, details.size(), ln);
                    break;
                }

                // DETALHE
                if (line.charAt(0) != 'C') throw bad(ln, "invalid_identifier");

                Integer seq = tryParseInt(take(line, 1, 6));       // [02-07]
                String raw  = take(line, 7, 19).replaceAll("\\D",""); // [08-26]
                if (raw.length() < 12) throw bad(ln, "pan_length");
                String pan  = raw.length() > 19 ? raw.substring(0,19) : raw;

                details.add(new Detail(ln, seq, pan));
                ln++;
            }

            return new FixedBatch(header, details);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /* ---------- parsing helpers compactos ---------- */
    private static Header parseHeader(String h) {
        String name = take(h, 0, 29);
        LocalDate date = LocalDate.parse(take(h, 29, 8), YYYYMMDD);
        String lot = take(h, 37, 8);
        int qty = Integer.parseInt(take(h, 45, 6));
        return new Header(name, date, lot, qty);
    }

    private static void validateTrailer(String line, Header header, int seen, int ln) {
        String lotTr = take(line, 0, 8);
        int qtyTr = Integer.parseInt(take(line, 8, 6));
        if (!Objects.equals(header.lot(), lotTr)) throw bad(ln, "trailer_mismatch_lot");
        if (qtyTr != seen)                         throw bad(ln, "trailer_qty_mismatch");
    }

    /** fatia segura: substring([from, from+len)), recorta aos limites e tira espaços à direita */
    private static String take(String s, int from, int len) {
        int i = Math.max(0, from), j = Math.min(s.length(), from + Math.max(0, len));
        return (i >= j) ? "" : s.substring(i, j).stripTrailing();
    }

    private static String requireLine(BufferedReader br, int n) throws IOException {
        String l = br.readLine();
        if (l == null) throw bad(n, "missing_header");
        return l;
    }

    private static Integer tryParseInt(String s) { try { return s.isBlank()? null : Integer.parseInt(s); } catch (Exception e) { return null; } }
    private static IllegalArgumentException bad(int line, String code) { return new IllegalArgumentException(code + " (line " + line + ")"); }

    /* ---------- modelos do parser ---------- */
    public record Header(String name, LocalDate date, String lot, int qty) {}
    public record Detail(int line, Integer seq, String pan) {}
    public record FixedBatch(Header header, List<Detail> details) {}
}