package org.dnd.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class UserIdentifierResolver {

  private static final Pattern EMAIL_PATTERN = Pattern.compile(
          "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
          Pattern.CASE_INSENSITIVE
  );

  private final UserRepository userRepository;

  public Optional<UserEntity> find(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return Optional.empty();
    }

    String trimmedIdentifier = identifier.trim();

    if (!EMAIL_PATTERN.matcher(trimmedIdentifier).matches()) {
      return userRepository.findByName(trimmedIdentifier);
    }

    Optional<UserEntity> userByEmail =
            userRepository.findByEmail(trimmedIdentifier.toLowerCase());

    if (userByEmail.isPresent()) {
      return userByEmail;
    }

    // A username is allowed to look like an email address.
    return userRepository.findByName(trimmedIdentifier);
  }

  public boolean matches(UserEntity user, String identifier) {
    return find(identifier)
            .map(resolvedUser -> resolvedUser.getId().equals(user.getId()))
            .orElse(false);
  }
}
