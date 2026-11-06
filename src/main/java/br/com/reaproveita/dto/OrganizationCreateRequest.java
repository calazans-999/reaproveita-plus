package br.com.reaproveita.dto;

import br.com.reaproveita.model.OrganizationType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record OrganizationCreateRequest(
    @NotBlank @Size(max=160) String name,
    @NotNull OrganizationType type,
    @NotBlank @Size(max=120) String city,
    @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer=3, fraction=6) BigDecimal latitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer=3, fraction=6) BigDecimal longitude
) {}
