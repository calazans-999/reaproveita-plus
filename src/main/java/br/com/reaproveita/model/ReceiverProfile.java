package br.com.reaproveita.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "receiver_profiles")
public class ReceiverProfile {
    @Id
    @Column(name = "organization_id")
    private Long organizationId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "capacity_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal capacityKg;

    @Column(name = "available_for_pickup", nullable = false)
    private boolean availableForPickup;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "receiver_profile_categories", joinColumns = @JoinColumn(name = "organization_id"))
    @Column(name = "category", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Set<FoodCategory> acceptedCategories = new HashSet<>();

    protected ReceiverProfile() { }

    public ReceiverProfile(Organization organization, BigDecimal capacityKg,
                           boolean availableForPickup, Set<FoodCategory> categories) {
        this.organization = organization;
        update(capacityKg, availableForPickup, categories);
    }

    public void update(BigDecimal capacityKg, boolean availableForPickup, Set<FoodCategory> categories) {
        this.capacityKg = capacityKg;
        this.availableForPickup = availableForPickup;
        this.acceptedCategories.clear();
        this.acceptedCategories.addAll(categories);
    }

    public Long getOrganizationId() { return organizationId; }
    public Organization getOrganization() { return organization; }
    public BigDecimal getCapacityKg() { return capacityKg; }
    public boolean isAvailableForPickup() { return availableForPickup; }
    public Set<FoodCategory> getAcceptedCategories() { return Set.copyOf(acceptedCategories); }
}
