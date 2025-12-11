package veds.vedsprodtoolbackend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import veds.vedsprodtoolbackend.dto.OdbDtos;
import veds.vedsprodtoolbackend.service.OdbService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/odb")
public class OdbController {

    private final OdbService odbService;

    @Autowired
    public OdbController(OdbService odbService) {
        this.odbService = odbService;
    }

    @PostMapping("/choices")
    public OdbDtos.LayersFilesResponse choices(@RequestParam("file") MultipartFile file)
            throws IOException {
        return odbService.getChoices(file);
    }

    @PostMapping("/rows/multi")
    public OdbDtos.RowsResponse rowsMulti(
            @RequestParam("file") MultipartFile file,
            @RequestParam("layers") List<String> layers
    ) throws IOException {
        return odbService.getRowsMulti(file, layers);
    }

    @PostMapping("/rows")
    public OdbDtos.RowsResponse rowsSingle(
            @RequestParam("file") MultipartFile file,
            @RequestParam("layer") String layer
    ) throws IOException {
        return odbService.getRowsSingle(file, layer);
    }

    @PostMapping("/refs")
    public OdbDtos.RefdesResponse refs(
            @RequestParam("file") MultipartFile file,
            @RequestParam("layer") String layer,
            @RequestParam("part") String partName
    ) throws IOException {
        return odbService.getRefs(file, layer, partName);
    }
}