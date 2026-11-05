package br.com.reaproveita.repository;

import br.com.reaproveita.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByDonationId(Long donationId);
    List<Reservation> findByReceiverOrganizationId(Long organizationId);
}