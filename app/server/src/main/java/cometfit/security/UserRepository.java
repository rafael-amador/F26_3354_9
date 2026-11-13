package cometfit.security;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /** UC-3 / SD-3 "search username". */
    @Query("select u from Student u where lower(u.username) like lower(concat('%', :q, '%')) order by u.username")
    List<Student> searchStudents(@Param("q") String q);
}
