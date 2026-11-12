package pl.medischedule.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "specializations")
public class Specialization extends BaseEntity {
  public String name;
}
