package br.com.reaproveita.repository;

import br.com.reaproveita.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    List<Organization> findByOwnerId(Long ownerId);
}