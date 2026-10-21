package com.codiebutterfly.schoolrun;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.*;
import static com.codiebutterfly.schoolrun.Store.*;
@RestController @RequestMapping("/api") public class FamilyService {
 final Store s;FamilyService(Store s){this.s=s;}
 @PutMapping("/links/{id}/service") @Transactional Object update(@RequestHeader(value="Authorization",required=false)String a,@PathVariable String id,@RequestBody Map<String,Object>b){
 var u=s.user(a);s.role(u,"DRIVER");s.one("SELECT id FROM sr_user WHERE id=? FOR UPDATE",u.get("id"));var link=s.one("SELECT * FROM sr_link WHERE id=? AND driver_id=?",id,u.get("id"));if(!(b.get("enabled") instanceof Boolean))throw error(400,"Choose service on or off");boolean enabled=Boolean.TRUE.equals(b.get("enabled"));
 if(!enabled&&s.db.queryForObject("SELECT count(*) FROM sr_trip t JOIN sr_child c ON c.id=t.child_id WHERE c.link_id=? AND t.state='PICKED_UP'",Integer.class,id)>0)throw error(409,"Complete the safe drop-off before disabling this family");
 s.accrue(id);boolean was=s.serviceEnabled(id);if(was!=enabled){s.db.update("UPDATE sr_family_service SET enabled=?,active_since=? WHERE link_id=?",enabled,LocalDate.now(ZONE),id);if(enabled)s.accrue(id);s.notifyUser(link.get("parent_id"),enabled?"Transport service enabled":"Transport service disabled","Your driver changed your family’s service status. Contact the driver to confirm transport arrangements.",null);}return Map.of("enabled",enabled);
 }
}
