package com.codiebutterfly.schoolrun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class PaynowClient {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PaynowClient.class);
 @jakarta.annotation.PostConstruct
 void logConfiguration() {
  log.info("Paynow configuration enabled={} testMode={} credentialsPresent={} testDriverCount={}", enabled,testMode,!id.isBlank() && !key.isBlank(), Arrays.stream(testCustomers.split(",")).filter(v->!v.isBlank()).count());
  if(testMode && testCustomers.isBlank()) log.warn("Paynow test driver allowlist is empty: all payment initiation requests will be blocked");
 }

 private final String id, key, base;
 private final boolean enabled;
 @Value("${paynow.test-mode:true}") private boolean testMode=true;
 @Value("${paynow.test-driver-ids:}") private String testCustomers="";
 public void allowDriver(String id) {
  if(testMode && Arrays.stream(testCustomers.split(",")).map(String::trim).noneMatch(id.toString()::equals)) {
   log.warn("Paynow blocked reason=test_driver_not_allowed driverId={}",id);
   throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Payments are being tested; please contact School Run");
  }
 }
 @Value("${paynow.test-email:}") private String testEmail="";
 public static class InitiationRejected extends ResponseStatusException {
  public InitiationRejected(String message){super(HttpStatus.BAD_REQUEST,message);}
 }
 public static String validateMethod(String method) {
  if(method==null || method.equals("browser"))return "browser";
  if(method.equals("ecocash"))return method;
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported payment method");
 }
 public static String normalizePhone(String phone) {
  if(phone==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"EcoCash number is required");
  String value=phone.replaceAll("[\\s-]", "");
  if(value.startsWith("+263"))value="0"+value.substring(4);
  else if(value.startsWith("263"))value="0"+value.substring(3);
  else if(value.matches("7[0-9]{8}"))value="0"+value;
  if(!value.matches("07[0-9]{8}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid Zimbabwe mobile number");
  return value;
 }
 public void validateCheckout(String method,String phone,String email) {
  validateMethod(method);
  if(method.equals("ecocash")) {
   normalizePhone(phone);
   if(!testMode && (email==null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Add an email address to your customer profile for EcoCash payment");
  }
 }
 private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
     .followRedirects(HttpClient.Redirect.NEVER).build();
 public PaynowClient(@Value("${paynow.integration-id:}") String id,
                     @Value("${paynow.integration-key:}") String key,
                     @Value("${paynow.public-base-url:}") String base,
                     @Value("${paynow.enabled:false}") boolean enabled) {
  this.id=id; this.key=key; this.base=base.replaceAll("/+$", ""); this.enabled=enabled;
 }
 public void requireConfigured() {
  if (!enabled || id.isBlank() || key.isBlank() || !base.startsWith("https://") || (testMode && testEmail.isBlank())) {
   log.warn("Paynow blocked reason=not_configured");
   throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Online payments are not available yet");
  }
 }
 public Map<String,String> initiate(String reference, String amount) {
  return initiate(reference,amount,"browser",null,null);
 }
 LinkedHashMap<String,String> initiationFields(String reference,String amount,String method,String phone,String email) {
  requireConfigured(); validateCheckout(method,phone,email);
  LinkedHashMap<String,String> fields=new LinkedHashMap<>();
  fields.put("id",id); fields.put("reference",reference); fields.put("amount",amount);
  fields.put("additionalinfo","School Run monthly subscription");
  fields.put("returnurl",base+"/api/paynow/return");fields.put("resulturl",base+"/api/paynow/result");
  if(testMode) fields.put("authemail",testEmail);
  else if(email!=null && !email.isBlank())fields.put("authemail",email.trim());
  if(method.equals("ecocash")) {
   fields.put("phone",normalizePhone(phone));fields.put("method","ecocash");
   fields.put("merchanttrace",reference.replace("-",""));
  }
  fields.put("status","Message");fields.put("hash",hash(fields,key));return fields;
 }
 public Map<String,String> initiate(String reference,String amount,String method,String phone,String email) {
  Map<String,String> result=post(method.equals("ecocash")?
    "https://www.paynow.co.zw/interface/remotetransaction":"https://www.paynow.co.zw/interface/initiatetransaction",
    encode(initiationFields(reference,amount,method,phone,email)));
  return acceptInitiation(result,method);
 }
 Map<String,String> acceptInitiation(Map<String,String> result,String method) {
  // Only a definite HTTPS error response permits a new attempt; network/hash failures remain UNKNOWN.
  if("error".equalsIgnoreCase(result.get("status"))) {
   String error=result.getOrDefault("error", "").toLowerCase(Locale.ROOT);
   log.warn("Paynow initiation rejected category={}",error.contains("insufficient")?"insufficient_balance":error.contains("hash")?"signature":error.contains("email")?"email":error.contains("integration")?"integration":error.contains("phone")?"phone":"other");
   throw new InitiationRejected(error.contains("insufficient")?"Insufficient wallet balance. Please top up or choose another method.":
     "Paynow rejected this payment request. Please check your details or contact School Run.");
  }
  verify(result);
  String status=result.getOrDefault("status", "");
  if(!status.equalsIgnoreCase("ok") && !(method.equals("ecocash") && status.equalsIgnoreCase("sent")))throw invalid();
  if(method.equals("browser"))safeUrl(result.get("browserurl"));
  safePollUrl(result.get("pollurl"));return result;
 }
 public Map<String,String> poll(String url) {
  requireConfigured(); safePollUrl(url);
  Map<String,String> fields=post(url, ""); verify(fields); return fields;
 }
 public void verify(Map<String,String> fields) {
  requireConfigured();
  String supplied=fields.get("hash");
  if(supplied==null || !supplied.matches("[0-9a-fA-F]{128}") ||
    !MessageDigest.isEqual(hash(fields,key).getBytes(StandardCharsets.US_ASCII),
       supplied.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) throw invalid();
 }
 public static String hash(Map<String,String> fields,String key) {
  StringBuilder data=new StringBuilder();
  fields.forEach((k,v)->{if(!k.equalsIgnoreCase("hash")) data.append(v);}); data.append(key);
  try { return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-512")
     .digest(data.toString().getBytes(StandardCharsets.UTF_8))); }
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static LinkedHashMap<String,String> parse(String raw) {
  if(raw==null || raw.length()>32768) throw invalid();
  LinkedHashMap<String,String> fields=new LinkedHashMap<>();
  try {
   for(String pair:raw.split("&")) {
    String[] kv=pair.split("=",2); if(kv.length!=2) throw invalid();
    String k=URLDecoder.decode(kv[0],StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
    if(fields.containsKey(k)) throw invalid();
    fields.put(k,URLDecoder.decode(kv[1],StandardCharsets.UTF_8));
   }
  } catch(IllegalArgumentException e){throw invalid();}
  return fields;
 }
 static String encode(Map<String,String> fields) {
  return fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+
   URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
 }
 public static URI safeUrl(String url) {
  try {
   URI uri=URI.create(url);
   if(!"https".equals(uri.getScheme()) || !"www.paynow.co.zw".equalsIgnoreCase(uri.getHost()) ||
      uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443) || uri.getFragment()!=null) throw invalid();
   return uri;
  } catch(IllegalArgumentException | NullPointerException e){throw invalid();}
 }
 public static void safePollUrl(String url) {
  URI uri=safeUrl(url);
  if(!uri.getPath().toLowerCase(Locale.ROOT).startsWith("/interface/checkpayment")) throw invalid();
 }
 private Map<String,String> post(String url,String body) {
  long started=System.nanoTime();
  String operation=url.contains("checkpayment")?"poll":url.contains("remotetransaction")?"express":"browser";
  log.info("Paynow gateway request operation={}",operation);
  try {
   HttpResponse<String> response=http.send(HttpRequest.newBuilder(safeUrl(url))
    .timeout(Duration.ofSeconds(25)).header("Content-Type","application/x-www-form-urlencoded")
    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
   log.info("Paynow gateway response operation={} httpStatus={} durationMs={}",operation,response.statusCode(),(System.nanoTime()-started)/1_000_000);
   if(response.statusCode()!=200) throw invalid();
   return parse(response.body());
  } catch(InterruptedException e){Thread.currentThread().interrupt();throw invalid();}
    catch(java.io.IOException e){log.warn("Paynow gateway failed operation={} errorType={}",operation,e.getClass().getSimpleName());throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Paynow is temporarily unreachable");}
 }
 private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Paynow response");}
}
