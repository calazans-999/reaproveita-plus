package br.com.reaproveita.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donation_id", nullable = false)
    private Donation donation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_organization_id", nullable = false)
    private Organization receiverOrganization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.REQUESTED;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "reserved_at", nullable = false)
    private OffsetDateTime reservedAt = OffsetDateTime.now(ZoneOffset.UTC);

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    protected Reservation() {}

    public Reservation(Donation donation, Organization receiverOrganization) {
        this.donation = donation;
        this.receiverOrganization = receiverOrganization;
    }

    public void confirm() {
        if (status != ReservationStatus.REQUESTED) throw new IllegalStateException("Reserva nao esta solicitada");
        status = ReservationStatus.CONFIRMED;
    }

    public void collect() {
        if (status != ReservationStatus.CONFIRMED) throw new IllegalStateException("Reserva nao esta confirmada");
        status = ReservationStatus.COLLECTED;
        collectedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void cancel() {
        if (status != ReservationStatus.REQUESTED && status != ReservationStatus.CONFIRMED)
            throw new IllegalStateException("Reserva nao pode ser cancelada");
        status = ReservationStatus.CANCELLED;
    }

    public Long getId() { return id; }
    public Donation getDonation() { return donation; }
    public Organization getReceiverOrganization() { return receiverOrganization; }
    public ReservationStatus getStatus() { return status; }
    public OffsetDateTime getReservedAt() { return reservedAt; }
    public OffsetDateTime getCollectedAt() { return collectedAt; }
}