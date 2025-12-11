package veds.vedsprodtoolbackend.service;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import veds.vedsprodtoolbackend.dto.OdbDtos;
import veds.vedsprodtoolbackend.repo.PartsLookupRepository;
import veds.vedsprodtoolbackend.odoo.OdooService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OdbService {

    private final PartsLookupRepository partsRepo;
    private final OdooService odooService;

    public OdbService(PartsLookupRepository partsRepo, OdooService odooService) {
        this.partsRepo = partsRepo;
        this.odooService = odooService;
    }

    // =======================================================================
    // PUBLIC API METHODS
    // =======================================================================

    /** Layers ophalen */
    public OdbDtos.LayersFilesResponse getChoices(MultipartFile file) throws IOException {

        Path root = extractTarToTemp(file);

        try {
            Path layersBase = findLayersBase(root);
            String boardName = layersBase.getParent().getFileName().toString();

            List<OdbDtos.NamePath> top = new ArrayList<>();
            List<OdbDtos.NamePath> bottom = new ArrayList<>();
            List<OdbDtos.NamePath> other = new ArrayList<>();

            try (Stream<Path> stream = Files.list(layersBase)) {

                for (Path p : stream
                        .filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .collect(Collectors.toList())) {

                    String name = p.getFileName().toString();
                    OdbDtos.NamePath np = new OdbDtos.NamePath(name, p.toString());

                    String lower = name.toLowerCase();
                    if (lower.contains("top")) top.add(np);
                    else if (lower.contains("bot")) bottom.add(np);
                    else other.add(np);
                }
            }

            return new OdbDtos.LayersFilesResponse(boardName, top, bottom, other);

        } finally {
            deleteDirectoryQuietly(root);
        }
    }

    /** Eén layer → rows */
    public OdbDtos.RowsResponse getRowsSingle(MultipartFile file, String layerFolder) throws IOException {
        return getRowsMulti(file, Collections.singletonList(layerFolder));
    }

    /** Meerdere layers → gegroepeerde rows */
    public OdbDtos.RowsResponse getRowsMulti(MultipartFile file, List<String> layerFolders) throws IOException {

        Path root = extractTarToTemp(file);

        try {
            Path layersBase = findLayersBase(root);

            Map<String, OdbDtos.Row> rowsByPart = new LinkedHashMap<>();

            for (String layerFolder : layerFolders) {

                Path components = layersBase.resolve(layerFolder).resolve("components");
                if (!Files.exists(components)) continue;

                List<OdbDtos.Row> fromLayer = parseComponentsFile(components, layerFolder);

                for (OdbDtos.Row r : fromLayer) {

                    OdbDtos.Row existing = rowsByPart.get(r.partName);

                    if (existing == null) {
                        rowsByPart.put(r.partName, r);
                    } else {
                        existing.refdesList.addAll(r.refdesList);
                        existing.qty = String.valueOf(existing.refdesList.size());

                        if (!existing.layer.contains(r.layer)) {
                            existing.layer = existing.layer + "," + r.layer;
                        }
                    }
                }
            }

            List<OdbDtos.Row> finalList = new ArrayList<>(rowsByPart.values());

            // STOCK TOEVOEGEN (SQL + Odoo)
            enrichStock(finalList);

            finalList.sort(Comparator.comparing(row -> row.partName));

            return new OdbDtos.RowsResponse(finalList, null);

        } finally {
            deleteDirectoryQuietly(root);
        }
    }

    /** Alle refdes voor popup */
    public OdbDtos.RefdesResponse getRefs(MultipartFile file, String layer, String partName) throws IOException {

        Path root = extractTarToTemp(file);

        try {
            Path layersBase = findLayersBase(root);
            Path components = layersBase.resolve(layer).resolve("components");

            if (!Files.exists(components))
                return new OdbDtos.RefdesResponse(new ArrayList<>());

            List<OdbDtos.Row> rows = parseComponentsFile(components, layer);

            for (OdbDtos.Row r : rows) {
                if (r.partName.equals(partName)) {
                    return new OdbDtos.RefdesResponse(r.refdesList);
                }
            }

            return new OdbDtos.RefdesResponse(new ArrayList<>());

        } finally {
            deleteDirectoryQuietly(root);
        }
    }

    // =======================================================================
    // ODB EXTRACT
    // =======================================================================

    private Path extractTarToTemp(MultipartFile file) throws IOException {

        Path tempDir = Files.createTempDirectory("odb_");

        InputStream raw = file.getInputStream();
        InputStream in = raw;

        if (file.getOriginalFilename().endsWith(".tgz") ||
                file.getOriginalFilename().endsWith(".gz")
        ) {
            in = new GzipCompressorInputStream(raw);
        }

        try (TarArchiveInputStream tar = new TarArchiveInputStream(in)) {

            TarArchiveEntry entry;

            while ((entry = tar.getNextTarEntry()) != null) {

                Path out = tempDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(tar, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        return tempDir;
    }

    // =======================================================================
    // PATH HELPERS
    // =======================================================================

    private Path findLayersBase(Path root) throws IOException {

        Path steps = Files.walk(root, 5)
                .filter(Files::isDirectory)
                .filter(p -> p.getFileName().toString().equalsIgnoreCase("steps"))
                .findFirst()
                .orElseThrow(() -> new IOException("steps folder niet gevonden"));

        Path board = Files.list(steps)
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new IOException("board folder niet gevonden"));

        Path layers = board.resolve("layers");

        if (!Files.exists(layers))
            throw new IOException("layers map niet gevonden");

        return layers;
    }

    // =======================================================================
    // COMPONENT PARSER
    // =======================================================================

    private List<OdbDtos.Row> parseComponentsFile(Path file, String layer) throws IOException {

        Map<String, OdbDtos.Row> rows = new LinkedHashMap<>();

        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {

            String line;
            String currentPart = null;

            while ((line = br.readLine()) != null) {

                line = line.trim();
                if (line.isEmpty()) continue;

                // CMP regel
                if (line.startsWith("CMP ")) {

                    String[] p = line.split("\\s+");

                    if (p.length >= 8) {
                        String refdes = p[6];
                        String partName = p[7];

                        currentPart = partName;

                        OdbDtos.Row r = rows.get(partName);

                        if (r == null) {
                            r = new OdbDtos.Row();
                            r.partName = partName;
                            r.layer = layer;
                            r.refdesList = new ArrayList<>();
                            rows.put(partName, r);
                        }

                        r.refdesList.add(refdes);
                        if (r.refdes == null) r.refdes = refdes;
                    }
                }

                // PRP regel
                else if (line.startsWith("PRP ") && currentPart != null) {

                    String content = line.substring(4).trim();
                    int sp = content.indexOf(" ");
                    if (sp < 0) continue;

                    String attr = content.substring(0, sp);
                    String val = extractValue(content);

                    OdbDtos.Row r = rows.get(currentPart);

                    if (attr.equals("TYPE")) r.type = val;
                    if (attr.equals("DESCRIPTION")) r.description = val;
                }
            }
        }

        // qty + mounting
        for (OdbDtos.Row r : rows.values()) {

            r.qty = String.valueOf(r.refdesList.size());

            String d = r.description == null ? "" : r.description.toUpperCase();
            r.mounting = d.contains("SMD") ? "SMD" : "";
        }

        return new ArrayList<>(rows.values());
    }

    private String extractValue(String s) {
        int a = s.indexOf('\'');
        int b = s.lastIndexOf('\'');
        if (a >= 0 && b > a) return s.substring(a + 1, b);
        return "";
    }

    // =======================================================================
    // STOCK ENRICHMENT (SQL SERVER + ODOO)
    // =======================================================================

    private void enrichStock(List<OdbDtos.Row> rows) {

        try {
            // Verzamel partnames
            Set<String> parts = rows.stream()
                    .map(r -> r.partName)
                    .collect(Collectors.toSet());

            // ---- SQL SERVER ----
            Map<String, PartsLookupRepository.PartInfo> sqlMap =
                    partsRepo.fetchByNumbers(parts);

            for (OdbDtos.Row r : rows) {
                PartsLookupRepository.PartInfo info = sqlMap.get(r.partName);
                if (info != null) {
                    r.odoo = info.navName;
                    r.alt = info.alt;
                    r.type = info.type;
                    r.pins = info.pins;
                    r.stock = info.stock != null ? info.stock : "";
                }
            }

            // ---- ODOO STOCK ----
            Map<String, Double> odooMap =
                    odooService.getStockForProducts(parts);

            for (OdbDtos.Row r : rows) {
                if (odooMap.containsKey(r.partName)) {
                    r.stock = String.valueOf(odooMap.get(r.partName));
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Stock enrich failed: " + e.getMessage());
        }
    }

    // =======================================================================
    // TEMP CLEANUP
    // =======================================================================

    private void deleteDirectoryQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p);} catch (Exception ignored) {}});
        } catch (Exception ignored) {}
    }
}
