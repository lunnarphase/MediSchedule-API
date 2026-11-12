package pl.medischedule.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "patients")
public class Patient extends BaseEntity {
  @OneToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "id_user", nullable = false, unique = true)
  public User user;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, unique = true, length = 11)
  public String pesel;

  @Column(length = 20)
  public String phone;
}
