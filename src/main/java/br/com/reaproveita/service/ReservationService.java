package br.com.reaproveita.service;

import br.com.reaproveita.dto.ReservationCreateRequest;
import br.com.reaproveita.dto.ReservationResponse;
import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {
    private final ReservationRepository reservations;
    private final DonationRepository donations;
    private final OrganizationRepository organizations;
    private final UserRepository users;

    public ReservationService(ReservationRepository reservations, DonationRepository donations,
            OrganizationRepository organizations, UserRepository users) {
        this.reservations = reservations;
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

    private boolean isDonor(Reservation r, User u) {
        return admin(u) || r.getDonation().getOrganization().getOwner().getId().equals(u.getId());
    }

    private boolean isReceiver(Reservation r, User u) {
        return admin(u) || r.getReceiverOrganization().getOwner().getId().equals(u.getId());
    }

    private void ensureVisible(Reservation r, User u) {
        if (!isDonor(r, u) && !isReceiver(r, u))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada");
    }

    private void ensureNotExpired(Donation d) {
        if (!d.getExpiresAt().isAfter(OffsetDateTime.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doacao vencida");
        if (!d.isSanitaryDeclaration())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Declaracao sanitaria ausente");
    }

    // A linha da doacao fica bloqueada ate o commit. O indice UNIQUE parcial do banco
    // fornece uma segunda protecao contra duas reservas ativas para a mesma doacao.
    @Transactional
    public ReservationResponse request(Long donationId, ReservationCreateRequest request, Authentication auth) {
        User user = current(auth);
        if (!admin(user) && user.getRole() != UserRole.RECEIVER)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil receptor necessario");

        Donation donation = donations.findLockedById(donationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doacao nao encontrada"));
        if (donation.getStatus() != DonationStatus.AVAILABLE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doacao indisponivel");
        ensureNotExpired(donation);

        Organization receiver = organizations.findById(request.receiverOrganizationId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada"));
        if (!admin(user) && !receiver.getOwner().getId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada");
        if (receiver.getType() != OrganizationType.RECEIVER)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organizacao nao e receptora");
        if (receiver.getId().equals(donation.getOrganization().getId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doador nao pode reservar propria doacao");

        donation.reserve();
        Reservation reservation = reservations.saveAndFlush(new Reservation(donation, receiver));
        donations.saveAndFlush(donation);
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(Long id, Authentication auth) {
        var user = current(auth);
        var reservation = reservations.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada"));
        ensureVisible(reservation, user);
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse confirm(Long id, Authentication auth) {
        var user = current(auth);
        var existing = reservations.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada"));
        Donation donation = donations.findLockedById(existing.getDonation().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doacao nao encontrada"));
        var r = reservations.findLockedById(id).orElseThrow();
        if (!isDonor(r, user))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada");
        if (r.getStatus() != ReservationStatus.REQUESTED || donation.getStatus() != DonationStatus.RESERVED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserva nao pode ser confirmada");
        ensureNotExpired(donation);
        r.confirm();
        return ReservationResponse.from(reservations.saveAndFlush(r));
    }

    @Transactional
    public ReservationResponse collect(Long id, Authentication auth) {
        var user = current(auth);
        var existing = reservations.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada"));
        Donation donation = donations.findLockedById(existing.getDonation().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doacao nao encontrada"));
        var r = reservations.findLockedById(id).orElseThrow();
        if (!isDonor(r, user))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada");
        if (r.getStatus() != ReservationStatus.CONFIRMED || donation.getStatus() != DonationStatus.RESERVED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserva nao pode ser retirada");
        ensureNotExpired(donation);
        r.collect();
        donation.collect();
        donations.saveAndFlush(donation);
        return ReservationResponse.from(reservations.saveAndFlush(r));
    }

    @Transactional
    public ReservationResponse cancel(Long id, Authentication auth) {
        var user = current(auth);
        var existing = reservations.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada"));
        Donation donation = donations.findLockedById(existing.getDonation().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doacao nao encontrada"));
        var r = reservations.findLockedById(id).orElseThrow();
        ensureVisible(r, user);
        if (r.getStatus() != ReservationStatus.REQUESTED && r.getStatus() != ReservationStatus.CONFIRMED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserva nao pode ser cancelada");
        if (donation.getStatus() != DonationStatus.RESERVED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doacao nao esta reservada");
        r.cancel();
        donation.releaseReservation();
        donations.saveAndFlush(donation);
        return ReservationResponse.from(reservations.saveAndFlush(r));
    }
}