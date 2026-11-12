package pl.medischedule.domain;

import jakarta.persistence.*;

@MappedSuperclass
public abstract class BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
}
