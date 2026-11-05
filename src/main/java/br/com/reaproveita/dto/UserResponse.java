package br.com.reaproveita.dto;

import br.com.reaproveita.model.User;
import br.com.reaproveita.model.UserRole;

public record UserResponse(Long id, String name, String email, UserRole role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
