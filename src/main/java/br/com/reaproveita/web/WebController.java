package br.com.reaproveita.web;

import br.com.reaproveita.dto.*;
import br.com.reaproveita.model.*;
import br.com.reaproveita.service.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebController {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final UserService users;
    private final OrganizationService organizations;
    private final DonationService donations;
    private final ReservationService reservations;
    private final ReceiverProfileService profiles;
    private final WebPageService pages;
    private final Validator validator;

    public WebController(UserService users, OrganizationService organizations, DonationService donations,
            ReservationService reservations, ReceiverProfileService profiles, WebPageService pages, Validator validator) {
        this.users = users;
        this.organizations = organizations;
        this.donations = donations;
        this.reservations = reservations;
        this.profiles = profiles;
        this.pages = pages;
        this.validator = validator;
    }

    @GetMapping("/") String home() { return "redirect:/dashboard"; }
    @GetMapping("/login") String login() { return "login"; }
    @GetMapping("/register") String registerForm() { return "register"; }

    @PostMapping("/register")
    String register(@RequestParam String name, @RequestParam String email,
            @RequestParam String password, @RequestParam UserRole role, RedirectAttributes flash) {
        if (role == UserRole.ADMIN) return fail(flash, "/register", "Cadastro ADMIN nao permitido");
        try {
            var dto = valid(new RegisterRequest(name, email, password, role));
            users.register(dto);
            flash.addFlashAttribute("notice", "Conta criada. Entre para continuar.");
            return "redirect:/login";
        } catch (RuntimeException ex) { return fail(flash, "/register", ex.getMessage()); }
    }

    @ModelAttribute("loggedEmail") String email(Principal principal) {
        return principal == null ? "" : principal.getName();
    }

    @GetMapping("/dashboard")
    String dashboard(Authentication auth, Model model) {
        common(auth, model, "dashboard");
        model.addAttribute("organizations", pages.organizations(auth));
        model.addAttribute("donations", pages.availableDonations(auth));
        model.addAttribute("myDonations", pages.ownDonations(auth));
        model.addAttribute("reservations", pages.visibleReservations(auth));
        return "dashboard";
    }

    @GetMapping("/organizations")
    String organizations(Authentication auth, Model model) {
        common(auth, model, "organizations");
        model.addAttribute("organizations", pages.organizations(auth));
        return "organizations";
    }

    @PostMapping("/organizations")
    String createOrganization(@RequestParam String name, @RequestParam OrganizationType type,
            @RequestParam String city, @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            Authentication auth, RedirectAttributes flash) {
        try {
            organizations.create(valid(new OrganizationCreateRequest(name, type, city, latitude, longitude)), auth);
            return ok(flash, "/organizations", "Organizacao cadastrada");
        } catch (RuntimeException ex) { return fail(flash, "/organizations", ex.getMessage()); }
    }

    @PostMapping("/organizations/{id}/update")
    String updateOrganization(@PathVariable Long id, @RequestParam String name, @RequestParam String city,
            @RequestParam(required=false) BigDecimal latitude, @RequestParam(required=false) BigDecimal longitude,
            Authentication auth, RedirectAttributes flash) {
        try {
            organizations.update(id, valid(new OrganizationUpdateRequest(name, city, latitude, longitude)), auth);
            return ok(flash, "/organizations", "Organizacao atualizada");
        } catch (RuntimeException ex) { return fail(flash, "/organizations", ex.getMessage()); }
    }

    @PostMapping("/organizations/{id}/delete")
    String deleteOrganization(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        try { organizations.delete(id,auth); return ok(flash, "/organizations", "Organizacao excluida"); }
        catch (RuntimeException ex) { return fail(flash, "/organizations", ex.getMessage()); }
    }

    @GetMapping("/donations")
    String donations(Authentication auth, Model model) {
        common(auth, model, "donations");
        model.addAttribute("available", pages.availableDonations(auth));
        model.addAttribute("mine", pages.ownDonations(auth));
        model.addAttribute("organizations", pages.organizations(auth));
        model.addAttribute("categories", FoodCategory.values());
        return "donations";
    }

    @PostMapping("/donations")
    String createDonation(@RequestParam Long organizationId, @RequestParam String title,
            @RequestParam FoodCategory category, @RequestParam BigDecimal quantityKg,
            @RequestParam String expiresAt, @RequestParam String storageCondition,
            @RequestParam(defaultValue="false") boolean sanitaryDeclaration,
            Authentication auth, RedirectAttributes flash) {
        try {
            var dto = valid(new DonationCreateRequest(organizationId, title, category, quantityKg,
                LocalDateTime.parse(expiresAt).atZone(ZONE).toOffsetDateTime(), storageCondition, sanitaryDeclaration));
            donations.create(dto,auth);
            return ok(flash, "/donations", "Doacao cadastrada");
        } catch (RuntimeException ex) { return fail(flash, "/donations", ex.getMessage()); }
    }

    @PostMapping("/donations/{id}/update")
    String updateDonation(@PathVariable Long id, @RequestParam String title,
            @RequestParam FoodCategory category, @RequestParam BigDecimal quantityKg,
            @RequestParam String expiresAt, @RequestParam String storageCondition,
            @RequestParam(defaultValue="false") boolean sanitaryDeclaration,
            Authentication auth, RedirectAttributes flash) {
        try {
            var dto = valid(new DonationUpdateRequest(title, category, quantityKg,
                LocalDateTime.parse(expiresAt).atZone(ZONE).toOffsetDateTime(), storageCondition, sanitaryDeclaration));
            donations.update(id, dto, auth);
            return ok(flash, "/donations", "Doacao atualizada");
        } catch (RuntimeException ex) { return fail(flash, "/donations", ex.getMessage()); }
    }

    @PostMapping("/donations/{id}/cancel")
    String cancelDonation(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        try { donations.cancel(id,auth); return ok(flash,"/donations","Doacao cancelada"); }
        catch(RuntimeException ex){ return fail(flash,"/donations",ex.getMessage()); }
    }

    @GetMapping("/reservations")
    String reservations(Authentication auth, Model model) {
        common(auth,model,"reservations");
        model.addAttribute("reservations", pages.visibleReservations(auth));
        model.addAttribute("available", pages.availableDonations(auth));
        model.addAttribute("organizations", pages.organizations(auth));
        return "reservations";
    }

    @PostMapping("/reservations")
    String requestReservation(@RequestParam Long donationId, @RequestParam Long receiverOrganizationId,
            Authentication auth, RedirectAttributes flash) {
        try {
            reservations.request(donationId, valid(new ReservationCreateRequest(receiverOrganizationId)), auth);
            return ok(flash, "/reservations", "Reserva solicitada");
        } catch(RuntimeException ex) { return fail(flash,"/reservations",ex.getMessage()); }
    }

    @PostMapping("/reservations/{id}/{action}")
    String reservationAction(@PathVariable Long id, @PathVariable String action,
            Authentication auth, RedirectAttributes flash) {
        try {
            switch(action) {
                case "confirm" -> reservations.confirm(id,auth);
                case "collect" -> reservations.collect(id,auth);
                case "cancel" -> reservations.cancel(id,auth);
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acao invalida");
            }
            return ok(flash,"/reservations","Reserva atualizada");
        } catch(RuntimeException ex) { return fail(flash,"/reservations",ex.getMessage()); }
    }

    @GetMapping("/recommendations")
    String recommendations(Authentication auth, Model model,
            @RequestParam(required=false) Long organizationId, @RequestParam(required=false) Long donationId) {
        common(auth,model,"recommendations");
        model.addAttribute("organizations", pages.organizations(auth));
        model.addAttribute("myDonations", pages.ownDonations(auth));
        model.addAttribute("selectedOrganization", organizationId);
        model.addAttribute("selectedDonation", donationId);
        try {
            if (organizationId != null)
                model.addAttribute("recommendations", pages.forReceiver(organizationId,auth));
            else if (donationId != null)
                model.addAttribute("recommendations", pages.forDonation(donationId,auth));
        } catch(RuntimeException ex) { model.addAttribute("error", ex.getMessage()); }
        return "recommendations";
    }

    @PostMapping("/receiver-profiles")
    String saveProfile(@RequestParam Long organizationId, @RequestParam BigDecimal capacityKg,
            @RequestParam(defaultValue="false") boolean availableForPickup,
            @RequestParam(required=false) Set<FoodCategory> acceptedCategories,
            Authentication auth, RedirectAttributes flash) {
        try {
            profiles.upsert(organizationId, valid(new ReceiverProfileRequest(capacityKg,
                availableForPickup, acceptedCategories == null ? Set.of() : acceptedCategories)),auth);
            return ok(flash, "/recommendations?organizationId="+organizationId, "Perfil atualizado");
        } catch(RuntimeException ex) { return fail(flash,"/recommendations",ex.getMessage()); }
    }

    private void common(Authentication auth, Model model, String section) {
        model.addAttribute("role",pages.role(auth).name());
        model.addAttribute("active",section);
    }

    private <T> T valid(T value) {
        Set<ConstraintViolation<T>> errors = validator.validate(value);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.iterator().next().getMessage());
        return value;
    }

    private String ok(RedirectAttributes flash,String target,String message) {
        flash.addFlashAttribute("notice",message); return "redirect:"+target;
    }
    private String fail(RedirectAttributes flash,String target,String message) {
        flash.addFlashAttribute("error",message == null ? "Operacao invalida" : message);
        return "redirect:"+target;
    }
}
