package veds.vedsprodtoolbackend.controller;

import org.springframework.web.bind.annotation.*;
import veds.vedsprodtoolbackend.odoo.OdooService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
@CrossOrigin(origins = "*")
public class OdooController {

    private final OdooService odooService;

    public OdooController(OdooService odooService) {
        this.odooService = odooService;
    }

    /**
     * GET /api/v1/test/odoo-stock
     * Voorbeeld: test of de Odoo-verbinding werkt en voorraad kan uitlezen.
     */
    @GetMapping("/odoo-stock")
    public Map<String, Double> testOdooStock(
            @RequestParam(defaultValue = "10uF_25V_X5R_0603") String term
    ) throws IOException {
        System.out.println("🔍 Testing Odoo stock fetch for term: " + term);
        return odooService.getStockForProducts(List.of(term));
    }
}
