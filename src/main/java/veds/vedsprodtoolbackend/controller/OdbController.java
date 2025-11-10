package veds.vedsprodtoolbackend.controller;

import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import veds.vedsprodtoolbackend.dto.OdbDtos;
import veds.vedsprodtoolbackend.service.OdbService;

import java.io.IOException;
import java.util.*;

/**
 * REST-controller voor ODB endpoints.
 *
 * Base path: /api/v1/odb
 */
@RestController
@RequestMapping("/api/v1/odb")
@CrossOrigin(origins = "*") // pas aan indien nodig
public class OdbController {

    private final OdbService odbService;

    public OdbController(OdbService odbService) {
        this.odbService = odbService;
    }

    /**
     * POST /choices
     * Multipart (file): designodb.tgz
     * -> { board, top[], bottom[], other[] }
     */
    @PostMapping(
            path = "/choices",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public OdbDtos.LayersFilesResponse choices(@RequestPart("file") MultipartFile file) throws IOException {
        return odbService.choices(file);
    }

    /**
     * POST /rows
     * Multipart (file, layer, side)
     * - layer: bv. "comp_+_top"
     * - side:  "top"|"bottom"|"other" (optioneel, alleen hint)
     * -> { rows:[], pdf?:null }
     */
    @PostMapping(
            path = "/rows",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public OdbDtos.RowsResponse rows(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "layer", required = false) String layer,
            @RequestParam(value = "side",  required = false) String side
    ) throws IOException {
        return odbService.rows(file, layer, side);
    }

    /**
     * POST /rows-multi
     * Ondersteunt meerdere layers in één call.
     *
     * Multipart varianten:
     *  - herhaal 'layer' meerdere keren (layer=comp_+_top&layer=comp_+_bot)
     *  - of stuur één parameter 'layers' als CSV
     */
    @PostMapping(
            path = "/rows-multi",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public OdbDtos.RowsResponse rowsMulti(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "layer", required = false) List<String> layersRepeated,
            @RequestParam(value = "layers", required = false) String layersCsv
    ) throws IOException {

        List<String> layers = new ArrayList<>();
        if (layersRepeated != null && !layersRepeated.isEmpty()) {
            for (String s : layersRepeated) if (StringUtils.hasText(s)) layers.add(s.trim());
        }
        if (StringUtils.hasText(layersCsv)) {
            for (String s : layersCsv.split(",")) if (StringUtils.hasText(s)) layers.add(s.trim());
        }
        // fallback als niets meegegeven is
        if (layers.isEmpty()) return new OdbDtos.RowsResponse(Collections.emptyList(), null);

        return odbService.rowsMulti(file, layers);
    }

    /**
     * POST /refs
     * Haal de RefDes-lijst op voor een gegroepeerde rij via key.
     *
     * Form-data:
     * - file   (Multipart)
     * - layer  (string)
     * - side   (string, optioneel)
     * - key    (string) -> de groupKey uit de tabel
     */
    @PostMapping(
            path = "/refs",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> refs(
            @RequestPart("file") MultipartFile file,
            @RequestParam("layer") String layer,
            @RequestParam(value = "side", required = false) String side,
            @RequestParam("key") String key
    ) throws IOException {
        return odbService.refs(file, layer, side, key);
    }
}
