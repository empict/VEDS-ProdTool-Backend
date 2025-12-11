package veds.vedsprodtoolbackend.controller;

import org.springframework.web.bind.annotation.*;
import veds.vedsprodtoolbackend.odoo.OdooService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/odoo")
@CrossOrigin(origins = "*")
public class OdooSearchController {

    private final OdooService odooService;

    public OdooSearchController(OdooService odooService) {
        this.odooService = odooService;
    }

    /**
     * Search ODOO for products by default_code/name.
     * Returns:
     * [
     *   { id, default_code, name, x_studio_extra_omschrijving, lst_price, qty_available }
     * ]
     */
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam("q") String query) throws Exception {

        System.out.println("🔍 Search Odoo for: " + query);

        // 1) TEMPLATE PRODUCTS ophalen (bevat: id, default_code, name, description, price)
        List<Map<String, Object>> templates = odooService.searchTemplateProducts(query);

        // Als geen resultaten, return lege lijst
        if (templates.isEmpty()) return templates;

        // 2) STOCK ophalen per naam
        List<String> names = templates.stream()
                .map(t -> (String) t.get("name"))
                .toList();

        Map<String, Double> stockMap = odooService.getStockForProducts(names);

        // 3) STOCK toevoegen aan elk product
        for (Map<String, Object> t : templates) {

            String name = (String) t.get("name");
            Double stock = stockMap.getOrDefault(name, 0d);

            // "qty_available" toevoegen voor frontend
            t.put("qty_available", stock);

            // Als Odoo prijs ontbreekt → zet 0.0 ipv null
            if (!t.containsKey("lst_price")) {
                t.put("lst_price", 0.0);
            }
        }

        return templates;
    }
}
