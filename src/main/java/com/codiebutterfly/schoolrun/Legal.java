package com.codiebutterfly.schoolrun;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.codiebutterfly.schoolrun.Store.*;
@RestController @RequestMapping("/api/legal") public class Legal {
 static final String VERSION="2026-10-21.1";
 final Store s;Legal(Store s){this.s=s;}
 @GetMapping("/acceptance") Object get(@RequestHeader(value="Authorization",required=false)String a){var u=s.user(a);return Map.of("version",VERSION,"accepted",s.db.queryForObject("SELECT count(*) FROM sr_terms_acceptance WHERE user_id=? AND version=?",Integer.class,u.get("id"),VERSION)>0);}
 @PostMapping("/acceptance") Object accept(@RequestHeader(value="Authorization",required=false)String a,@RequestBody Map<String,Object>b){var u=s.user(a);if(!VERSION.equals(b.get("version")))throw error(409,"Read the latest terms before accepting");s.db.update("INSERT INTO sr_terms_acceptance(user_id,version) SELECT ?,? WHERE NOT EXISTS(SELECT 1 FROM sr_terms_acceptance WHERE user_id=? AND version=?)",u.get("id"),VERSION,u.get("id"),VERSION);return get(a);}
}
