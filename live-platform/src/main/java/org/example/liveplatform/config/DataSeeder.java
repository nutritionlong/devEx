package org.example.liveplatform.config;

import java.time.LocalDate;

import org.example.liveplatform.dao.UserRepository;
import org.example.liveplatform.dao.entity.User;
import org.example.liveplatform.dao.entity.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class DataSeeder {

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final UserRepository userRepository;

  public DataSeeder(
      UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    log.info("ApplicationReadyEvent received; seeding data if necessary");
    seedUsers();
  }

  private void seedUsers() {
    seedUser("Alice", "Wang", "alice.wang@example.com", LocalDate.of(1990, 1, 15), UserStatus.ACTIVE);
    seedUser("Bruno", "Dias", "bruno.dias@example.com", LocalDate.of(1985, 7, 3), UserStatus.INACTIVE);
    seedUser("Chen", "Li", "chen.li@example.com", null, UserStatus.ACTIVE);
  }


  private void seedUser(String firstName, String lastName, String email, LocalDate dob, UserStatus status) {
    userRepository.findByEmail(email).orElseGet(
            () -> {
              User user = new User();
              user.setFirstName(firstName);
              user.setLastName(lastName);
              user.setEmail(email);
              user.setDateOfBirth(dob);
              user.setStatus(status);
              return userRepository.save(user);
            });
  }

}
