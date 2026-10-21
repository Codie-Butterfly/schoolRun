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
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:marketplacetest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"}) @AutoConfigureMockMvc
class MarketplaceTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired Store s;
 Map<String,Object> request(MockHttpServletRequestBuilder req,String token,Object body,int status)throws Exception{if(token!=null)req.header("Authorization","Bearer "+token);if(body!=null)req.contentType("application/json").content(json.writeValueAsString(body));var response=mvc.perform(req).andReturn().getResponse();assertEquals(status,response.getStatus(),response.getContentAsString());return response.getContentAsString().isBlank()?Map.of():json.readValue(response.getContentAsString(),Map.class);}
 Map<String,Object> register(String role,String phone)throws Exception{var b=new HashMap<String,Object>(Map.of("role",role,"name","QA "+role,"phone",phone,"email","qa@example.com","password","SchoolTest!234","address","Test address"));if(role.equals("DRIVER")){b.putAll(Map.of("licenceNumber","TEST","licenceExpiry","2030-01-01","vehicleModel","Test minibus","plateNumber","QA123","fitnessExpiry","2030-01-01","capacity",1,"serviceAreas","Test area"));}else b.putAll(Map.of("emergencyName","QA contact","emergencyPhone","0779999999"));return request(post("/api/auth/register"),null,b,200);}
 String token(Map<String,Object>session){return session.get("token").toString();}Map<String,Object> user(Map<String,Object>session){return (Map<String,Object>)session.get("user");}

 Object read(MockHttpServletRequestBuilder req,String token)throws Exception{var res=mvc.perform(req.header("Authorization","Bearer "+token)).andReturn().getResponse();assertEquals(200,res.getStatus(),res.getContentAsString());return json.readValue(res.getContentAsString(),Object.class);}
 List<?> search(String token,Map<String,Object> coords)throws Exception{var res=mvc.perform(post("/api/marketplace/search").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(coords))).andReturn().getResponse();assertEquals(200,res.getStatus());return json.readValue(res.getContentAsString(),List.class);}
 @Test void discoveryAndApprovalPreserveOwnershipCapacityAndTimetable()throws Exception{
 var d=register("DRIVER","0771100001");var p=register("PARENT","0771100002");var other=register("DRIVER","0771100003");var stranger=register("PARENT","0771100004");String dt=token(d),pt=token(p),ot=token(other),st=token(stranger);String driver=user(d).get("id").toString();
 var coords=new HashMap<String,Object>(Map.of("homeLat",-17.8,"homeLng",31.0,"schoolLat",-17.9,"schoolLng",31.1));var listing=new HashMap<String,Object>(coords);listing.putAll(Map.of("area","Avondale","school","Gateway","radiusKm",5,"available",true,"shareLicence",false));
 assertTrue(search(pt,coords).isEmpty());request(put("/api/marketplace/listing"),pt,listing,403);request(post("/api/marketplace/search"),dt,coords,403);request(put("/api/marketplace/listing"),dt,listing,200);assertEquals(1,search(pt,coords).size());
 var far=new HashMap<>(coords);far.put("homeLat",-18.8);assertTrue(search(pt,far).isEmpty());far=new HashMap<>(coords);far.put("schoolLat",-18.9);assertTrue(search(pt,far).isEmpty());
 request(get("/api/marketplace/drivers/"+driver+"/licence"),pt,null,404);
 request(put("/api/marketplace/availability"),dt,Map.of("available",false),200);assertTrue(search(pt,coords).isEmpty());request(put("/api/marketplace/availability"),dt,Map.of("available",true),200);
 var child=new HashMap<String,Object>(coords);child.putAll(Map.of("driverId",driver,"name","Test child","grade","1","homeAddress","Home","homeGate","Front gate","schoolName","Gateway","schoolAddress","School","schoolGate","Main gate","collectionContact","Parent"));
 var leg=new HashMap<String,Object>(Map.of("pickup","Home","dropoff","School","pickupLat",-17.8,"pickupLng",31.0,"dropoffLat",-17.9,"dropoffLng",31.1,"pickupTime","06:30","arrivalBy","07:30","schoolFinishTime","13:30"));
 var today=LocalDate.now(Store.ZONE);child.putAll(Map.of("termStart",today.toString(),"termEnd",today.plusDays(90).toString(),"week",Map.of("1",List.of())));request(post("/api/marketplace/requests"),pt,child,400);child.put("week",Map.of("1",List.of(leg)));
 String rid=request(post("/api/marketplace/requests"),pt,child,200).get("id").toString();request(post("/api/marketplace/requests"),pt,child,409);
 assertEquals(0,((List<?>)read(get("/api/marketplace/requests"),ot)).size());assertEquals(0,((List<?>)read(get("/api/marketplace/requests"),st)).size());assertEquals(1,((List<?>)read(get("/api/marketplace/requests"),dt)).size());assertEquals(0,((List<?>)read(get("/api/children"),dt)).size());
 request(post("/api/marketplace/requests/"+rid+"/approve"),ot,null,404);request(post("/api/marketplace/requests/"+rid+"/approve"),pt,null,403);
 assertEquals("APPROVED",request(post("/api/marketplace/requests/"+rid+"/approve"),dt,null,200).get("state"));request(post("/api/marketplace/requests/"+rid+"/approve"),dt,null,200);
 var children=(List<Map<String,Object>>)read(get("/api/children"),dt);assertEquals(1,children.size());String cid=children.get(0).get("id").toString();assertEquals(1,((List<?>)read(get("/api/children"),pt)).size());request(get("/api/children/"+cid+"/schedule"),st,null,403);
 var schedule=request(get("/api/children/"+cid+"/schedule"),pt,null,200);var week=(Map<?,?>)schedule.get("week");assertEquals("13:30",((Map<?,?>)((List<?>)week.get("1")).get(0)).get("schoolFinishTime"));assertTrue(search(pt,coords).isEmpty());assertEquals(0,s.db.queryForObject("SELECT count(*) FROM sr_entitlement",Integer.class));
 // Multiple trips makes a full-enrolment driver discoverable and accepts another child.
 listing.put("limitEnrolment",false);request(put("/api/marketplace/listing"),dt,listing,200);assertEquals(1,search(pt,coords).size());
 child.put("name","Second child");String second=request(post("/api/marketplace/requests"),pt,child,200).get("id").toString();request(post("/api/marketplace/requests/"+second+"/approve"),dt,null,200);assertEquals(2,((List<?>)read(get("/api/children"),dt)).size());
 // The physical one-seat vehicle still rejects a second simultaneous pickup, then permits it after drop-off.
 var enrolled=(List<Map<String,Object>>)read(get("/api/children"),dt);String link=enrolled.get(0).get("link_id").toString();s.db.update("INSERT INTO sr_entitlement VALUES(?,?,NULL)",link,YearMonth.from(today).toString());s.db.update("UPDATE sr_child_charge SET paid=true WHERE child_id IN(SELECT id FROM sr_child WHERE link_id=?)",link);
 var timetable=Map.of("termStart",today.toString(),"termEnd",today.plusDays(90).toString(),"week",Map.of(""+today.getDayOfWeek().getValue(),List.of(leg)));
 for(var c:enrolled)request(put("/api/children/"+c.get("id")+"/schedule"),pt,timetable,200);
 request(get("/api/route").param("day",today.toString()),dt,null,200);String firstTrip=enrolled.get(0).get("id")+":"+today+":0",secondTrip=enrolled.get(1).get("id")+":"+today+":0";
 request(post("/api/trips/"+firstTrip+"/pickup"),dt,null,200);request(post("/api/trips/"+secondTrip+"/pickup"),dt,null,409);request(post("/api/trips/"+firstTrip+"/dropoff"),dt,null,200);request(post("/api/trips/"+secondTrip+"/pickup"),dt,null,200);
 // A rejected application never creates a child or customer link.
 request(put("/api/marketplace/listing"),ot,listing,200);child.put("driverId",user(other).get("id"));String rejected=request(post("/api/marketplace/requests"),st,child,200).get("id").toString();assertEquals("REJECTED",request(post("/api/marketplace/requests/"+rejected+"/reject"),ot,null,200).get("state"));assertTrue(((List<?>)read(get("/api/children"),ot)).isEmpty());assertTrue(((List<?>)read(get("/api/links"),st)).isEmpty());
 }
}
