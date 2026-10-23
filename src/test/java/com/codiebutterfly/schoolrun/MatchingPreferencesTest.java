package com.codiebutterfly.schoolrun;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MatchingPreferencesTest {
 final Marketplace market=new Marketplace(null,null,"/tmp");
 @Test void switchesIndependentlyRelaxOnlyTheirOwnRestriction(){
 var listing=new HashMap<String,Object>(Map.of("area","Avondale","school","Gateway","homeLat",-17.8,"homeLng",31.0,"schoolLat",-17.9,"schoolLng",31.1,"radiusKm",5));
 var near=Map.<String,Object>of("homeLat",-17.8,"homeLng",31.0,"schoolLat",-17.9,"schoolLng",31.1);
 var farHome=new HashMap<>(near);farHome.put("homeLat",-19.0);
 var farSchool=new HashMap<>(near);farSchool.put("schoolLat",-19.0);
 assertTrue(market.matches(listing,near));assertFalse(market.matches(listing,farHome));assertFalse(market.matches(listing,farSchool));
 listing.put("matchSchool",false);var homeOnly=market.route(listing);assertTrue(market.matches(homeOnly,farSchool));assertFalse(market.matches(homeOnly,farHome));
 listing.put("matchPickupArea",false);listing.put("matchSchool",true);var schoolOnly=market.route(listing);assertTrue(market.matches(schoolOnly,farHome));assertFalse(market.matches(schoolOnly,farSchool));
 var unrestricted=market.route(Map.of("matchPickupArea",false,"matchSchool",false));assertTrue(market.matches(unrestricted,farHome));assertTrue(market.matches(unrestricted,farSchool));
 assertThrows(RuntimeException.class,()->market.route(Map.of("matchPickupArea",true,"matchSchool",false)));
 assertThrows(RuntimeException.class,()->market.route(Map.of("matchPickupArea","false","matchSchool",false)));
 }
 @Test void matchesAnyAreaAndAnySchoolAndValidatesEveryEntry(){
 var a=Map.<String,Object>of("area","A","homeLat",-17.0,"homeLng",31.0,"radiusKm",2);
 var b=Map.<String,Object>of("area","B","homeLat",-18.0,"homeLng",32.0,"radiusKm",2);
 var x=Map.<String,Object>of("school","X","schoolLat",-17.1,"schoolLng",31.1);
 var y=Map.<String,Object>of("school","Y","schoolLat",-18.1,"schoolLng",32.1);
 var route=market.route(Map.of("areas",List.of(a,b),"schools",List.of(x,y)));
 assertTrue(market.matches(route,Map.of("homeLat",-18.0,"homeLng",32.0,"schoolLat",-18.1,"schoolLng",32.1)));
 assertTrue(market.matches(route,Map.of("homeLat",-17.0,"homeLng",31.0,"schoolLat",-18.1,"schoolLng",32.1)));
 assertFalse(market.matches(route,Map.of("homeLat",-19.0,"homeLng",32.0,"schoolLat",-18.1,"schoolLng",32.1)));
 assertThrows(RuntimeException.class,()->market.route(Map.of("areas",List.of(),"schools",List.of(x))));
 assertThrows(RuntimeException.class,()->market.route(Map.of("areas",List.of(a,Map.of("area","Invalid")),"schools",List.of(x))));
 }
}
