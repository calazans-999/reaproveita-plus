package br.com.reaproveita.service;

import br.com.reaproveita.dto.*;
import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DonationService {
    private final DonationRepository donations;
    private final OrganizationRepository organizations;
    private final UserRepository users;

    public DonationService(DonationRepository donations, OrganizationRepository organizations,
                           UserRepository users) {
        this.donations = donations;
        this.organizations = organizations;
        this.users = users;
    }

    private User current(Authentication auth) {
        if (auth == null || !auth.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria");
        return users.findByEmailIgnoreCase(auth.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
    }

    private boolean admin(User u) { return u.getRole() == UserRole.ADMIN; }

    private boolean owns(Donation d, User u) {
        return admin(u) || d.getOrganization().getOwner().getId().equals(u.getId());
    }

    private Organization allowedDonor(Long id, User u) {
        var o = organizations.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada"));
        if (o.getType() != OrganizationType.DONOR)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organizacao nao e doadora");
        if (!admin(u) && !o.getOwner().getId().equals(u.getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada");
        if (!admin(u) && u.getRole() != UserRole.DONOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil nao autorizado");
        return o;
    }

    private Donation editable(Long id, User u) {
        var d = donations.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doacao nao encontrada"));
        if (!owns(d, u))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Doacao nao encontrada");
        if (d.getStatus() != DonationStatus.AVAILABLE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doacao nao esta disponivel para alteracao");
        if (!d.getExpiresAt().isAfter(OffsetDateTime.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doacao vencida");
        return d;
    }

    @Transactional
    public DonationResponse create(DonationCreateRequest r, Authentication auth) {
        var u = current(auth);
        var org = allowedDonor(r.organizationId(), u);
        Donation d = new Donation(org, r.title().trim(), r.category(), r.quantityKg(),
            r.expiresAt(), r.storageCondition().trim(), r.sanitaryDeclaration());
        return DonationResponse.from(donations.saveAndFlush(d));
    }

    @Transactional(readOnly = true)
    public List<DonationResponse> list(FoodCategory category, Authentication auth) {
        current(auth);
        return donations.findByStatusOrderByExpiresAtAsc(DonationStatus.AVAILABLE).stream()
            .filter(d -> d.getExpiresAt().isAfter(OffsetDateTime.now()))
            .filter(Donation::isSanitaryDeclaration)
            .filter(d -> category == null || category == d.getCategory())
            .map(DonationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DonationResponse get(Long id, Authentication auth) {
        User u = current(auth);
        var d = donations.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doacao nao encontrada"));
        boolean visible = owns(d, u) || (d.getStatus() == DonationStatus.AVAILABLE
            && d.getExpiresAt().isAfter(OffsetDateTime.now()) && d.isSanitaryDeclaration());
        if (!visible) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Doacao nao encontrada");
        return DonationResponse.from(d);
    }

    @Transactional
    public DonationResponse update(Long id, DonationUpdateRequest r, Authentication auth) {
        var d = editable(id, current(auth));
        d.updateDetails(r.title().trim(), r.category(), r.quantityKg(), r.expiresAt(),
            r.storageCondition().trim(), r.sanitaryDeclaration());
        return DonationResponse.from(donations.saveAndFlush(d));
    }

    @Transactional
    public DonationResponse cancel(Long id, Authentication auth) {
        var d = editable(id, current(auth));
        d.cancel();
        return DonationResponse.from(donations.saveAndFlush(d));
    }
}
