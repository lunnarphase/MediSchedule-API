package pl.medischedule.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
  @Column(nullable = false, unique = true, length = 255)
  public String email;

  @JsonIgnore
  @Column(nullable = false, length = 255)
  public String password;

  @Column(name = "username", nullable = false, length = 100)
  public String name;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "id_role", nullable = false)
  public Role role;

  @Column(name = "is_blocked", nullable = false)
  public boolean blocked;
}
