package com.portal.bulkpayments.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "remittance")
public class RemittanceEntity extends PanacheEntityBase {

  @Id
  @Column(length = 32)
  public String id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public ApiVariant api;

  @Column(nullable = false)
  public String name;

  @Column(nullable = false)
  public String status;

  /** "settled" en bulk-payments, "sentToSignature" en deferred-signature. */
  public boolean finalized;

  @OneToMany(mappedBy = "remittance", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  public List<DebtorEntity> debtors = new ArrayList<>();

  @OneToMany(mappedBy = "remittance", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  public List<PaymentEntity> payments = new ArrayList<>();
}
