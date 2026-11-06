package br.com.reaproveita.service;

import br.com.reaproveita.dto.*;
import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationService {
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final DonationRepository donations;
    private final ReservationRepository reservations;

    public OrganizationService(OrganizationRepository organizations, UserRepository users,
                               DonationRepository donations, ReservationRepository reservations) {
        this.organizations = organizations;
        this.users = users;
        this.donations = donations;
        this.reservations = reservations;
    }

    private User currentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria");
        }
        return users.findByEmailIgnoreCase(auth.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
    }

    private boolean admin(User user) { return user.getRole() == UserRole.ADMIN; }

    private Organization accessible(Long id, User user) {
        var org = organizations.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada"));
        // 404 para nao revelar a existencia de organizacoes de terceiros.
        if (!admin(user) && !org.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada");
        }
        return org;
    }

    @Transactional
    public OrganizationResponse create(OrganizationCreateRequest request, Authentication auth) {
        var user = currentUser(auth);
        if (!admin(user) && !user.getRole().name().equals(request.type().name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tipo de organizacao nao permitido para seu perfil");
        }
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude e longitude devem ser informadas juntas");
        }
        var org = new Organization(user, request.name().trim(), request.type(), request.city().trim());
        org.setCoordinates(request.latitude(), request.longitude());
        return OrganizationResponse.from(organizations.saveAndFlush(org));
    }

    @Transactional(readOnly=true)
    public List<OrganizationResponse> list(Authentication auth) {
        var user = currentUser(auth);
        var result = admin(user) ? organizations.findAll() : organizations.findByOwnerId(user.getId());
        return result.stream().map(OrganizationResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public OrganizationResponse get(Long id, Authentication auth) {
        return OrganizationResponse.from(accessible(id, currentUser(auth)));
    }

    @Transactional
    public OrganizationResponse update(Long id, OrganizationUpdateRequest request, Authentication auth) {
        var org = accessible(id, currentUser(auth));
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude e longitude devem ser informadas juntas");
        }
        org.updateDetails(request.name().trim(), request.city().trim(), request.latitude(), request.longitude());
        return OrganizationResponse.from(organizations.saveAndFlush(org));
    }

    @Transactional
    public void delete(Long id, Authentication auth) {
        var org = accessible(id, currentUser(auth));
        if (donations.existsByOrganizationId(id) || reservations.existsByReceiverOrganizationId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organizacao possui doacoes ou reservas vinculadas");
        }
        organizations.delete(org);
        organizations.flush();
    }
}
