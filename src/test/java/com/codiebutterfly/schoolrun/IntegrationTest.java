package com.codiebutterfly.schoolrun;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:schooltest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"}) @AutoConfigureMockMvc
class IntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired Store s;
 Map<String,Object> request(MockHttpServletRequestBuilder req,String token,Object body,int status)throws Exception{if(token!=null)req.header("Authorization","Bearer "+token);if(body!=null)req.contentType("application/json").content(json.writeValueAsString(body));var response=mvc.perform(req).andReturn().getResponse();assertEquals(status,response.getStatus(),response.getContentAsString());return response.getContentAsString().isBlank()?Map.of():json.readValue(response.getContentAsString(),Map.class);}
 Map<String,Object> register(String role,String phone)throws Exception{var b=new HashMap<String,Object>(Map.of("role",role,"name","QA "+role,"phone",phone,"email","qa@example.com","password","SchoolTest!234","address","Test address"));if(role.equals("DRIVER")){b.putAll(Map.of("licenceNumber","TEST","licenceExpiry","2030-01-01","vehicleModel","Test minibus","plateNumber","QA123","fitnessExpiry","2030-01-01","capacity",1,"serviceAreas","Test area"));}else b.putAll(Map.of("emergencyName","QA contact","emergencyPhone","0779999999"));return request(post("/api/auth/register"),null,b,200);}
 String token(Map<String,Object>session){return session.get("token").toString();}Map<String,Object> user(Map<String,Object>session){return (Map<String,Object>)session.get("user");}
 @Test void registrationOwnershipScheduleAndTripLifecycle()throws Exception{
  request(get("/api/children"),null,null,401);
  var d=register("DRIVER","0770000001");var p=register("PARENT","0770000002");var stranger=register("PARENT","0770000003");
  var link=request(post("/api/links"),token(p),Map.of("code",user(d).get("driver_code")),200);
  var c=new HashMap<String,Object>(Map.of("linkId",link.get("id"),"name","QA Child","grade","1","homeAddress","Home","homeGate","Gate A","schoolName","Test school","schoolAddress","School","schoolGate","Gate B","collectionContact","QA parent"));c.putAll(Map.of("homeLat",-17.8,"homeLng",31.0,"schoolLat",-17.9,"schoolLng",31.1));
  c.put("dateOfBirth","2017-03-15");var child=request(post("/api/children"),token(p),c,200);assertEquals("2017-03-15",((Map<?,?>)child.get("details")).get("dateOfBirth"));String id=child.get("id").toString();
  request(get("/api/children/"+id+"/tracking"),token(stranger),null,403);
  request(post("/api/children"),token(stranger),c,404);
  var leg=Map.of("pickup","Home","dropoff","School","pickupLat",-17.8,"pickupLng",31.0,"dropoffLat",-17.9,"dropoffLng",31.1,"pickupTime","06:30","arrivalBy","07:30");
  var today=LocalDate.now(Store.ZONE);var schedule=Map.of("termStart",today.toString(),"termEnd",today.plusDays(90).toString(),"week",Map.of(""+today.getDayOfWeek().getValue(),List.of(leg)));
  request(put("/api/children/"+id+"/schedule"),token(p),schedule,200);
  var schoolDay=new HashMap<String,Object>(leg);schoolDay.put("schoolFinishTime","13:30");
  var withFinish=Map.of("termStart",today.toString(),"termEnd",today.plusDays(90).toString(),"week",Map.of(""+today.getDayOfWeek().getValue(),List.of(schoolDay)));
  request(put("/api/children/"+id+"/schedule"),token(p),withFinish,200);
  var returns=mvc.perform(get("/api/children/"+id+"/week").param("start",today.toString()).header("Authorization","Bearer "+token(d))).andReturn().getResponse();
  var shown=json.readTree(returns.getContentAsString()).get(0).get("legs");assertEquals(2,shown.size());assertEquals("13:30",shown.get(1).get("pickupTime").asText());assertTrue(shown.get(1).get("automaticReturn").asBoolean());assertEquals("Home",shown.get(1).get("dropoff").asText());
  request(put("/api/children/"+id+"/schedule"),token(p),schedule,200);
  request(put("/api/children/"+id+"/schedule"),token(d),schedule,403);
  request(get("/api/children/"+id+"/week").param("start",today.toString()),token(stranger),null,403);
  var weekly=mvc.perform(get("/api/children/"+id+"/week").param("start",today.toString()).header("Authorization","Bearer "+token(d))).andReturn().getResponse();assertEquals(200,weekly.getStatus());var weekRows=json.readTree(weekly.getContentAsString());assertEquals(7,weekRows.size());assertEquals("06:30",weekRows.get(0).get("legs").get(0).get("pickupTime").asText());
  var route=request(get("/api/route").param("day",today.toString()),token(d),null,200);assertTrue(((List<?>)route.get("stops")).isEmpty(),"Unpaid customer must not create a pickup");
  String payment=Store.id();s.db.update("INSERT INTO sr_payment(id,driver_id,billing_month,amount,currency,selection,state,idempotency_key) VALUES(?,?,?,?,?,?,?,?)",payment,user(d).get("id"),YearMonth.from(today).toString(),1,"USD","[]","PAID","test");s.db.update("INSERT INTO sr_entitlement VALUES(?,?,?)",link.get("id"),YearMonth.from(today).toString(),payment);s.db.update("UPDATE sr_child_charge SET paid=true,payment_id=? WHERE child_id=?",payment,id);
  route=request(get("/api/route").param("day",today.toString()),token(d),null,200);assertEquals(1,((List<?>)route.get("stops")).size());String trip=id+":"+today+":0";
  request(put("/api/children/"+id+"/days/"+today),token(p),Map.of("absent",true),200);
  route=request(get("/api/route").param("day",today.toString()),token(d),null,200);assertTrue(((List<?>)route.get("stops")).isEmpty());
  var changed=mvc.perform(get("/api/children/"+id+"/week").param("start",today.toString()).header("Authorization","Bearer "+token(d))).andReturn().getResponse();var changedDays=json.readTree(changed.getContentAsString());assertTrue(changedDays.get(0).get("changed").asBoolean());assertEquals(0,changedDays.get(0).get("legs").size());assertTrue(changedDays.get(0).get("cancelled").asBoolean());
  var roster=mvc.perform(get("/api/children").header("Authorization","Bearer "+token(d))).andReturn().getResponse();assertEquals(today.toString(),json.readTree(roster.getContentAsString()).get(0).get("cancelledDates").get(0).asText());
  request(post("/api/trips/"+trip+"/pickup"),token(d),null,409);
  request(delete("/api/children/"+id+"/days/"+today),token(p),null,200);
  var restored=mvc.perform(get("/api/children/"+id+"/week").param("start",today.toString()).header("Authorization","Bearer "+token(d))).andReturn().getResponse();assertFalse(json.readTree(restored.getContentAsString()).get(0).get("cancelled").asBoolean());
  request(get("/api/route").param("day",today.toString()),token(d),null,200);
  request(post("/api/trips/"+trip+"/dropoff"),token(d),null,409);
  request(post("/api/trips/"+trip+"/pickup"),token(d),null,200);
  request(post("/api/trips/"+trip+"/pickup"),token(d),null,200);
  assertEquals(1,s.db.queryForObject("SELECT count(*) FROM sr_rating_event WHERE trip_id=?",Integer.class,trip));
  var rating=Map.of("tripId",trip,"action","pickup","stars",3);
  request(post("/api/ratings"),token(stranger),rating,404);request(post("/api/ratings"),token(d),rating,403);
  request(post("/api/ratings"),token(p),Map.of("tripId",trip,"action","dropoff","stars",5),404);
  request(post("/api/ratings"),token(p),Map.of("tripId",trip,"action","pickup","stars",6),400);
  request(post("/api/ratings"),token(p),Map.of("tripId",trip,"action","pickup","stars",1.5),400);
  request(post("/api/ratings"),token(p),rating,200);request(post("/api/ratings"),token(p),rating,200);
  request(post("/api/ratings"),token(p),Map.of("tripId",trip,"action","pickup","stars",5),409);
  var summary=request(get("/api/ratings/mine"),token(d),null,200);assertEquals(1,((Number)summary.get("ratingCount")).intValue());assertFalse(Ratings.recommended(summary));
  request(put("/api/marketplace/listing"),token(d),Map.of("matchPickupArea",false,"matchSchool",false,"limitEnrolment",false,"available",true),200);
  var query=Map.of("homeLat",-17.8,"homeLng",31.0,"schoolLat",-17.9,"schoolLng",31.1);
  var search=mvc.perform(post("/api/marketplace/search").header("Authorization","Bearer "+token(p)).contentType("application/json").content(json.writeValueAsString(query))).andReturn().getResponse();assertEquals(0,json.readTree(search.getContentAsString()).size());

  request(put("/api/children/"+id+"/days/"+today),token(p),Map.of("absent",true),409);
  request(post("/api/position"),token(d),Map.of("latitude",-17.85,"longitude",31.05),200);
  var tracked=request(get("/api/children/"+id+"/tracking"),token(p),null,200);assertEquals(true,tracked.get("available"));assertEquals(false,tracked.get("stale"));
  request(post("/api/trips/"+trip+"/dropoff"),token(d),null,200);
  assertEquals(2,s.db.queryForObject("SELECT count(*) FROM sr_rating_event WHERE trip_id=?",Integer.class,trip));
  request(post("/api/ratings"),token(p),Map.of("tripId",trip,"action","dropoff","stars",5),200);
  summary=request(get("/api/ratings/mine"),token(d),null,200);assertEquals(2,((Number)summary.get("ratingCount")).intValue());assertEquals(4.0,((Number)summary.get("averageRating")).doubleValue());assertTrue(Ratings.recommended(summary));
  search=mvc.perform(post("/api/marketplace/search").header("Authorization","Bearer "+token(p)).contentType("application/json").content(json.writeValueAsString(query))).andReturn().getResponse();assertTrue(json.readTree(search.getContentAsString()).size()>0);

  assertEquals(false,request(get("/api/children/"+id+"/tracking"),token(p),null,200).get("active"));
  request(post("/api/position"),token(d),Map.of("latitude",-17.85,"longitude",31.05),409);
  request(post("/api/auth/logout"),token(p),null,200);request(get("/api/me"),token(p),null,401);
 }
 @Test void paymentSignaturesAndUrls(){var fields=new LinkedHashMap<String,String>();fields.put("reference","a");fields.put("status","Paid");String hash=PaynowClient.hash(fields,"key");fields.put("hash",hash);assertEquals(hash,PaynowClient.hash(fields,"key"));fields.put("status","Failed");assertNotEquals(hash,PaynowClient.hash(fields,"key"));assertThrows(RuntimeException.class,()->PaynowClient.parse("status=Paid&status=Failed"));assertThrows(RuntimeException.class,()->PaynowClient.safePollUrl("http://localhost/internal"));assertThrows(RuntimeException.class,()->PaynowClient.safePollUrl("https://www.paynow.co.zw/other"));}
}
