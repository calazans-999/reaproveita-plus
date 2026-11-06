package br.com.reaproveita.dto;

import jakarta.validation.constraints.NotNull;

public record ReservationCreateRequest(@NotNull Long receiverOrganizationId) {}