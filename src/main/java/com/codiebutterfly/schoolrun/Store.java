package com.codiebutterfly.schoolrun;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.*;
@Component public class Store {
 @org.springframework.beans.factory.annotation.Value("${schoolrun.subscription-unit}") String billingUnit;
 final JdbcTemplate db;final ObjectMapper json;final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
 public Store(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 static final ZoneId ZONE=ZoneId.of("Africa/Harare");
 static String id(){return UUID.randomUUID().toString();}
 static ResponseStatusException error(int code,String message){return new ResponseStatusException(HttpStatus.valueOf(code),message);}
 static String text(Map<String,Object> b,String key,int max){Object v=b.get(key);if(!(v instanceof String s)||s.trim().isEmpty()||s.length()>max)throw error(400,"Enter a valid "+key);return s.trim();}
 static double coordinate(Map<String,Object>b,String key,double bound){try{double v=Double.parseDouble(b.get(key).toString());if(!Double.isFinite(v)||Math.abs(v)>bound)throw new Exception();return v;}catch(Exception e){throw error(400,"Invalid "+key);}}
 static String phone(String s){s=s.replaceAll("[ ()+-]","");if(s.matches("0[0-9]{9}"))s="263"+s.substring(1);if(s.matches("7[0-9]{8}"))s="263"+s;if(!s.matches("[1-9][0-9]{7,14}"))throw error(400,"Invalid phone number");return "+"+s;}
 static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw error(400,"Invalid data");}}
 @SuppressWarnings("unchecked") <T>T decode(Object o){try{return (T)json.readValue(o.toString(),Object.class);}catch(Exception e){throw new IllegalStateException(e);}}
 Map<String,Object> one(String q,Object...args){var rows=db.queryForList(q,args);if(rows.isEmpty())throw error(404,"Record not found");return rows.get(0);}
 Map<String,Object> user(String auth){if(auth==null||!auth.startsWith("Bearer "))throw error(401,"Please sign in");var rows=db.queryForList("SELECT u.* FROM sr_user u JOIN sr_session s ON s.user_id=u.id WHERE s.token_hash=? AND s.expires_at>?",hash(auth.substring(7)),Timestamp.from(Instant.now()));if(rows.isEmpty())throw error(401,"Session expired");return rows.get(0);}
 void role(Map<String,Object>u,String role){if(!role.equals(u.get("role")))throw error(403,"Not permitted for this account");}
 Map<String,Object> publicUser(Map<String,Object>u){var r=new LinkedHashMap<String,Object>();for(String k:List.of("id","name","phone","email","role","driver_code"))r.put(k,u.get(k));r.put("profile",decode(u.get("profile")));return r;}
 Map<String,Object> session(Map<String,Object>u){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);db.update("INSERT INTO sr_session VALUES(?,?,?)",hash(token),u.get("id"),Timestamp.from(Instant.now().plus(Duration.ofDays(7))));return Map.of("token",token,"user",publicUser(u));}
 Map<String,Object> child(String id,Map<String,Object>u){var c=one("SELECT c.*,l.parent_id,l.driver_id FROM sr_child c JOIN sr_link l ON l.id=c.link_id WHERE c.id=?",id);if(!u.get("id").equals(c.get("parent_id"))&&!u.get("id").equals(c.get("driver_id")))throw error(403,"This child is not linked to your account");return c;}
 void notifyUser(Object user,String title,String message,Object child){db.update("INSERT INTO sr_notification(id,user_id,title,message,child_id) VALUES(?,?,?,?,?)",id(),user,title,message,child);}
 boolean serviceEnabled(Object link){var rows=db.queryForList("SELECT enabled FROM sr_family_service WHERE link_id=?",link);return rows.isEmpty()||Boolean.TRUE.equals(rows.get(0).get("enabled"));}
 void startChildBilling(String child){db.update("INSERT INTO sr_child_billing_start VALUES(?,?)",child,LocalDate.now(ZONE));var c=one("SELECT link_id FROM sr_child WHERE id=?",child);db.update("INSERT INTO sr_family_service SELECT ?,true,? WHERE NOT EXISTS(SELECT 1 FROM sr_family_service WHERE link_id=?)",c.get("link_id"),LocalDate.now(ZONE),c.get("link_id"));if("CHILD".equals(billingUnit))accrue(c.get("link_id"));}
 void sLock(Object link){one("SELECT id FROM sr_link WHERE id=? FOR UPDATE",link);}
 void accrue(Object link){
  sLock(link);var now=LocalDate.now(ZONE);db.update("INSERT INTO sr_family_service SELECT ?,true,? WHERE NOT EXISTS(SELECT 1 FROM sr_family_service WHERE link_id=?)",link,now,link);
  var service=one("SELECT * FROM sr_family_service WHERE link_id=?",link);if(!Boolean.TRUE.equals(service.get("enabled")))return;
  for(var c:db.queryForList("SELECT id FROM sr_child WHERE link_id=?",link)){
   var dates=db.queryForList("SELECT starts FROM sr_child_billing_start WHERE child_id=?",c.get("id"));boolean legacy=dates.isEmpty();if(legacy)db.update("INSERT INTO sr_child_billing_start VALUES(?,?)",c.get("id"),now);
   var start=legacy?now:LocalDate.parse(dates.get(0).get("starts").toString());var active=LocalDate.parse(service.get("active_since").toString());if(start.isBefore(active))start=active;
   for(var month=YearMonth.from(start);!month.isAfter(YearMonth.from(now));month=month.plusMonths(1)){
    boolean covered=legacy&&db.queryForObject("SELECT count(*) FROM sr_entitlement WHERE link_id=? AND billing_month=?",Integer.class,link,month.toString())>0;
    db.update("INSERT INTO sr_child_charge(child_id,billing_month,amount,paid) SELECT ?,?,1,? WHERE NOT EXISTS(SELECT 1 FROM sr_child_charge WHERE child_id=? AND billing_month=?)",c.get("id"),month.toString(),covered,c.get("id"),month.toString());
   }
  }
 }
 boolean paid(Object link,String month){
  int charges=db.queryForObject("SELECT count(*) FROM sr_child_charge q JOIN sr_child c ON c.id=q.child_id WHERE c.link_id=? AND q.billing_month=?",Integer.class,link,month);
  if(charges>0)return db.queryForObject("SELECT count(*) FROM sr_child_charge q JOIN sr_child c ON c.id=q.child_id WHERE c.link_id=? AND q.billing_month=? AND q.paid=false",Integer.class,link,month)==0;
  return db.queryForObject("SELECT count(*) FROM sr_entitlement WHERE link_id=? AND billing_month=?",Integer.class,link,month)>0;
 }
}
