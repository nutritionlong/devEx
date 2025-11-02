package org.example.liveplatform.dao;

import java.util.Optional;

import org.example.liveplatform.dao.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);
}
