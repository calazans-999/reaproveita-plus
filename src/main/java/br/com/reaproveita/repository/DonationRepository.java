package br.com.reaproveita.repository;

import br.com.reaproveita.model.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.reaproveita.model.DonationStatus;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    List<Donation> findByStatusOrderByExpiresAtAsc(DonationStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d where d.id = :id")
    Optional<Donation> findLockedById(@Param("id") Long id);

    boolean existsByOrganizationId(Long organizationId);
}