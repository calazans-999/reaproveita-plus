
package br.com.reaproveita.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiFoodAnalysisRequest(
    @NotBlank(message = "A descricao do alimento e obrigatoria")
    @Size(min = 5, max = 1000)
    String description
) {}
