package com.codiebutterfly.schoolrun;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:billingtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","schoolrun.subscription-price=2.50","schoolrun.subscription-unit=CUSTOMER"}) @AutoConfigureMockMvc
class BillingTest extends IntegrationTest {
 @MockBean PaynowClient gateway;
 @Test void verifiesAmountAndReferenceBeforeGrantingCoverageAndDoesNotDoubleCharge()throws Exception {
  var d=register("DRIVER","0770000101");var p=register("PARENT","0770000102");var link=request(post("/api/links"),token(p),Map.of("code",user(d).get("driver_code")),200);String m=YearMonth.now(Store.ZONE).toString();
  when(gateway.initiate(anyString(),anyString(),anyString(),nullable(String.class),anyString())).thenReturn(Map.of("pollurl","https://www.paynow.co.zw/interface/checkpayment/?guid=test","browserurl","https://www.paynow.co.zw/payment/test"));
  var body=Map.of("month",m,"linkIds",List.of(link.get("id")),"idempotencyKey","QA-one","method","browser");
  var payment=request(post("/api/payments"),token(d),body,200);assertEquals("PENDING",payment.get("state"));assertEquals(2.5,((Number)payment.get("amount")).doubleValue());assertFalse(s.paid(link.get("id"),m));
  request(post("/api/payments"),token(d),body,200);verify(gateway,times(1)).initiate(anyString(),anyString(),anyString(),nullable(String.class),anyString());
  String id=payment.get("id").toString();when(gateway.poll(anyString())).thenReturn(Map.of("reference",id,"amount","99","status","Paid"));request(post("/api/payments/"+id+"/check"),token(d),null,502);assertFalse(s.paid(link.get("id"),m));
  when(gateway.poll(anyString())).thenReturn(Map.of("reference","other","amount","2.50","status","Paid"));request(post("/api/payments/"+id+"/check"),token(d),null,502);assertFalse(s.paid(link.get("id"),m));
  when(gateway.poll(anyString())).thenReturn(Map.of("reference",id,"amount","2.50","status","Paid"));request(post("/api/payments/"+id+"/check"),token(d),null,200);assertTrue(s.paid(link.get("id"),m));request(post("/api/payments/"+id+"/check"),token(d),null,200);assertEquals(1,s.db.queryForObject("SELECT count(*) FROM sr_entitlement WHERE payment_id=?",Integer.class,id));
 }
}
