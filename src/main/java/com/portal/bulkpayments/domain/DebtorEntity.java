package com.portal.bulkpayments.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "remittance_debtor")
public class DebtorEntity extends PanacheEntity {

  @ManyToOne(optional = false)
  public RemittanceEntity remittance;

  public String accountNumber;
  public String identityId;
  public String identityDocumentType;
}
