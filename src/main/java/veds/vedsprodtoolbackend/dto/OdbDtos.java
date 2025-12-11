package veds.vedsprodtoolbackend.dto;

import java.util.List;

public class OdbDtos {

    // -------------------------------------------------------------
    // NamePath for Top/Bottom selection
    // -------------------------------------------------------------
    public static class NamePath {
        public String name;
        public String path;

        public NamePath(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    // -------------------------------------------------------------
    // LayersFilesResponse
    // -------------------------------------------------------------
    public static class LayersFilesResponse {
        public String board;
        public List<NamePath> top;
        public List<NamePath> bottom;
        public List<NamePath> other;

        public LayersFilesResponse(String board,
                                   List<NamePath> top,
                                   List<NamePath> bottom,
                                   List<NamePath> other) {
            this.board = board;
            this.top = top;
            this.bottom = bottom;
            this.other = other;
        }
    }

    // -------------------------------------------------------------
    // Row = één component
    // -------------------------------------------------------------
    public static class Row {

        // 🔥 nieuwe velden (verplicht!)
        public String layer;              // bv. "top/comp_+_top/components"
        public List<String> refdesList;   // alle refdes: ["R1","R2","R3"]

        public String key;          // uniek ID: REF@layer
        public String refdes;       // hoofd-refdes (bv. R1)
        public String partName;
        public String description;
        public String mounting;
        public String qty;

        // Database-enrichment velden
        public String odoo;
        public String alt;
        public String stock;
        public String type;
        public String pins;

        public Row() {}
    }

    // -------------------------------------------------------------
    // RowsResponse wrapper
    // -------------------------------------------------------------
    public static class RowsResponse {
        public List<Row> rows;
        public String pdf;

        public RowsResponse(List<Row> rows, String pdf) {
            this.rows = rows;
            this.pdf = pdf;
        }
    }
    // -------------------------------------------------------------
    // RefdesResponse – gebruikt door /api/v1/odb/refs
    // -------------------------------------------------------------
    public static class RefdesResponse {
        public List<String> refs;

        public RefdesResponse(List<String> refs) {
            this.refs = refs;
        }
    }
}
