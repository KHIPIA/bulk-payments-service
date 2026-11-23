package com.portal.bulkpayments.service;

import com.portal.bulkpayments.dto.Amount;
import com.portal.bulkpayments.dto.Identity;
import com.portal.bulkpayments.dto.Payment;
import com.portal.bulkpayments.dto.Remittance;
import com.portal.bulkpayments.dto.RemittanceRequest;
import com.portal.bulkpayments.exception.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validación de forma y reglas de negocio, equivalente a validateShape/checkBusinessRules de los
 * routers Node. Se mantiene manual (sin Bean Validation) para reproducir exactamente el mensaje
 * "Mandatory parameters not reported: a, b".
 */
@ApplicationScoped
public class RemittanceValidator {

  public static final String INTERNAL = "INTERNAL_PAYMENT";
  public static final String INTERBANK = "INTERBANK_PAYMENT";

  private static final BigDecimal TOLERANCE = new BigDecimal("0.001");

  /** Valida forma y reglas de negocio; lanza ApiException 400 en el primer incumplimiento. */
  public void validate(RemittanceRequest body, boolean requireName, boolean debtorIdentityOptional) {
    List<String> missing = validateShape(body, requireName, debtorIdentityOptional);
    if (!missing.isEmpty()) {
      throw mandatoryMissing(String.join(", ", missing));
    }
    checkBusinessRules(body);
  }

  public static ApiException mandatoryMissing(String paths) {
    return ApiException.badRequest("mandatoryParametersMissing", "Mandatory parameters not reported: " + paths);
  }

  List<String> validateShape(RemittanceRequest body, boolean requireName, boolean debtorIdentityOptional) {
    List<String> missing = new ArrayList<>();
    if (body == null) {
      body = new RemittanceRequest(null, null, null, null);
    }

    if (requireName) {
      req(missing, notBlank(body.name()), "name");
    }
    req(missing, "DRAFT".equals(body.status()) || "CLOSED".equals(body.status()), "status");
    req(missing, body.header() != null && notZero(body.header().numberOfTransactions()), "header.numberOfTransactions");
    req(missing, body.header() != null && notEmpty(body.header().controlSum()), "header.controlSum");
    req(missing, notEmpty(body.remittances()), "remittances");

    List<Remittance> remittances = orEmpty(body.remittances());
    for (int i = 0; i < remittances.size(); i++) {
      Remittance r = remittances.get(i);
      String rp = "remittances[" + i + "]";
      req(missing, r != null && notBlank(r.instructionId()), rp + ".instructionId");

      Identity debtorIdentity = r != null && r.debtor() != null ? r.debtor().identity() : null;
      if (!debtorIdentityOptional || debtorIdentity != null) {
        req(missing, debtorIdentity != null && notBlank(debtorIdentity.id()), rp + ".debtor.identity.id");
        req(missing, debtorIdentity != null && notBlank(debtorIdentity.documentType()), rp + ".debtor.identity.documentType");
      }

      req(missing, r != null && r.debtorAccount() != null && notBlank(r.debtorAccount().number()), rp + ".debtorAccount.number");
      req(missing, r != null && notEmpty(r.payments()), rp + ".payments");

      List<Payment> payments = r != null ? orEmpty(r.payments()) : List.of();
      for (int j = 0; j < payments.size(); j++) {
        Payment p = payments.get(j);
        String pp = rp + ".payments[" + j + "]";
        req(missing, p != null && p.paymentAmount() != null && notBlank(p.paymentAmount().currency()), pp + ".paymentAmount.currency");
        req(missing, p != null && p.paymentAmount() != null && notZero(p.paymentAmount().amount()), pp + ".paymentAmount.amount");
        req(missing, p != null && p.creditor() != null && notBlank(p.creditor().name()), pp + ".creditor.name");
        req(missing, p != null && p.creditorAccount() != null && notBlank(p.creditorAccount().number()), pp + ".creditorAccount.number");
        String type = p != null ? p.paymentType() : null;
        req(missing, INTERNAL.equals(type) || INTERBANK.equals(type), pp + ".paymentType");
        if (INTERBANK.equals(type)) {
          Identity ci = p.creditor() != null ? p.creditor().identity() : null;
          req(missing, ci != null && notBlank(ci.id()), pp + ".creditor.identity.id");
          req(missing, ci != null && notBlank(ci.documentType()), pp + ".creditor.identity.documentType");
        }
      }
    }
    return missing;
  }

  /** Reglas evaluadas con la forma ya válida; el primer incumplimiento gana. */
  void checkBusinessRules(RemittanceRequest body) {
    List<Payment> payments = allPayments(body);

    Set<String> types = new HashSet<>();
    payments.forEach(p -> types.add(p.paymentType()));
    if (types.size() > 1) {
      throw ApiException.badRequest("invalidParameters", "Invalid Parameters: solo se permite un tipo de pago por remesa.");
    }

    for (Payment p : payments) {
      int expected = INTERBANK.equals(p.paymentType()) ? 20 : 18;
      if (p.creditorAccount().number().length() != expected) {
        throw ApiException.badRequest(
            "invalidParameters",
            "Invalid Parameters: creditorAccount.number debe tener " + expected + " dígitos para " + p.paymentType() + ".");
      }
    }

    if (body.header().numberOfTransactions() != payments.size()) {
      throw ApiException.badRequest("totalRegistersNotMatch", "There is a difference between the total number of records reported.");
    }

    Map<String, BigDecimal> sums = new LinkedHashMap<>();
    payments.forEach(p -> sums.merge(p.paymentAmount().currency(), p.paymentAmount().amount(), BigDecimal::add));
    Map<String, BigDecimal> controlSums = new LinkedHashMap<>();
    for (Amount c : body.header().controlSum()) {
      controlSums.put(c.currency(), c.amount() != null ? c.amount() : BigDecimal.ZERO);
    }
    Set<String> currencies = new LinkedHashSet<>(sums.keySet());
    currencies.addAll(controlSums.keySet());
    for (String currency : currencies) {
      BigDecimal diff = sums.getOrDefault(currency, BigDecimal.ZERO).subtract(controlSums.getOrDefault(currency, BigDecimal.ZERO)).abs();
      if (diff.compareTo(TOLERANCE) > 0) {
        throw ApiException.badRequest("totalAmountNotMatch", "There is a difference between the total amount of records reported.");
      }
    }
  }

  public static List<Payment> allPayments(RemittanceRequest body) {
    List<Payment> all = new ArrayList<>();
    orEmpty(body.remittances()).forEach(r -> all.addAll(orEmpty(r.payments())));
    return all;
  }

  private static void req(List<String> missing, boolean ok, String path) {
    if (!ok) {
      missing.add(path);
    }
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isEmpty();
  }

  /** Equivalente a la veracidad de JS: null y 0 cuentan como "no informado". */
  private static boolean notZero(Integer n) {
    return n != null && n != 0;
  }

  private static boolean notZero(BigDecimal n) {
    return n != null && n.signum() != 0;
  }

  private static boolean notEmpty(List<?> l) {
    return l != null && !l.isEmpty();
  }

  private static <T> List<T> orEmpty(List<T> l) {
    return l != null ? l : List.of();
  }
}
