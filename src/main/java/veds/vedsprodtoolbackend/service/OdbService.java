package veds.vedsprodtoolbackend.service;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import veds.vedsprodtoolbackend.dto.OdbDtos;
import veds.vedsprodtoolbackend.dto.OdbDtos.NamePath;
import veds.vedsprodtoolbackend.dto.OdbDtos.Row;
import veds.vedsprodtoolbackend.dto.OdbDtos.RowsResponse;
import veds.vedsprodtoolbackend.repo.PartsLookupRepository;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Service
public class OdbService {

    @Lazy @Autowired(required = false)
    private PartsLookupRepository partsRepo;

    /* ====================== PUBLIC API ====================== */

    /** Bouw Top/Bottom/Other-keuzes uit designodb.tgz. */
    public OdbDtos.LayersFilesResponse choices(MultipartFile file) throws IOException {
        List<String> names  = listAllTarPaths(file);
        String board        = detectBoard(names);
        Set<String> layers  = collectLayerDirs(names, board);

        List<NamePath> top = new ArrayList<>(), bottom = new ArrayList<>(), other = new ArrayList<>();
        for (String layer : layers) {
            String rel = "steps/" + board + "/layers/" + layer + "/";
            if (isBottom(layer)) bottom.add(new NamePath(layer, rel));
            else if (isTop(layer)) top.add(new NamePath(layer, rel));
            else other.add(new NamePath(layer, rel));
        }
        Comparator<NamePath> by = Comparator.comparing(np -> np.name.toLowerCase(Locale.ROOT));
        top.sort(by); bottom.sort(by); other.sort(by);
        return new OdbDtos.LayersFilesResponse(board, top, bottom, other);
    }

    /** Één layer → rijen. */
    public RowsResponse rows(MultipartFile file, String layer, String side) throws IOException {
        List<String> names  = listAllTarPaths(file);
        String board        = detectBoard(names);
        String rootPrefix   = detectRootPrefix(names);

        if (layer != null && layer.toLowerCase(Locale.ROOT).startsWith("comp_")) {
            String componentsPath = "steps/" + board + "/layers/" + layer + "/components";
            List<String> lines = readTextFileFromTgz(file, componentsPath);
            if (lines.isEmpty() && !rootPrefix.isEmpty())
                lines = readTextFileFromTgz(file, rootPrefix + componentsPath);

            if (!lines.isEmpty()) {
                List<ComponentInstance> instances = parseComponentsToInstances(lines, side);
                instances.forEach(ci -> ci.sourceLayer = layer);
                List<Row> rows = groupInstancesToRows(instances);
                enrichFromDbIfAvailable(rows);
                return new RowsResponse(rows, null);
            }
        }

        // Niet-component layer: toon gewoon bestanden (mounting leeg laten)
        String prefixA = "steps/" + board + "/layers/" + layer + "/";
        String prefixB = rootPrefix + prefixA;
        List<String> files = new ArrayList<>();
        for (String n : names) if ((n.startsWith(prefixA)||n.startsWith(prefixB)) && !n.endsWith("/")) files.add(n);

        List<Row> rows = new ArrayList<>();
        for (String full : files) {
            String fileName = full.substring(full.lastIndexOf('/') + 1);
            Row r = emptyRow();
            r.partName    = fileName;
            r.description = rootPrefix.isEmpty()? full : (rootPrefix+full);
            r.sourceLayer = layer;
            // r.mounting blijft leeg; side ook leeg (geen component)
            rows.add(r);
        }
        rows.sort(Comparator.comparing(o -> nvl(o.partName).toLowerCase(Locale.ROOT)));
        enrichFromDbIfAvailable(rows);
        return new RowsResponse(rows, null);
    }

    /** Meerdere layers tegelijk (bijv. comp_+_top en comp_+_bot). */
    public RowsResponse rowsMulti(MultipartFile file, List<String> layers) throws IOException {
        if (layers == null || layers.isEmpty()) return new RowsResponse(new ArrayList<>(), null);

        List<String> names  = listAllTarPaths(file);
        String board        = detectBoard(names);
        String rootPrefix   = detectRootPrefix(names);

        List<ComponentInstance> all = new ArrayList<>();

        for (String layer : layers) {
            if (layer == null) continue;
            boolean isComp = layer.toLowerCase(Locale.ROOT).startsWith("comp_");
            String componentsPath = "steps/" + board + "/layers/" + layer + "/components";

            if (isComp) {
                List<String> lines = readTextFileFromTgz(file, componentsPath);
                if (lines.isEmpty() && !rootPrefix.isEmpty())
                    lines = readTextFileFromTgz(file, rootPrefix + componentsPath);
                if (!lines.isEmpty()) {
                    String sideHint = isTop(layer) ? "top" : (isBottom(layer) ? "bot" : "");
                    List<ComponentInstance> instances = parseComponentsToInstances(lines, sideHint);
                    instances.forEach(ci -> ci.sourceLayer = layer);
                    all.addAll(instances);
                }
            } else {
                // optioneel: files als "neutrale" rows toevoegen
                String prefixA = "steps/" + board + "/layers/" + layer + "/";
                String prefixB = rootPrefix + prefixA;
                for (String n : names) {
                    if ((n.startsWith(prefixA)||n.startsWith(prefixB)) && !n.endsWith("/")) {
                        String fileName = n.substring(n.lastIndexOf('/')+1);
                        all.add(new ComponentInstance(
                                fileName, fileName, "", 0, "", "",
                                rootPrefix.isEmpty()? n : (rootPrefix+n),
                                makeKey(fileName, "0", "", ""), layer
                        ));
                    }
                }
            }
        }

        List<Row> rows = groupInstancesToRows(all);
        enrichFromDbIfAvailable(rows);
        return new RowsResponse(rows, null);
    }

    /** RefDes-lijst bij een key (voor de Qty-click modal). */
    public Map<String, Object> refs(MultipartFile file, String layer, String side, String key) throws IOException {
        List<String> names  = listAllTarPaths(file);
        String board        = detectBoard(names);
        String rootPrefix   = detectRootPrefix(names);
        String componentsPath = "steps/" + board + "/layers/" + layer + "/components";

        List<String> lines = readTextFileFromTgz(file, componentsPath);
        if (lines.isEmpty() && !rootPrefix.isEmpty())
            lines = readTextFileFromTgz(file, rootPrefix + componentsPath);

        List<ComponentInstance> instances = parseComponentsToInstances(lines, side);
        List<String> refs = instances.stream()
                .filter(ci -> Objects.equals(ci.groupKey, key))
                .map(ci -> ci.ref)
                .sorted(this::alphaNumCompare)
                .toList();
        return Map.of("key", key, "refs", refs);
    }

    /* ====================== PARSER ====================== */

    private static final Pattern CMP_PATTERN = Pattern.compile(
            "^\\s*CMP\\s+\\d+\\s+[+-]?\\d*\\.?\\d+\\s+[+-]?\\d*\\.?\\d+\\s+[+-]?\\d*\\.?\\d+\\s+[NM]\\s+([A-Za-z0-9_.\\-]+)\\s+([A-Za-z0-9_.\\-\\[\\]]+).*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PRP_PATTERN = Pattern.compile(
            "^\\s*PRP\\s+([A-Za-z0-9_.\\-]+)\\s+'(.*)'\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TOPBOT_PATTERN = Pattern.compile(
            "^\\s*(TOP|BOT)\\b.*$", Pattern.CASE_INSENSITIVE);

    private static class Ctx {
        String ref, geometry, partNo, type, description;
        String side = "";          // "top" / "bot"
        int    pins = 0;
    }

    private List<ComponentInstance> parseComponentsToInstances(List<String> lines, String sideFromArg) {
        List<ComponentInstance> out = new ArrayList<>();
        Ctx c = null;

        for (String raw : lines) {
            String s = raw.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;

            Matcher mCmp = CMP_PATTERN.matcher(s);
            if (mCmp.find()) {
                flushCtx(c, sideFromArg, out);
                c = new Ctx();
                c.ref      = mCmp.group(1);
                c.geometry = mCmp.group(2);
                continue;
            }

            Matcher mPrp = PRP_PATTERN.matcher(s);
            if (mPrp.find()) {
                String k = mPrp.group(1);
                if (k.startsWith(".")) k = k.substring(1);
                k = k.toUpperCase(Locale.ROOT);
                String v = mPrp.group(2);
                if (c == null) c = new Ctx();
                switch (k) {
                    case "PART_NO"     -> c.partNo = v;
                    case "TYPE"        -> c.type = v;
                    case "DESCRIPTION" -> c.description = v;
                    case "CELL_NAME"   -> { if (c.geometry == null || c.geometry.isBlank()) c.geometry = v; }
                }
                continue;
            }

            Matcher tb = TOPBOT_PATTERN.matcher(s);
            if (tb.find()) {
                if (c == null) c = new Ctx();
                c.pins++;
                String u = s.toUpperCase(Locale.ROOT);
                if (u.startsWith("TOP")) c.side = "top";
                if (u.startsWith("BOT")) c.side = "bot";
            }
        }
        flushCtx(c, sideFromArg, out);
        return out;
    }

    private void flushCtx(Ctx c, String sideFromArg, List<ComponentInstance> out) {
        if (c == null || c.ref == null) return;

        String partName = firstNonEmpty(c.partNo, c.geometry, c.ref);
        String type     = firstNonEmpty(c.type, guessTypeFromRef(c.ref));
        String desc     = firstNonEmpty(c.description, buildDefaultDescription(partName, type));

        // SIDE = uit TOP/BOT, anders meegegeven hint
        String side = firstNonEmpty(c.side, sideFromArg);

        // MOUNTING TYPE = SMD/THT (niet gelijk aan side!)
        String mountingType = guessMountingType(desc, c.partNo, c.geometry);

        int pins    = c.pins;
        // KEY: groepeer op part + pins + side + mounting (geen type/description!)
        String key  = makeKey(partName, String.valueOf(pins), side, mountingType);

        out.add(new ComponentInstance(
                c.ref, partName, type, pins, side, mountingType, desc, key, null
        ));
    }

    /* ====================== GROUPING ====================== */

    private List<Row> groupInstancesToRows(List<ComponentInstance> instances) {
        Map<String, List<ComponentInstance>> grouped =
                instances.stream().collect(Collectors.groupingBy(ci -> ci.groupKey));

        List<Row> rows = new ArrayList<>(grouped.size());
        for (var e : grouped.entrySet()) {
            String key = e.getKey();
            List<ComponentInstance> comps = e.getValue();
            comps.sort(this::byRefAlphaNum);
            ComponentInstance f = comps.get(0);

            Row r = emptyRow();
            r.partName    = nvl(f.partName);
            r.type        = nvl(f.type);
            r.description = nvl(f.description);
            r.pins        = f.pins == 0 ? "" : String.valueOf(f.pins);

            r.side        = nvl(f.side);          // "top"/"bot"
            r.mounting    = nvl(f.mountingType);  // "SMD"/"THT"  <-- nooit "top/bot"

            r.qty         = String.valueOf(comps.size()); // qty = groepsgrootte
            r.key         = key;
            r.sourceLayer = nvl(f.sourceLayer);

            rows.add(r);
        }

        rows.sort(Comparator
                .comparing((Row r) -> nvl(r.type).toLowerCase(Locale.ROOT))
                .thenComparing(r -> nvl(r.partName).toLowerCase(Locale.ROOT))
                .thenComparing(r -> nvl(r.side).toLowerCase(Locale.ROOT)));
        return rows;
    }

    private Row emptyRow(){
        Row r = new Row();
        r.partName=r.odoo=r.alt=r.pins=r.stock=r.type=r.mounting=r.side=r.description=r.qty=r.key=r.sourceLayer=r.nop=r.hand=r.skip="";
        return r;
    }

    /* ====================== MAPPING/HELPERS ====================== */

    /** Bepaal SMD/THT op basis van omschrijving/footprint/partno. */
    private static String guessMountingType(String desc, String partNo, String geomOrCell) {
        String s = (nvl(desc) + " " + nvl(partNo) + " " + nvl(geomOrCell)).toUpperCase(Locale.ROOT);

        // duidelijke through-hole hints
        if (s.contains("THT") || s.contains("THROUGH") || s.contains("PTH") || s.matches(".*\\bTH\\b.*"))
            return "THT";

        // duidelijke SMD hints
        if (s.contains("SMD")) return "SMD";

        // footprint / package hints voor SMD
        if (s.matches(".*\\b(01005|0201|0402|0603|0805|1206|1210|2010|2512|SOT|SC70|QFN|QFP|TQFP|BGA|LGA|DFN|MSOP|TSOP)\\b.*"))
            return "SMD";

        // default
        return "SMD";
    }

    private static String guessTypeFromRef(String ref){
        if (ref == null) return null;
        String r = ref.toUpperCase(Locale.ROOT);
        if (r.startsWith("R"))  return "Resi";
        if (r.startsWith("C"))  return "Capa";
        if (r.startsWith("L"))  return "Indu";
        if (r.startsWith("D"))  return "Diode";
        if (r.startsWith("U"))  return "IC";
        if (r.startsWith("Q"))  return "Trans";
        if (r.startsWith("TP")) return "TestPoint";
        return null;
    }

    private static String buildDefaultDescription(String part, String type){
        if (part == null && type == null) return "";
        if (type == null) return part;
        if (part == null) return type;
        return type + " " + part.replace('_',' ');
    }

    private int byRefAlphaNum(ComponentInstance a, ComponentInstance b){ return alphaNumCompare(a.ref, b.ref); }
    private static final Pattern REF_SPLIT = Pattern.compile("^([A-Za-z]+)(\\d+)$");
    private int alphaNumCompare(String a, String b){
        if (a == null) a=""; if (b == null) b="";
        Matcher ma = REF_SPLIT.matcher(a), mb = REF_SPLIT.matcher(b);
        if (ma.matches() && mb.matches()) {
            int cmp = ma.group(1).compareToIgnoreCase(mb.group(1));
            if (cmp != 0) return cmp;
            int na = Integer.parseInt(ma.group(2)), nb = Integer.parseInt(mb.group(2));
            return Integer.compare(na, nb);
        }
        return String.CASE_INSENSITIVE_ORDER.compare(a,b);
    }

    private static String makeKey(String... parts){
        String joined = Arrays.stream(parts).map(s -> s==null?"":s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("|"));
        return Integer.toHexString(joined.hashCode());
    }

    private static String nvl(String s){ return s==null ? "" : s; }
    private static String firstNonEmpty(String... values) {
        for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    /* ====================== TAR/IO ====================== */

    @SuppressWarnings("deprecation")
    private List<String> listAllTarPaths(MultipartFile file) throws IOException {
        List<String> result = new ArrayList<>();
        try (InputStream raw = new BufferedInputStream(file.getInputStream());
             InputStream gz = new GZIPInputStream(raw);
             TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
            TarArchiveEntry e;
            while ((e = tar.getNextTarEntry()) != null) {
                String name = e.getName().replace('\\','/');
                if (name.startsWith("./")) name = name.substring(2);
                result.add(name);
            }
        }
        return result;
    }

    private String detectBoard(List<String> paths) {
        Pattern p = Pattern.compile("(^|.*/?)steps/([^/]+)/");
        Set<String> set = new LinkedHashSet<>();
        for (String s : paths) { Matcher m = p.matcher(s); if (m.find()) set.add(m.group(2)); }
        if (set.isEmpty()) return "board1";
        for (String b : set) if ("board1".equalsIgnoreCase(b)) return b;
        return set.iterator().next();
    }

    private Set<String> collectLayerDirs(List<String> paths, String board) {
        Pattern p = Pattern.compile("(^|.*/?)steps/" + Pattern.quote(board) + "/layers/([^/]+)/");
        Set<String> res = new LinkedHashSet<>();
        for (String s : paths) { Matcher m = p.matcher(s); if (m.find()) res.add(m.group(2)); }
        return res;
    }

    private String detectRootPrefix(List<String> names) {
        for (String n : names) {
            int idx = n.indexOf("steps/");
            if (idx > 0) {
                String prefix = n.substring(0, idx);
                if (prefix.endsWith("/")) return prefix;
            }
        }
        return "";
    }

    private boolean isBottom(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("bottom") || n.equals("smb") || n.equals("ssb")
                || n.contains("_+_bot") || n.contains("_bot") || n.endsWith("bot");
    }

    private boolean isTop(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("top") || n.equals("smt") || n.equals("sst")
                || n.contains("_+_top") || n.contains("_top") || n.endsWith("top");
    }

    @SuppressWarnings("deprecation")
    private List<String> readTextFileFromTgz(MultipartFile tgz, String wantedPath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream raw = new BufferedInputStream(tgz.getInputStream());
             InputStream gz = new GZIPInputStream(raw);
             TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {

            TarArchiveEntry e;
            while ((e = tar.getNextTarEntry()) != null) {
                String name = e.getName().replace('\\','/').replaceFirst("^\\./","");
                if (name.equalsIgnoreCase(wantedPath)) {
                    try (Scanner sc = new Scanner(tar, StandardCharsets.UTF_8)) {
                        while (sc.hasNextLine()) lines.add(sc.nextLine());
                    }
                    break;
                }
            }
        }
        return lines;
    }

    /* ====================== MODEL ====================== */

    private static class ComponentInstance {
        final String ref, partName, type, side, mountingType, description, groupKey;
        final int pins;
        String sourceLayer;

        ComponentInstance(String ref, String partName, String type, int pins,
                          String side, String mountingType, String description,
                          String groupKey, String sourceLayer) {
            this.ref = ref;
            this.partName = partName;
            this.type = type;
            this.pins = pins;
            this.side = side;                 // "top"/"bot"
            this.mountingType = mountingType; // "SMD"/"THT"
            this.description = description;
            this.groupKey = groupKey;
            this.sourceLayer = sourceLayer;
        }
    }

    /* ====================== ENRICH ====================== */

    /** Verrijk uit DB waar mogelijk; qty NIET overschrijven. */
    private void enrichFromDbIfAvailable(List<Row> rows){
        if (rows == null || rows.isEmpty()) return;

        if (partsRepo != null) {
            Set<String> keys = rows.stream()
                    .map(r -> stripExt(r.partName))
                    .filter(k -> k != null && !k.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Map<String, PartsLookupRepository.MentorInfo> mentor = partsRepo.fetchMentorByNames(keys);
            for (Row r : rows) {
                String k = stripExt(r.partName);
                PartsLookupRepository.MentorInfo mi = mentor.get(k);
                if (mi != null) {
                    if (mi.mentorPartName != null) r.partName = mi.mentorPartName;
                    if (mi.mentorDescription != null) r.description = mi.mentorDescription;
                }
            }

            Map<String, PartsLookupRepository.PartInfo> info = partsRepo.fetchByNumbers(keys);
            for (Row r : rows) {
                String k = stripExt(r.partName);
                PartsLookupRepository.PartInfo pi = info.get(k);
                if (pi != null) r.odoo = nvl(pi.navName);
            }
        }

        // qty is al gezet in groupInstancesToRows(); niet aanpassen
        // tijdelijk: Stock = Qty als stock nog leeg is
        for (Row r : rows) {
            if (isBlank(r.stock) && !isBlank(r.qty)) r.stock = r.qty;
        }
    }

    private static String stripExt(String s) {
        return s == null ? "" : s.replaceAll("\\.[^.]+$", "");
    }
}
