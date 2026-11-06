package br.com.reaproveita.dto;

import br.com.reaproveita.model.FoodCategory;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Set;

public record ReceiverProfileRequest(
    @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal capacityKg,
    @NotNull Boolean availableForPickup,
    @NotEmpty Set<@NotNull FoodCategory> acceptedCategories
) { }
