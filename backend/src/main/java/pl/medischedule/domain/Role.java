package pl.medischedule.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role extends BaseEntity {
  @Column(nullable = false, unique = true, length = 20)
  public String name;
}
