package veds.vedsprodtoolbackend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO’s voor ODB API responses.
 *
 * Let op: velden zijn public voor makkelijke (de)serialisatie.
 */
public class OdbDtos {

    /** Kleine naam+pad pair, voor de layer-keuzes. */
    public static class NamePath {
        public String name;
        public String path;

        public NamePath() {}
        public NamePath(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    /** Response voor de layer-chooser modal. */
    public static class LayersFilesResponse {
        public String board;
        public List<NamePath> top = new ArrayList<>();
        public List<NamePath> bottom = new ArrayList<>();
        public List<NamePath> other = new ArrayList<>();

        public LayersFilesResponse() {}

        public LayersFilesResponse(String board, List<NamePath> top, List<NamePath> bottom, List<NamePath> other) {
            this.board = board;
            if (top != null)    this.top    = top;
            if (bottom != null) this.bottom = bottom;
            if (other != null)  this.other  = other;
        }
    }

    /** Rij in de parts table (moet aansluiten op je frontend). */
    public static class Row {
        public String partName = "";
        public String odoo = "";
        public String alt = "";
        public String pins = "";
        public String stock = "";
        public String type = "";
        public String mounting = "";    // "SMD" / "THT"
        public String side = "";        // "top" / "bot"
        public String description = "";

        public String qty = "";         // aantal in deze groep
        public String key = "";         // grouping key (voor refs)
        public String sourceLayer = ""; // uit welke layer

        // checkbox-kolommen
        public String nop = "";
        public String hand = "";
        public String skip = "";

        public Row() {}
    }

    /** Optionele PDF payload als data-URL (nu niet gebruikt, maar interface-vriendelijk). */
    public static class PdfAttachment {
        public String filename;
        public String dataUrl;

        public PdfAttachment() {}
        public PdfAttachment(String filename, String dataUrl) {
            this.filename = filename;
            this.dataUrl = dataUrl;
        }
    }

    /** Response met rijen (+ optionele PDF). */
    public static class RowsResponse {
        public List<Row> rows = new ArrayList<>();
        public PdfAttachment pdf; // kan null zijn

        public RowsResponse() {}

        public RowsResponse(List<Row> rows, PdfAttachment pdf) {
            if (rows != null) this.rows = rows;
            this.pdf = pdf;
        }
    }
}
