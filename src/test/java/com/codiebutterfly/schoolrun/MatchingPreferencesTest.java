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
}
