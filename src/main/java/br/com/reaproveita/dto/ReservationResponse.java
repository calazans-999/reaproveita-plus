package br.com.reaproveita.dto;

import br.com.reaproveita.model.Reservation;
import br.com.reaproveita.model.ReservationStatus;
import java.time.OffsetDateTime;

public record ReservationResponse(
    Long id, Long donationId, Long receiverOrganizationId,
    ReservationStatus status, OffsetDateTime reservedAt, OffsetDateTime collectedAt
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(r.getId(), r.getDonation().getId(),
            r.getReceiverOrganization().getId(), r.getStatus(), r.getReservedAt(), r.getCollectedAt());
    }
}