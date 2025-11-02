package org.example.liveplatform.dto;

import org.example.liveplatform.dao.entity.User;
import org.example.liveplatform.dao.entity.UserStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record UserResponse(
    Long id,
    String firstName,
    String lastName,
    String email,
    LocalDate dateOfBirth,
    UserStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

  public static UserResponse fromEntity(User user) {
    return new UserResponse(
        user.getId(),
        user.getFirstName(),
        user.getLastName(),
        user.getEmail(),
        user.getDateOfBirth(),
        user.getStatus(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
