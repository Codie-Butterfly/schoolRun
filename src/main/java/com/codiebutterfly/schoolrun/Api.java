package com.codiebutterfly.schoolrun;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.util.*;
import java.nio.file.*;
import static com.codiebutterfly.schoolrun.Store.*;
@RestController @RequestMapping("/api") public class Api {
 final Store s;final Path uploads;
 Api(Store s,@Value("${schoolrun.uploads}")String uploads){this.s=s;this.uploads=Path.of(uploads).toAbsolutePath();}
 @GetMapping("/health") Object health(){return Map.of("status","UP","service","school-run");}
 @PostMapping("/auth/register") @Transactional Object register(@RequestBody Map<String,Object>b){
  String role=text(b,"role",10);if(!Set.of("DRIVER","PARENT").contains(role))throw error(400,"Choose driver or parent");
  String name=text(b,"name",120),phone=phone(text(b,"phone",30)),email=text(b,"email",254),password=text(b,"password",72);
  if(password.length()<10||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw error(400,"Use a password of 10–72 bytes");
  if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw error(400,"Enter a valid email");
  Map<String,Object> profile=new LinkedHashMap<>();profile.put("address",text(b,"address",300));
  if(role.equals("DRIVER")){
   for(String k:List.of("licenceNumber","licenceExpiry","vehicleModel","plateNumber","fitnessExpiry","serviceAreas"))profile.put(k,text(b,k,150));
   try{for(String k:List.of("licenceExpiry","fitnessExpiry"))if(java.time.LocalDate.parse(profile.get(k).toString()).isBefore(java.time.LocalDate.now(ZONE)))throw error(400,"Licence and vehicle fitness must be current");}catch(java.time.format.DateTimeParseException e){throw error(400,"Use YYYY-MM-DD dates");}
   int capacity;try{capacity=Integer.parseInt(b.get("capacity").toString());}catch(Exception e){throw error(400,"Enter vehicle passenger capacity");}if(capacity<1||capacity>80)throw error(400,"Capacity must be between 1 and 80");profile.put("capacity",capacity);
  }else{profile.put("emergencyName",text(b,"emergencyName",120));profile.put("emergencyPhone",phone(text(b,"emergencyPhone",30)));}
  String id=id(),code=role.equals("DRIVER")?"DRV-"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase():null;
  try{s.db.update("INSERT INTO sr_user(id,role,phone,email,name,password_hash,profile,driver_code) VALUES(?,?,?,?,?,?,?,?)",id,role,phone,email,name,s.passwords.encode(password),s.encode(profile),code);}catch(DataIntegrityViolationException e){throw error(409,"This phone number already has an account");}
  return s.session(s.one("SELECT * FROM sr_user WHERE id=?",id));
 }
 @PostMapping("/auth/login") Object login(@RequestBody Map<String,Object>b){var users=s.db.queryForList("SELECT * FROM sr_user WHERE phone=?",phone(text(b,"phone",30)));String password=text(b,"password",72);if(users.isEmpty()||!s.passwords.matches(password,users.get(0).get("password_hash").toString()))throw error(401,"Phone or password is incorrect");return s.session(users.get(0));}
 @GetMapping("/me") Object me(@RequestHeader(value="Authorization",required=false)String a){return s.publicUser(s.user(a));}
 @PostMapping("/auth/logout") void logout(@RequestHeader(value="Authorization",required=false)String a){s.user(a);s.db.update("DELETE FROM sr_session WHERE token_hash=?",hash(a.substring(7)));}
 @GetMapping("/drivers/{code}") Object lookup(@RequestHeader(value="Authorization",required=false)String a,@PathVariable String code){s.role(s.user(a),"PARENT");var d=s.one("SELECT * FROM sr_user WHERE driver_code=? AND role='DRIVER'",code.trim().toUpperCase());Map<String,Object>p=s.decode(d.get("profile"));return Map.of("id",d.get("id"),"name",d.get("name"),"code",d.get("driver_code"),"vehicleModel",p.get("vehicleModel"),"plateNumber",p.get("plateNumber"),"serviceAreas",p.get("serviceAreas"));}
 @PostMapping("/links") @Transactional Object link(@RequestHeader(value="Authorization",required=false)String a,@RequestBody Map<String,Object>b){var u=s.user(a);s.role(u,"PARENT");var d=s.one("SELECT id FROM sr_user WHERE driver_code=? AND role='DRIVER'",text(b,"code",30).toUpperCase());s.one("SELECT id FROM sr_user WHERE id=? FOR UPDATE",u.get("id"));var old=s.db.queryForList("SELECT * FROM sr_link WHERE parent_id=? AND driver_id=?",u.get("id"),d.get("id"));if(!old.isEmpty())return old.get(0);String id=id();s.db.update("INSERT INTO sr_link VALUES(?,?,?)",id,u.get("id"),d.get("id"));s.notifyUser(d.get("id"),"New customer","A parent has linked to your driver code.",null);return Map.of("id",id,"driver_id",d.get("id"));}
 @GetMapping("/links") Object links(@RequestHeader(value="Authorization",required=false)String a){var u=s.user(a);return s.db.queryForList("SELECT l.id,l.driver_id,l.parent_id,d.name driver_name,d.phone driver_phone,d.driver_code,p.name parent_name,p.phone parent_phone FROM sr_link l JOIN sr_user d ON d.id=l.driver_id JOIN sr_user p ON p.id=l.parent_id WHERE l.parent_id=? OR l.driver_id=?",u.get("id"),u.get("id"));}
 @GetMapping("/children") Object children(@RequestHeader(value="Authorization",required=false)String a){var u=s.user(a);var rows=s.db.queryForList("SELECT c.*,l.driver_id,l.parent_id FROM sr_child c JOIN sr_link l ON l.id=c.link_id WHERE l.parent_id=? OR l.driver_id=?",u.get("id"),u.get("id"));for(var c:rows){c.put("details",s.decode(c.get("details")));c.put("cancelledDates",s.db.queryForList("SELECT journey_date FROM sr_override WHERE child_id=? AND absent=true AND journey_date>=? ORDER BY journey_date",java.time.LocalDate.class,c.get("id"),java.time.LocalDate.now(ZONE)));}return rows;}
 @PostMapping("/children") @Transactional Object child(@RequestHeader(value="Authorization",required=false)String a,@RequestBody Map<String,Object>b){var u=s.user(a);s.role(u,"PARENT");String link=text(b,"linkId",36);var owned=s.one("SELECT driver_id FROM sr_link WHERE id=? AND parent_id=?",link,u.get("id"));s.one("SELECT id FROM sr_user WHERE id=? FOR UPDATE",owned.get("driver_id"));Map<String,Object>d=new LinkedHashMap<>();for(String k:List.of("name","grade","homeAddress","homeGate","schoolName","schoolAddress","schoolGate","collectionContact"))d.put(k,text(b,k,300));for(String k:List.of("homeLat","schoolLat"))d.put(k,coordinate(b,k,90));for(String k:List.of("homeLng","schoolLng"))d.put(k,coordinate(b,k,180));Object dob=b.get("dateOfBirth");if(dob!=null&&!dob.toString().isBlank()){try{var date=java.time.LocalDate.parse(dob.toString());if(date.isAfter(java.time.LocalDate.now(ZONE)))throw error(400,"Date of birth cannot be in the future");d.put("dateOfBirth",date.toString());}catch(java.time.format.DateTimeParseException e){throw error(400,"Use YYYY-MM-DD for date of birth");}}String id=id();s.db.update("INSERT INTO sr_child VALUES(?,?,?)",id,link,s.encode(d));s.startChildBilling(id);return Map.of("id",id,"details",d,"link_id",link);}
 @GetMapping("/notifications") Object notifications(@RequestHeader(value="Authorization",required=false)String a){var u=s.user(a);return s.db.queryForList("SELECT * FROM sr_notification WHERE user_id=? AND is_read=false ORDER BY created_at DESC LIMIT 100",u.get("id"));}
 @PostMapping("/notifications/{id}/read") void read(@RequestHeader(value="Authorization",required=false)String a,@PathVariable String id){var u=s.user(a);if(s.db.update("UPDATE sr_notification SET is_read=true WHERE id=? AND user_id=?",id,u.get("id"))==0)throw error(404,"Notification not found");}
 @PostMapping("/broadcast") Object broadcast(@RequestHeader(value="Authorization",required=false)String a,@RequestBody Map<String,Object>b){var u=s.user(a);s.role(u,"DRIVER");String msg=text(b,"message",1000);var parents=s.db.queryForList("SELECT parent_id FROM sr_link WHERE driver_id=?",u.get("id"));for(var p:parents)s.notifyUser(p.get("parent_id"),"Message from your driver",msg,null);return Map.of("recipients",parents.size());}
 @PostMapping("/documents/licence") Object document(@RequestHeader(value="Authorization",required=false)String a,@RequestParam MultipartFile file)throws Exception{
  var u=s.user(a);s.role(u,"DRIVER");byte[] data=file.getBytes();String mime;
  if(data.length>8*1024*1024||data.length<8)throw error(400,"Choose an image under 8 MB");
  if(data[0]==(byte)0xFF&&data[1]==(byte)0xD8&&data[2]==(byte)0xFF)mime="image/jpeg";else if(Arrays.equals(Arrays.copyOf(data,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))mime="image/png";else throw error(400,"Use a JPEG or PNG licence photo");
  Files.createDirectories(uploads);String id=id();Files.write(uploads.resolve(id),data,StandardOpenOption.CREATE_NEW);s.db.update("INSERT INTO sr_document(id,user_id,mime) VALUES(?,?,?)",id,u.get("id"),mime);return Map.of("id",id);
 }
 @GetMapping("/documents/{id}") ResponseEntity<byte[]> document(@RequestHeader(value="Authorization",required=false)String a,@PathVariable String id)throws Exception{var u=s.user(a);var doc=s.one("SELECT * FROM sr_document WHERE id=? AND user_id=?",id,u.get("id"));return ResponseEntity.ok().header("Cache-Control","no-store").contentType(MediaType.parseMediaType(doc.get("mime").toString())).body(Files.readAllBytes(uploads.resolve(doc.get("id").toString())));}
}
