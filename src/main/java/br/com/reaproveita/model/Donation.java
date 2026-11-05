package br.com.reaproveita.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "donations")
public class Donation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodCategory category;

    @Column(name = "quantity_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantityKg;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "storage_condition", nullable = false, length = 255)
    private String storageCondition;

    @Column(name = "sanitary_declaration", nullable = false)
    private boolean sanitaryDeclaration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DonationStatus status = DonationStatus.AVAILABLE;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

    protected Donation() {}

    public Donation(Organization organization, String title, FoodCategory category,
                    BigDecimal quantityKg, OffsetDateTime expiresAt,
                    String storageCondition, boolean sanitaryDeclaration) {
        this.organization = organization;
        this.title = title;
        this.category = category;
        this.quantityKg = quantityKg;
        this.expiresAt = expiresAt;
        this.storageCondition = storageCondition;
        this.sanitaryDeclaration = sanitaryDeclaration;
    }

    public Long getId() { return id; }
    public Organization getOrganization() { return organization; }
    public String getTitle() { return title; }
    public FoodCategory getCategory() { return category; }
    public BigDecimal getQuantityKg() { return quantityKg; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public String getStorageCondition() { return storageCondition; }
    public boolean isSanitaryDeclaration() { return sanitaryDeclaration; }
    public DonationStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}