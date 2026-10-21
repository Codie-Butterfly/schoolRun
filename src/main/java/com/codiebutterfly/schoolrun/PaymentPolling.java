package com.codiebutterfly.schoolrun;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/** Reconcile known pending poll URLs even if the app closes or a callback is missed. */
@Component @ConditionalOnProperty(name="paynow.enabled",havingValue="true") public class PaymentPolling {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PaymentPolling.class);
 final Store s;final Billing billing;
 PaymentPolling(Store s,Billing billing){this.s=s;this.billing=billing;}
 @Scheduled(fixedDelayString="${paynow.poll-interval-ms:60000}") void reconcile(){
  for(var payment:s.db.queryForList("SELECT * FROM sr_payment WHERE state IN ('PENDING','UNKNOWN') AND poll_url IS NOT NULL ORDER BY created_at LIMIT 50")){
   try{billing.reconcile(payment);}catch(RuntimeException e){log.warn("PAYMENT STATUS CHECK deferred paymentId={} reason={}",payment.get("id"),e.getClass().getSimpleName());}
  }
 }
}
