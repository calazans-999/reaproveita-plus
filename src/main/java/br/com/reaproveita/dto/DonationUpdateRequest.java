package br.com.reaproveita.dto;

import br.com.reaproveita.model.FoodCategory;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DonationUpdateRequest(
    @NotBlank @Size(max = 160) String title,
    @NotNull FoodCategory category,
    @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal quantityKg,
    @NotNull @Future OffsetDateTime expiresAt,
    @NotBlank @Size(max = 255) String storageCondition,
    @AssertTrue(message = "Declaracao sanitaria obrigatoria") boolean sanitaryDeclaration
) {}
