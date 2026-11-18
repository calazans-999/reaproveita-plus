
package br.com.reaproveita.dto;

import br.com.reaproveita.model.FoodCategory;
import java.math.BigDecimal;

public record AiFoodAnalysisResponse(
    FoodCategory category,
    BigDecimal quantityKg,
    String storageCondition,
    String explanation,
    boolean requiresReview
) {}
