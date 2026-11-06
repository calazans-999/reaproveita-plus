package br.com.reaproveita.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "organizations")
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationType type;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false)
    private boolean verified = false;

    protected Organization() {}

    public Organization(User owner, String name, OrganizationType type, String city) {
        this.owner = owner;
        this.name = name;
        this.type = type;
        this.city = city;
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getName() { return name; }
    public OrganizationType getType() { return type; }
    public String getCity() { return city; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public boolean isVerified() { return verified; }
    public void updateDetails(String name, String city, BigDecimal latitude, BigDecimal longitude) {
        this.name = name;
        this.city = city;
        setCoordinates(latitude, longitude);
    }
    public void setCoordinates(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
