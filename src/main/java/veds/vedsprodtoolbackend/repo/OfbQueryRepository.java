package veds.vedsprodtoolbackend.repo;

import veds.vedsprodtoolbackend.dto.OfbWithNavDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class OfbQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;
    public OfbQueryRepository(NamedParameterJdbcTemplate jdbc){ this.jdbc = jdbc; }

    /**
     * Zoek OFBParts met optionele filter q, hasNav en whitelist-sorting.
     * sort: ofbNumber | navNumber  (default ofbNumber)
     * dir : asc | desc             (default asc)
     */
    public Map<String,Object> search(String q, int page, int size,
                                     Boolean hasNav, String sort, String dir) {

        int startRow = page * size + 1;
        int endRow   = (page + 1) * size;

        // --- WHITELIST voor ORDER BY (voorkomt SQL injection)
        String orderCol = "o.OFBNumber";
        if (sort != null) {
            switch (sort.toLowerCase()) {
                case "navnumber" -> orderCol = "n.NAVNumber";
                case "ofbnumber" -> orderCol = "o.OFBNumber";
                default -> orderCol = "o.OFBNumber";
            }
        }
        String orderDir = "DESC".equalsIgnoreCase(dir) ? "DESC" : "ASC";

        String sqlList =
                "WITH x AS ( " +
                        "  SELECT o.OFBPartID AS OfbPartID, o.OFBNumber AS OfbNumber, o.GenericID AS GenericID, " +
                        "         o.NAVPartID AS NavPartID, n.NAVNumber AS NavNumber, " +
                        "         ROW_NUMBER() OVER (ORDER BY " + orderCol + " " + orderDir + ") rn " +
                        "  FROM dbo.OFBParts o " +
                        "  LEFT JOIN dbo.NAVParts n " +
                        "    ON (o.NAVPartID IS NOT NULL AND o.NAVPartID <> '' " +
                        "        AND CONVERT(nvarchar(36), n.NAVPartID) = o.NAVPartID) " +
                        "  WHERE (:q IS NULL OR o.OFBNumber LIKE '%' + :q + '%') " +
                        "    AND (:hasNav IS NULL " +
                        "         OR (:hasNav = 1 AND n.NAVNumber IS NOT NULL) " +
                        "         OR (:hasNav = 0 AND n.NAVNumber IS NULL)) " +
                        ") " +
                        "SELECT OfbPartID, OfbNumber, GenericID, NavPartID, NavNumber " +
                        "  FROM x WHERE rn BETWEEN :startRow AND :endRow";

        String sqlCount =
                "SELECT COUNT(*) " +
                        "FROM dbo.OFBParts o " +
                        "LEFT JOIN dbo.NAVParts n " +
                        "  ON (o.NAVPartID IS NOT NULL AND o.NAVPartID <> '' " +
                        "      AND CONVERT(nvarchar(36), n.NAVPartID) = o.NAVPartID) " +
                        "WHERE (:q IS NULL OR o.OFBNumber LIKE '%' + :q + '%') " +
                        "  AND (:hasNav IS NULL " +
                        "       OR (:hasNav = 1 AND n.NAVNumber IS NOT NULL) " +
                        "       OR (:hasNav = 0 AND n.NAVNumber IS NULL))";

        var p = new MapSqlParameterSource()
                .addValue("q", (q == null || q.isBlank()) ? null : q)
                .addValue("startRow", startRow)
                .addValue("endRow", endRow)
                .addValue("hasNav", hasNav);

        var items = jdbc.query(sqlList, p, (rs, i) -> new OfbWithNavDto(
                toUuid(rs.getString("OfbPartID")),
                rs.getString("OfbNumber"),
                toUuid(rs.getString("GenericID")),
                rs.getString("NavPartID"),
                rs.getString("NavNumber")
        ));
        long total = jdbc.queryForObject(sqlCount, p, Long.class);

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("page", page);
        out.put("size", size);
        out.put("total", total);
        return out;
    }

    // Helper: String (jTDS) -> UUID (null-safe)
    private static java.util.UUID toUuid(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;
        if (v.startsWith("{") && v.endsWith("}")) v = v.substring(1, v.length()-1);
        try { return java.util.UUID.fromString(v); }
        catch (IllegalArgumentException e) { return null; }
    }
}
