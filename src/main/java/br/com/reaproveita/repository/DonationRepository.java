package br.com.reaproveita.repository;

import br.com.reaproveita.model.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import br.com.reaproveita.model.DonationStatus;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    List<Donation> findByStatusOrderByExpiresAtAsc(DonationStatus status);
}