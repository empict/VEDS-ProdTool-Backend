package veds.vedsprodtoolbackend.dto;

import java.util.UUID;

public record OfbWithNavDto(
        UUID ofbPartID,
        String ofbNumber,
        UUID genericID,
        String navPartID,
        String navNumber
) {}
