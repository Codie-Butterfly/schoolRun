package com.codiebutterfly.schoolrun;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:childbillingtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","schoolrun.subscription-price=1","schoolrun.subscription-unit=CHILD"}) @AutoConfigureMockMvc
class SubscriptionPolicyTest extends IntegrationTest {
 @MockBean PaynowClient gateway;
 Map<String,Object> addChild(String token,Object link,String name)throws Exception{var b=new HashMap<String,Object>(Map.of("linkId",link,"name",name,"grade","1","homeAddress","Home","homeGate","Gate","schoolName","School","schoolAddress","School","schoolGate","Gate","collectionContact","Teacher 0771234567"));b.putAll(Map.of("homeLat",-17.8,"homeLng",31.0,"schoolLat",-17.9,"schoolLng",31.1));return request(post("/api/children"),token,b,200);}
 @Test void childFeesPersistAfterDisablingAndNewChildrenNeedTheirOwnPayment()throws Exception{
 var d=register("DRIVER","0770000201");var p=register("PARENT","0770000202");var stranger=register("DRIVER","0770000203");String dt=token(d),pt=token(p),m=YearMonth.now(Store.ZONE).toString();Object link=request(post("/api/links"),pt,Map.of("code",user(d).get("driver_code")),200).get("id");
 addChild(pt,link,"Child one");addChild(pt,link,"Child two");
 var sub=request(get("/api/subscriptions").param("month",m),dt,null,200);var family=(Map<?,?>)((List<?>)sub.get("customers")).get(0);assertEquals(2,((Number)family.get("amountDue")).intValue());assertEquals(2,((List<?>)family.get("charges")).size());
 request(put("/api/links/"+link+"/service"),token(stranger),Map.of("enabled",false),404);
 request(put("/api/links/"+link+"/service"),dt,Map.of("enabled",false),200);assertFalse(s.serviceEnabled(link));sub=request(get("/api/subscriptions").param("month",m),dt,null,200);family=(Map<?,?>)((List<?>)sub.get("customers")).get(0);assertEquals(2,((Number)family.get("amountDue")).intValue());
 when(gateway.initiate(anyString(),anyString(),anyString(),nullable(String.class),anyString())).thenReturn(Map.of("pollurl","https://www.paynow.co.zw/interface/checkpayment/?guid=test","browserurl","https://www.paynow.co.zw/payment/test"));
 var pay=request(post("/api/payments"),dt,Map.of("month",m,"linkIds",List.of(link),"idempotencyKey","children-one","method","browser"),200);assertEquals(2,((Number)pay.get("amount")).intValue());String pid=pay.get("id").toString();
 when(gateway.poll(anyString())).thenReturn(Map.of("reference",pid,"amount","2.00","status","Paid"));request(post("/api/payments/"+pid+"/check"),dt,null,200);assertTrue(s.paid(link,m));assertFalse(s.serviceEnabled(link));
 request(put("/api/links/"+link+"/service"),dt,Map.of("enabled",true),200);assertEquals(2,s.db.queryForObject("SELECT count(*) FROM sr_child_charge WHERE child_id IN(SELECT id FROM sr_child WHERE link_id=?)",Integer.class,link));
 addChild(pt,link,"Child three");sub=request(get("/api/subscriptions").param("month",m),dt,null,200);family=(Map<?,?>)((List<?>)sub.get("customers")).get(0);assertEquals(1,((Number)family.get("amountDue")).intValue());assertFalse(s.paid(link,m));
 request(put("/api/links/"+link+"/service"),dt,Map.of("enabled",false),200);request(put("/api/links/"+link+"/service"),dt,Map.of("enabled",true),200);assertEquals(3,s.db.queryForObject("SELECT count(*) FROM sr_child_charge WHERE child_id IN(SELECT id FROM sr_child WHERE link_id=?)",Integer.class,link));
 }
 @Test void acceptanceRequiresCurrentVersionAndRecordsAuthenticatedUser()throws Exception{
 var p=register("PARENT","0770000210");String pt=token(p);assertEquals(false,request(get("/api/legal/acceptance"),pt,null,200).get("accepted"));request(post("/api/legal/acceptance"),null,Map.of("version",Legal.VERSION),401);request(post("/api/legal/acceptance"),pt,Map.of("version","old"),409);request(post("/api/legal/acceptance"),pt,Map.of("version",Legal.VERSION),200);request(post("/api/legal/acceptance"),pt,Map.of("version",Legal.VERSION),200);assertEquals(true,request(get("/api/legal/acceptance"),pt,null,200).get("accepted"));assertEquals(1,s.db.queryForObject("SELECT count(*) FROM sr_terms_acceptance WHERE user_id=?",Integer.class,user(p).get("id")));
 }
}
