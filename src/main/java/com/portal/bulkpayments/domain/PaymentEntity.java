package com.portal.bulkpayments.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "remittance_payment")
public class PaymentEntity extends PanacheEntity {

  @ManyToOne(optional = false)
  public RemittanceEntity remittance;

  public String paymentType;
  public String currency;
  public BigDecimal amount;
  public String creditorName;
  public String creditorIdentityId;
  public String creditorIdentityDocumentType;
  public String creditorAccountNumber;
}
