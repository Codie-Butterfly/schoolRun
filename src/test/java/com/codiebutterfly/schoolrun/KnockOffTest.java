package com.codiebutterfly.schoolrun;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class KnockOffTest {
 @Test void schoolDayCreatesReturnWithoutInventingDeadline(){
  var morning=new HashMap<String,Object>();morning.putAll(Map.of("pickup","Home","dropoff","School","pickupLat",-17.8,"pickupLng",31.0,"dropoffLat",-17.9,"dropoffLng",31.1,"pickupTime","06:30","arrivalBy","07:30","schoolFinishTime","13:30"));
  var result=Journeys.withReturns(List.of(morning));assertEquals(2,result.size());var back=result.get(1);assertEquals("School",back.get("pickup"));assertEquals("Home",back.get("dropoff"));assertEquals("13:30",back.get("pickupTime"));assertEquals(true,back.get("noArrivalDeadline"));
  assertEquals(2,Journeys.withReturns(List.of(morning,back)).size());
  morning.remove("schoolFinishTime");assertEquals(1,Journeys.withReturns(List.of(morning)).size());
 }
}
