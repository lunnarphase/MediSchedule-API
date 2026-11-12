package pl.medischedule.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pl.medischedule.domain.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {
  Optional<Role> findByName(String name);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Role r where r.name = :name")
  Optional<Role> lockByName(@Param("name") String name);
}
