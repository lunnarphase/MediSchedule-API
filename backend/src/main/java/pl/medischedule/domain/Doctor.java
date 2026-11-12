package pl.medischedule.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "doctors")
public class Doctor extends BaseEntity {
  @OneToOne
  @JoinColumn(name = "id_user", nullable = false, unique = true)
  public User user;

  @Column(columnDefinition = "text")
  public String bio = "";

  public BigDecimal visitPrice = BigDecimal.ZERO;
  public int visitDuration = 30;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "doctors_specializations",
      joinColumns = @JoinColumn(name = "id_doctor"),
      inverseJoinColumns = @JoinColumn(name = "id_specialization"))
  public Set<Specialization> specializations = new LinkedHashSet<>();
}
