package com.codiebutterfly.schoolrun;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.*;
/** Capacity-constrained nearest feasible stop heuristic. Distances are geodesic estimates, not road ETAs. */
@Component public class RoutePlanner {
 public List<Map<String,Object>> plan(List<Map<String,Object>> trips,int capacity,double[] origin,LocalDate day){
  var remaining=new ArrayList<Map<String,Object>>();Set<String> onboard=new HashSet<>();
  for(var trip:trips){if(trip.get("state").equals("PICKED_UP"))onboard.add(trip.get("id").toString());else remaining.add(event(trip,"pickup"));remaining.add(event(trip,"dropoff"));}
  List<Map<String,Object>> plan=new ArrayList<>();double clock=day.equals(LocalDate.now(Store.ZONE))?LocalTime.now(Store.ZONE).toSecondOfDay()/60.0:0;double[] position=origin;
  while(!remaining.isEmpty()){
   Map<String,Object> best=null;double bestScore=Double.MAX_VALUE,bestArrival=0;
   for(var e:remaining){String id=e.get("id").toString();boolean pickup=e.get("action").equals("pickup");
    if(pickup&&(onboard.size()>=capacity||onboard.stream().anyMatch(x->x.startsWith(e.get("childId")+":"))))continue;
    if(pickup&&remaining.stream().anyMatch(other->other.get("childId").equals(e.get("childId"))&&!other.get("id").equals(id)&&other.get("id").toString().compareTo(id)<0))continue;
    if(!pickup&&!onboard.contains(id))continue;
    double travel=position==null?0:distance(position[0],position[1],((Number)e.get("latitude")).doubleValue(),((Number)e.get("longitude")).doubleValue())/25*60;
    double at=Math.max(clock+travel,((Number)e.get("earliest")).doubleValue());double late=Math.max(0,at-((Number)e.get("deadline")).doubleValue());
    // Prefer an achievable deadline, then the earliest arrival. A large late penalty exposes infeasible schedules.
    double score=at+late*1000+travel*0.01;if(score<bestScore){best=e;bestScore=score;bestArrival=at;}
   }
   if(best==null)throw new IllegalStateException("No feasible pickup/drop-off ordering");
   remaining.remove(best);String id=best.get("id").toString();if(best.get("action").equals("pickup"))onboard.add(id);else onboard.remove(id);
   best.put("estimatedArrival",String.format("%02d:%02d",(int)(bestArrival/60)%24,(int)bestArrival%60));best.put("lateByMinutes",Math.max(0,(int)Math.ceil(bestArrival-((Number)best.get("deadline")).doubleValue())));best.put("sequence",plan.size()+1);best.remove("earliest");best.remove("deadline");plan.add(best);clock=bestArrival+2;position=new double[]{((Number)best.get("latitude")).doubleValue(),((Number)best.get("longitude")).doubleValue()};
  }
  return plan;
 }
 @SuppressWarnings("unchecked") Map<String,Object> event(Map<String,Object>trip,String action){var e=new LinkedHashMap<String,Object>(trip);var leg=(Map<String,Object>)trip.get("leg");e.put("action",action);e.put("latitude",leg.get(action+"Lat"));e.put("longitude",leg.get(action+"Lng"));e.put("address",leg.get(action));e.put("earliest",LocalTime.parse(leg.get("pickupTime").toString()).toSecondOfDay()/60);e.put("deadline",Boolean.TRUE.equals(leg.get("noArrivalDeadline"))?Double.POSITIVE_INFINITY:LocalTime.parse(leg.get("arrivalBy").toString()).toSecondOfDay()/60);return e;}
 static double distance(double a,double b,double c,double d){double x=Math.sin(Math.toRadians(c-a)/2),y=Math.sin(Math.toRadians(d-b)/2);return 6371*2*Math.asin(Math.sqrt(Math.min(1,x*x+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*y*y)));}
}
