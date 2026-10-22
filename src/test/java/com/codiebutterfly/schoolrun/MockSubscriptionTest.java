package com.codiebutterfly.schoolrun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.YearMonth;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:mocksubscriptions;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE","schoolrun.mock-subscriptions=true"})
@Transactional
class MockSubscriptionTest {
 @Autowired Store s;
 @Test void existingAndNewChildrenAreCoveredWithoutPayment(){
  for(String id:new String[]{"p","d"})s.db.update("INSERT INTO sr_user(id,role,phone,email,name,password_hash,profile) VALUES(?,?,?,?,?,?,?)",id,id.equals("p")?"PARENT":"DRIVER",id,id+"@test.invalid",id,"unused","{}");
  s.db.update("INSERT INTO sr_link VALUES('l','p','d')");
  String month=YearMonth.now(Store.ZONE).toString();
  s.db.update("INSERT INTO sr_child VALUES('old','l','{}')");
  s.db.update("INSERT INTO sr_child_charge(child_id,billing_month,amount,paid) VALUES('old',?,1,false)",month);
  assertTrue(s.paid("l",month));
  s.db.update("INSERT INTO sr_child VALUES('new','l','{}')");
  s.startChildBilling("new");
  assertTrue(s.paid("l",month));
  assertEquals(0,s.db.queryForObject("SELECT amount FROM sr_child_charge WHERE child_id='new'",Integer.class));
  assertEquals(0,s.db.queryForObject("SELECT count(*) FROM sr_payment",Integer.class));
  assertFalse(s.db.queryForObject("SELECT paid FROM sr_child_charge WHERE child_id='old'",Boolean.class));
  s.db.update("UPDATE sr_family_service SET enabled=false WHERE link_id='l'");
  assertFalse(s.serviceEnabled("l"));
 }
}
