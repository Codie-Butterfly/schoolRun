package com.codiebutterfly.schoolrun;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class RoutePlannerTest {
 Map<String,Object> trip(String id,double lng){return Map.of("id",id+":2030-01-01:0","childId",id,"name",id,"state","PENDING","leg",Map.of("pickup","Home","dropoff","School","pickupLat",-17.8,"pickupLng",lng,"dropoffLat",-17.81,"dropoffLng",31.03,"pickupTime","06:00","arrivalBy","08:00"));}
 @Test void preservesPrecedenceAndCapacityAndChoosesNearbyPickup(){var plan=new RoutePlanner().plan(List.of(trip("far",31.5),trip("near",31.001)),1,new double[]{-17.8,31.0},LocalDate.of(2030,1,1));assertEquals("near",plan.get(0).get("childId"));Set<String> aboard=new HashSet<>();for(var e:plan){if(e.get("action").equals("pickup")){assertTrue(aboard.size()<1);aboard.add(e.get("childId").toString());}else assertTrue(aboard.remove(e.get("childId")));}assertTrue(aboard.isEmpty());assertEquals(4,plan.size());}
 @Test void onboardChildRetainsDropoff(){var t=new HashMap<>(trip("onboard",31.001));t.put("state","PICKED_UP");var plan=new RoutePlanner().plan(List.of(t,trip("pending",31.002)),1,new double[]{-17.8,31.0},LocalDate.of(2030,1,1));assertEquals("dropoff",plan.get(0).get("action"));assertEquals("onboard",plan.get(0).get("childId"));}
 @Test void fiveSeatsCanServeSevenChildrenByReusingSeatsAfterDropoffs(){
 var trips=new ArrayList<Map<String,Object>>();
 for(int i=0;i<7;i++){var t=new HashMap<>(trip("child"+i,i<5?31.0:31.03));var leg=new HashMap<String,Object>((Map<String,Object>)t.get("leg"));leg.put("dropoffLat",-17.8);leg.put("dropoffLng",i<2?31.02:31.1);t.put("leg",leg);trips.add(t);}
 var plan=new RoutePlanner().plan(trips,5,new double[]{-17.8,31.0},LocalDate.of(2030,1,1));
 Set<Object> onboard=new HashSet<>();int pickups=0,dropoffs=0,max=0;
 for(var e:plan){if(e.get("action").equals("pickup")){assertTrue(onboard.add(e.get("childId")));pickups++;}else{assertTrue(onboard.remove(e.get("childId")));dropoffs++;}max=Math.max(max,onboard.size());assertTrue(onboard.size()<=5);}
 assertEquals(7,pickups);assertEquals(7,dropoffs);assertEquals(5,max);assertTrue(onboard.isEmpty());
 for(int i=0;i<5;i++)assertEquals("pickup",plan.get(i).get("action"));
 assertEquals("dropoff",plan.get(5).get("action"));assertEquals("dropoff",plan.get(6).get("action"));
 assertEquals("pickup",plan.get(7).get("action"));assertEquals("pickup",plan.get(8).get("action"));
 }
}
