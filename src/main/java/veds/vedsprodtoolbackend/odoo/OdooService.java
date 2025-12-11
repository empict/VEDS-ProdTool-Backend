package veds.vedsprodtoolbackend.odoo;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.*;

@Service
public class OdooService {

    @Value("${odoo.url}")
    private String url;

    @Value("${odoo.db}")
    private String db;

    @Value("${odoo.username}")
    private String username;

    @Value("${odoo.password}")
    private String password;

    private Integer uid;

    /* ------------------------
     * LOGIN
     * ------------------------ */
    private Integer login() throws Exception {
        if (uid != null) return uid;

        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(new URL(url + "/xmlrpc/2/common"));

        XmlRpcClient client = new XmlRpcClient();

        Object result = client.execute(
                config,
                "authenticate",
                new Object[]{db, username, password, Collections.emptyMap()}
        );

        uid = (Integer) result;
        return uid;
    }

    /* ----------------------------------------------------------
     * SEARCH products (product.template)
     * NOW RETURNS: id, default_code, name, description, price, stock
     * ---------------------------------------------------------- */
    public List<Map<String, Object>> searchTemplateProducts(String term) throws Exception {

        Integer uid = login();

        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(new URL(url + "/xmlrpc/2/object"));

        XmlRpcClient client = new XmlRpcClient();

        // SEARCH
        Object[] ids = (Object[]) client.execute(
                config,
                "execute_kw",
                new Object[]{
                        db, uid, password,
                        "product.template", "search",
                        new Object[]{
                                new Object[]{
                                        new Object[]{"default_code", "ilike", term}
                                }
                        },
                        new HashMap<String, Object>() {{
                            put("limit", 20);
                        }}
                }
        );

        if (ids.length == 0) return List.of();

        // READ with price + stock
        Object[] results = (Object[]) client.execute(
                config,
                "execute_kw",
                new Object[]{
                        db, uid, password,
                        "product.template", "read",
                        new Object[]{ids},
                        new HashMap<String, Object>() {{
                            put("fields", new String[] {
                                    "id",
                                    "default_code",
                                    "name",
                                    "x_studio_extra_omschrijving",
                                    "list_price",        // ⭐ PRICE
                                    "qty_available"      // ⭐ STOCK
                            });
                        }}
                }
        );

        List<Map<String, Object>> out = new ArrayList<>();

        for (Object obj : results) {
            Map<String, Object> row = (Map<String, Object>) obj;

            Map<String, Object> mapped = new HashMap<>();
            mapped.put("id", row.get("id"));
            mapped.put("default_code", row.get("default_code"));
            mapped.put("name", row.get("name"));
            mapped.put("x_studio_extra_omschrijving", row.get("x_studio_extra_omschrijving"));
            mapped.put("lst_price", row.get("list_price"));        // normalize naming
            mapped.put("qty_available", row.get("qty_available")); // normalize naming

            out.add(mapped);
        }

        return out;
    }

    /* ----------------------------------------------------------
     * STOCK LOOKUP (product.product) - still available if needed
     * ---------------------------------------------------------- */
    public Double getStockForProduct(String name) {
        try {
            Integer uid = login();

            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setServerURL(new URL(url + "/xmlrpc/2/object"));
            XmlRpcClient client = new XmlRpcClient();

            Object[] ids = (Object[]) client.execute(
                    config,
                    "execute_kw",
                    new Object[]{
                            db, uid, password,
                            "product.product", "search",
                            new Object[]{
                                    new Object[]{ new Object[]{"name", "=", name } }
                            }
                    }
            );

            if (ids.length == 0) return 0d;

            Object[] products = (Object[]) client.execute(
                    config,
                    "execute_kw",
                    new Object[]{
                            db, uid, password,
                            "product.product", "read",
                            new Object[]{ids},
                            new HashMap<String, Object>() {{
                                put("fields", new String[]{"qty_available"});
                            }}
                    }
            );

            Map<String, Object> data = (Map<String, Object>) products[0];
            Object qty = data.get("qty_available");

            if (qty instanceof Double) return (Double) qty;
            if (qty instanceof Integer) return ((Integer) qty).doubleValue();
            return 0d;

        } catch (Exception e) {
            System.err.println("❌ Odoo stock error: " + e.getMessage());
            return 0d;
        }
    }

    public Map<String, Double> getStockForProducts(Iterable<String> names) {
        Map<String, Double> stockMap = new HashMap<>();
        for (String n : names) {
            stockMap.put(n, getStockForProduct(n));
        }
        return stockMap;
    }
}
