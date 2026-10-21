package com.codiebutterfly.schoolrun;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.codiebutterfly.schoolrun.Store.*;
@RestController @RequestMapping("/api/ratings") public class Ratings {
 final Store s;Ratings(Store s){this.s=s;}
 static Map<String,Object> summary(Store s,Object driver){var row=s.one("SELECT count(stars) rating_count,coalesce(avg(cast(stars as double precision)),0) average_rating FROM sr_rating_event WHERE driver_id=?",driver);return Map.of("ratingCount",row.get("rating_count"),"averageRating",row.get("average_rating"));}
 static boolean recommended(Map<String,Object> rating){return ((Number)rating.get("ratingCount")).intValue()==0||((Number)rating.get("averageRating")).doubleValue()>=4;}
 @GetMapping("/pending") Object pending(@RequestHeader(value="Authorization",required=false)String a){var u=s.user(a);s.role(u,"PARENT");return s.db.queryForList("SELECT r.trip_id,r.action,r.child_name,r.created_at,u.name driver_name FROM sr_rating_event r JOIN sr_user u ON u.id=r.driver_id WHERE r.parent_id=? AND r.stars IS NULL ORDER BY r.created_at,r.trip_id,r.action",u.get("id"));}
 @PostMapping @Transactional Object rate(@RequestHeader(value="Authorization",required=false)String a,@RequestBody Map<String,Object>b){var u=s.user(a);s.role(u,"PARENT");String trip=text(b,"tripId",100),action=text(b,"action",10);Object raw=b.get("stars");if(!(raw instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<1||n.intValue()>5)throw error(400,"Choose 1 to 5 stars");var r=s.one("SELECT * FROM sr_rating_event WHERE trip_id=? AND action=? AND parent_id=? FOR UPDATE",trip,action,u.get("id"));if(r.get("stars")!=null){if(((Number)r.get("stars")).intValue()!=((Number)raw).intValue())throw error(409,"This event has already been rated");return summary(s,r.get("driver_id"));}s.db.update("UPDATE sr_rating_event SET stars=?,rated_at=CURRENT_TIMESTAMP WHERE trip_id=? AND action=?",((Number)raw).intValue(),trip,action);return summary(s,r.get("driver_id"));}
 @GetMapping("/mine") Object mine(@RequestHeader(value="Authorization",required=false)String a){var u=s.user(a);s.role(u,"DRIVER");return summary(s,u.get("id"));}
}
