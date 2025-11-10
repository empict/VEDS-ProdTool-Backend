package veds.vedsprodtoolbackend.repo;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Batch-lookup van partinformatie uit je SQL Server.
 * Key = OFBNumber of NAVNumber (we vullen beide in dezelfde map).
 *
 * Let op: vervang de CAST(NULL ...) velden door jouw echte kolommen
 * (bijv. voorraad/stock, type, pins, alt). De code compileert zo al.
 */
@Repository
public class PartsLookupRepository {

    public static final class MentorInfo {
        public final String mentorPartName;
        public final String mentorDescription;
        public MentorInfo(String mentorPartName, String mentorDescription) {
            this.mentorPartName = mentorPartName;
            this.mentorDescription = mentorDescription;
        }
    }

    /** Haal NAV_MentorParts op per MentorPartName (batch). Key = MentorPartName */
    public Map<String, MentorInfo> fetchMentorByNames(Set<String> names) {
        if (names == null || names.isEmpty()) return java.util.Collections.emptyMap();

        String sql = """
        SELECT MentorPartName, MentorDescription
        FROM dbo.NAV_MentorParts
        WHERE MentorPartName IN (:names)
    """;

        var p = new MapSqlParameterSource().addValue("names", names);
        Map<String, MentorInfo> out = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            String name = rs.getString("MentorPartName");
            String desc = rs.getString("MentorDescription");
            if (name != null && !name.isBlank()) {
                out.put(name.trim(), new MentorInfo(name, desc));
            }
        });
        return out;
    }

    private final NamedParameterJdbcTemplate jdbc;
    public PartsLookupRepository(NamedParameterJdbcTemplate jdbc){ this.jdbc = jdbc; }

    public static final class PartInfo {
        public final String ofbNumber;  // bv. "20K_1%_0402"
        public final String navNumber;  // bv. "52125"
        public final String navName;    // → gebruik als ODOO Name
        public final String alt;        // alternatief (optioneel)
        public final String stock;      // voorraad (optioneel)
        public final String type;       // extra info (optioneel)
        public final String pins;       // extra info (optioneel)

        public PartInfo(String ofbNumber, String navNumber, String navName,
                        String alt, String stock, String type, String pins) {
            this.ofbNumber = ofbNumber;
            this.navNumber = navNumber;
            this.navName   = navName;
            this.alt       = alt;
            this.stock     = stock;
            this.type      = type;
            this.pins      = pins;
        }
    }

    /**
     * Haal info op voor alle opgegeven nummers. We matchen op OFBNumber of NAVNumber.
     * TODO: vervang CAST(NULL ...) door de juiste kolommen/joins uit jouw schema.
     */
    public Map<String, PartInfo> fetchByNumbers(Set<String> nums){
        if (nums == null || nums.isEmpty()) return Collections.emptyMap();

        String sql = """
          SELECT
              o.OFBNumber                         AS OfbNumber,
              o.NAVPartID                         AS NavPartId,
              n.NAVNumber                         AS NavNumber,
              n.NAVName                           AS NavName,
              CAST(NULL AS varchar(200))          AS Alt,   -- TODO: bv. uit PreferredNAVPart
              CAST(NULL AS varchar(50))           AS Stock, -- TODO: echte voorraadkolom
              CAST(NULL AS varchar(50))           AS Type,  -- TODO
              CAST(NULL AS varchar(50))           AS Pins   -- TODO
          FROM dbo.OFBParts o
          LEFT JOIN dbo.NAVParts n
            ON (o.NAVPartID IS NOT NULL AND o.NAVPartID <> ''
                AND CONVERT(nvarchar(36), n.NAVPartID) = o.NAVPartID)
          WHERE o.OFBNumber IN (:nums) OR n.NAVNumber IN (:nums)
        """;

        var p = new MapSqlParameterSource().addValue("nums", nums);

        Map<String, PartInfo> out = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            String ofb   = rs.getString("OfbNumber");
            String nav   = rs.getString("NavNumber");
            String navNm = rs.getString("NavName");
            String alt   = rs.getString("Alt");
            String stock = rs.getString("Stock");
            String type  = rs.getString("Type");
            String pins  = rs.getString("Pins");

            PartInfo pi = new PartInfo(ofb, nav, navNm, alt, stock, type, pins);

            if (ofb != null && !ofb.isBlank()) out.putIfAbsent(ofb.trim(), pi);
            if (nav != null && !nav.isBlank()) out.putIfAbsent(nav.trim(), pi);
        });
        return out;
    }
}
