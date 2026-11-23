package com.portal.bulkpayments.service;

import com.portal.bulkpayments.domain.ApiVariant;
import com.portal.bulkpayments.domain.DebtorEntity;
import com.portal.bulkpayments.domain.PaymentEntity;
import com.portal.bulkpayments.domain.RemittanceEntity;
import com.portal.bulkpayments.dto.Payment;
import com.portal.bulkpayments.dto.Remittance;
import com.portal.bulkpayments.dto.RemittanceData;
import com.portal.bulkpayments.dto.RemittanceRequest;
import com.portal.bulkpayments.exception.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Ciclo de vida de una remesa: crear (POST), agregar pagos (PATCH) y cerrar/liquidar. */
@ApplicationScoped
public class RemittanceService {

  private static final SecureRandom RANDOM = new SecureRandom();

  @Inject RemittanceValidator validator;

  @Transactional
  public RemittanceData create(ApiVariant variant, RemittanceRequest body) {
    validator.validate(body, true, variant.debtorIdentityOptional());

    RemittanceEntity record = new RemittanceEntity();
    record.id = generateId();
    record.api = variant;
    record.name = variant.applyNamePrefix(body.name());
    record.status = body.status();
    record.finalized = false;

    for (Remittance r : body.remittances()) {
      DebtorEntity d = new DebtorEntity();
      d.remittance = record;
      d.accountNumber = r.debtorAccount().number();
      if (r.debtor() != null && r.debtor().identity() != null) {
        d.identityId = r.debtor().identity().id();
        d.identityDocumentType = r.debtor().identity().documentType();
      }
      record.debtors.add(d);
    }
    appendPayments(record, body);
    record.persist();

    return new RemittanceData(record.id, record.name, body.header(), record.status, body.remittances());
  }

  @Transactional
  public void addPayments(ApiVariant variant, String id, RemittanceRequest body) {
    RemittanceEntity record = find(variant, id);
    if ("CLOSED".equals(record.status) || record.finalized) {
      throw fullPaymentRemittance();
    }

    validator.validate(body, variant.requireNameOnPatch(), variant.debtorIdentityOptional());

    appendPayments(record, body);
    if (body.status() != null) {
      record.status = body.status();
    }
  }

  @Transactional
  public void closeSettlement(ApiVariant variant, String id, RemittanceRequest body) {
    RemittanceEntity record = find(variant, id);
    if (record.finalized) {
      throw fullPaymentRemittance();
    }

    List<String> accountNumbers =
        body == null || body.remittances() == null
            ? List.of()
            : body.remittances().stream()
                .map(r -> r == null || r.debtorAccount() == null ? null : r.debtorAccount().number())
                .filter(n -> n != null && !n.isEmpty())
                .collect(Collectors.toList());
    if (accountNumbers.isEmpty()) {
      throw RemittanceValidator.mandatoryMissing("remittances[0].debtorAccount.number");
    }

    Set<String> known = record.debtors.stream().map(d -> d.accountNumber).collect(Collectors.toSet());
    if (!known.containsAll(accountNumbers)) {
      throw ApiException.badRequest(
          "notMatchingAccount",
          "Provided account does not match with the one provided in the payment-remittance creation.");
    }

    if (accountNumbers.stream().anyMatch(n -> n.endsWith("0000"))) {
      throw ApiException.badRequest("functionalError", variant.functionalErrorMessage());
    }

    // bulk-payments: liquida el pago. deferred-signature: solo lo envía a "pendiente de firma".
    record.finalized = true;
    record.status = "CLOSED";
  }

  private RemittanceEntity find(ApiVariant variant, String id) {
    RemittanceEntity record = RemittanceEntity.findById(id);
    // Cada API mantiene su propio registro de remesas, como en los routers Node.
    if (record == null || record.api != variant) {
      throw ApiException.badRequest(
          "senderNotFound", "The sender include in the petition is not present in the payment-remittance.");
    }
    return record;
  }

  private static ApiException fullPaymentRemittance() {
    return ApiException.badRequest("fullPaymentRemittance", "Invalid Parameters.");
  }

  private static void appendPayments(RemittanceEntity record, RemittanceRequest body) {
    for (Payment p : RemittanceValidator.allPayments(body)) {
      PaymentEntity e = new PaymentEntity();
      e.remittance = record;
      e.paymentType = p.paymentType();
      e.currency = p.paymentAmount().currency();
      e.amount = p.paymentAmount().amount();
      e.creditorName = p.creditor().name();
      if (p.creditor().identity() != null) {
        e.creditorIdentityId = p.creditor().identity().id();
        e.creditorIdentityDocumentType = p.creditor().identity().documentType();
      }
      e.creditorAccountNumber = p.creditorAccount().number();
      record.payments.add(e);
    }
  }

  private static String generateId() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().withUpperCase().formatHex(bytes);
  }
}
