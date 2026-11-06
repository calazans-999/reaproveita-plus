package br.com.reaproveita.service;

import br.com.reaproveita.dto.ReceiverProfileRequest;
import br.com.reaproveita.dto.ReceiverProfileResponse;
import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReceiverProfileService {
    private final ReceiverProfileRepository profiles;
    private final OrganizationRepository organizations;
    private final UserRepository users;

    public ReceiverProfileService(ReceiverProfileRepository profiles, OrganizationRepository organizations,
                                  UserRepository users) {
        this.profiles = profiles;
        this.organizations = organizations;
        this.users = users;
    }

    private Organization visibleReceiver(Long id, Authentication auth) {
        if (auth == null || !auth.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria");
        User user = users.findByEmailIgnoreCase(auth.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
        Organization org = organizations.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada"));
        if (user.getRole() != UserRole.ADMIN && !org.getOwner().getId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada");
        if (org.getType() != OrganizationType.RECEIVER)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organizacao nao e receptora");
        return org;
    }

    @Transactional
    public ReceiverProfileResponse upsert(Long organizationId, ReceiverProfileRequest request, Authentication auth) {
        Organization org = visibleReceiver(organizationId, auth);
        ReceiverProfile profile = profiles.findById(organizationId)
            .orElseGet(() -> new ReceiverProfile(org, request.capacityKg(),
                request.availableForPickup(), request.acceptedCategories()));
        profile.update(request.capacityKg(), request.availableForPickup(), request.acceptedCategories());
        return ReceiverProfileResponse.from(profiles.saveAndFlush(profile));
    }

    @Transactional(readOnly = true)
    public ReceiverProfileResponse get(Long organizationId, Authentication auth) {
        visibleReceiver(organizationId, auth);
        return profiles.findById(organizationId).map(ReceiverProfileResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil nao configurado"));
    }
}
