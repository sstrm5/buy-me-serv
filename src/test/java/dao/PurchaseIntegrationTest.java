package dao;

import infrastructure.db.postgres.DatabaseConfig;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

 
class PurchaseIntegrationTest {
    static JdbcTemplate admin, db;
    static PurchaseDao dao;
    static String schema;
    long author, shopper, other;
    @BeforeAll static void database() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getProperty("purchase.integration")), "Enable with -Dpurchase.integration=true against a development PostgreSQL database");
        admin=DatabaseConfig.createJdbcTemplate(null);
        schema="purchase_test_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE SCHEMA "+schema);
        db=new JdbcTemplate(new AbstractDataSource() {
            public Connection getConnection() throws SQLException {
                Connection c=admin.getDataSource().getConnection(); c.setSchema(schema);return c;
            }
            public Connection getConnection(String u,String p) throws SQLException {return getConnection();}
        });
        DatabaseConfig.initializeSchema(db);dao=new PurchaseDao(db);
    }
    @AfterAll static void cleanup() {if(schema!=null && admin!=null)admin.execute("DROP SCHEMA "+schema+" CASCADE");}
    long user() {String name=UUID.randomUUID().toString();return db.queryForObject("INSERT INTO users(login,email,hash_password) VALUES(?,?, 'test') RETURNING id",Long.class,name,name+"@example.test");}
    @BeforeEach void users() {author=user();shopper=user();other=user();}
    long request() {
        long id=db.queryForObject("INSERT INTO buy_requests(user_id,valid_until,status_id) VALUES(?,CURRENT_TIMESTAMP+interval '1 day',(SELECT id FROM buy_request_statuses WHERE code='PENDING')) RETURNING id",Long.class,author);
        db.update("INSERT INTO buy_requests_recipients(buy_request_id,user_id) VALUES(?,?),(?,?)",id,shopper,id,other);
        long item=db.queryForObject("INSERT INTO buy_request_items(buy_request_id,item_name,requested_quantity,unit_id,buy_status_id,approval_status_id) VALUES(?,'Молоко',2,(SELECT id FROM units LIMIT 1),(SELECT id FROM buy_item_statuses WHERE code='PENDING'),(SELECT id FROM approval_statuses LIMIT 1)) RETURNING id",Long.class,id);
        db.update("INSERT INTO item_requirements(buy_request_item_id,replacement_policy_id) VALUES(?,(SELECT id FROM replacement_rules LIMIT 1))",item);
        return id;
    }
    long purchase(long u) {return dao.create(u,"Банк","+79991234567");}
    long item(long r) {return db.queryForObject("SELECT id FROM buy_request_items WHERE buy_request_id=?",Long.class,r);}
    String status(long r) {return db.queryForObject("SELECT s.code FROM buy_requests b JOIN buy_request_statuses s ON s.id=b.status_id WHERE b.id=?",String.class,r);}
    @Test void profileRequisitesSaveEditSelectDeleteAndPopulatePurchase() {
        RequisiteDao requisites=new RequisiteDao(db);
        var first=requisites.createRequisite(shopper,"Первый банк","+79991234567",false);
        assertEquals("Первый банк",dao.defaults(shopper).get("bank"));
        var second=requisites.createRequisite(shopper,"Второй банк","+79997654321",false);
        assertEquals("Первый банк",dao.defaults(shopper).get("bank"));
        assertFalse(requisites.update(other,first.getId(),"Чужой банк","+79990000000"));
        assertFalse(requisites.setDefault(other,second.getId()));
        assertTrue(requisites.update(shopper,second.getId(),"Обновлённый банк","+79990000000"));
        assertTrue(requisites.setDefault(shopper,second.getId()));
        assertEquals("Обновлённый банк",dao.defaults(shopper).get("bank"));
        assertTrue(requisites.delete(shopper,second.getId()));
        assertEquals("Первый банк",dao.defaults(shopper).get("bank"));
        assertTrue(requisites.delete(shopper,first.getId()));assertTrue(dao.defaults(shopper).isEmpty());
    }
    @Test void purchaseUsesSelectedProfileRequisiteOrPrivateTemporaryOne() {
        RequisiteDao requisites=new RequisiteDao(db);
        var first=requisites.createRequisite(shopper,"Основной","+79991234567",false);
        var second=requisites.createRequisite(shopper,"Другой","+79997654321",false);
        assertEquals(first.getId(),dao.profileRequisites(shopper).getFirst().get("id"));
        long saved=dao.create(shopper,second.getId(),false,null,null);
        assertEquals("Другой",dao.get(saved,shopper).get("bank"));
        assertEquals(second.getId(),db.queryForObject("SELECT requisite_id FROM purchase WHERE id=?",Long.class,saved));
        assertThrows(IllegalArgumentException.class,()->dao.create(other,first.getId(),false,null,null));
        assertThrows(IllegalArgumentException.class,()->dao.create(shopper,null,false,null,null));
        long temporary=dao.create(shopper,null,true,"Временный","+79990000000");
        assertEquals("Временный",dao.get(temporary,shopper).get("bank"));
        assertEquals(2,dao.profileRequisites(shopper).size());
        assertEquals("Основной",dao.defaults(shopper).get("bank"));
    }
    @Test void availableRequestsAreGroupedBySenderAndExcludeUnavailableOnes() {
        long first=request(),second=request(),p=purchase(shopper);
        String login=db.queryForObject("SELECT login FROM users WHERE id=?",String.class,author);
        long originalAuthor=author;author=user();long third=request();
        String anotherLogin=db.queryForObject("SELECT login FROM users WHERE id=?",String.class,author);
        author=originalAuthor;
        long expired=request();
        db.update("UPDATE buy_requests SET created_at=CURRENT_TIMESTAMP-interval '2 days',valid_until=CURRENT_TIMESTAMP-interval '1 day' WHERE id=?",expired);
        long hidden=request();db.update("DELETE FROM buy_requests_recipients WHERE buy_request_id=? AND user_id=?",hidden,shopper);
        var groups=dao.available(shopper,p);
        assertEquals(2,groups.size());assertEquals(2,groups.get(login).size());
        assertEquals(third,groups.get(anotherLogin).getFirst().get("id"));
        dao.add(p,shopper,first);
        assertEquals(second,dao.available(shopper,p).get(login).getFirst().get("id"));
        long otherPurchase=purchase(other);dao.add(otherPurchase,other,second);dao.start(otherPurchase,other);
        assertFalse(dao.available(shopper,p).containsKey(login));
    }
    @Test void fullCycleAndPrivacy() {
        long r=request(),p=purchase(shopper),p2=purchase(other);
        assertNull(dao.get(p,author));
        dao.add(p,shopper,r);dao.add(p2,other,r);
        assertEquals("ADDED",status(r));
        assertThrows(IllegalArgumentException.class,()->dao.item(p,shopper,item(r),"BOUGHT","2","125.50",""));
        dao.start(p,shopper);
        assertEquals("COLLECTING",status(r));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM assigned_buy_requests WHERE purchase_id=?",Integer.class,p2));
        assertThrows(IllegalArgumentException.class,()->dao.finish(p,shopper,null,null));
        assertThrows(IllegalArgumentException.class,()->dao.item(p,other,item(r),"BOUGHT","2","125.50",""));
        dao.item(p,shopper,item(r),"BOUGHT","2","125.50","");
        dao.finish(p,shopper,new byte[]{1,2},"image/png");
        assertEquals("COMPLETED",status(r));
        assertThrows(IllegalArgumentException.class,()->dao.payment(p,other,r,true));
        dao.payment(p,author,r,true);
        assertTrue(db.queryForObject("SELECT is_paid_back FROM buy_requests WHERE id=?",Boolean.class,r));
        dao.payment(p,shopper,r,true);
        assertTrue(db.queryForObject("SELECT is_paid_back AND paid_back_at IS NOT NULL FROM buy_requests WHERE id=?",Boolean.class,r));
        dao.payment(p,shopper,r,false);
        assertTrue(db.queryForObject("SELECT NOT is_paid_back AND paid_back_at IS NULL FROM buy_requests WHERE id=?",Boolean.class,r));
        assertEquals("125.50",db.queryForObject("SELECT total_amount_due FROM buy_requests WHERE id=?",String.class,r));
        assertNotNull(dao.get(p,author)); assertNull(dao.get(p,other));
        assertArrayEquals(new byte[]{1,2},(byte[])dao.receipt(p,author).get("receipt_data"));
        assertThrows(IllegalArgumentException.class,()->dao.item(p,shopper,item(r),"NOT_BOUGHT","","",""));
    }
    @Test void partialResultCreatesPrivateEditableDraft() {
        long r=request(),p=purchase(shopper);dao.add(p,shopper,r);dao.start(p,shopper);
        dao.item(p,shopper,item(r),"NOT_FOUND",null,null,null);dao.finish(p,shopper,null,null);
        assertEquals("AWAITING_DECISION",status(r));
        assertThrows(IllegalArgumentException.class,()->dao.decide(r,other,true));
        long copy=dao.decide(r,author,true);
        assertEquals("COMPLETED",status(r));assertEquals("DRAFT",status(copy));
        BuyRequestDao requests=new BuyRequestDao(db);
        assertNull(requests.findByIdForUser(copy,shopper));
        var draft=requests.findByIdForUser(copy,author);assertTrue(draft.isEditable());
        assertTrue(requests.update(copy,author,draft.getUpdatedAt(),"Повтор",draft.getValidUntil(),List.of(shopper,other),draft.getItems()));
        assertEquals("PENDING",status(copy));assertNotNull(requests.findByIdForUser(copy,shopper));
    }
    @Test void concurrentStartsHaveExactlyOneWinner() throws Exception {
        long r=request(),p=purchase(shopper),p2=purchase(other);dao.add(p,shopper,r);dao.add(p2,other,r);
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch gate=new CountDownLatch(1);
            Callable<Boolean> a=()->{gate.await();try{dao.start(p,shopper);return true;}catch(IllegalArgumentException e){return false;}};
            Callable<Boolean> b=()->{gate.await();try{dao.start(p2,other);return true;}catch(IllegalArgumentException e){return false;}};
            var f=pool.submit(a);var g=pool.submit(b);gate.countDown();
            assertNotEquals(f.get(10,TimeUnit.SECONDS),g.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM assigned_buy_requests WHERE buy_request_id=?",Integer.class,r));
    }
    @Test void buyerCannotReadOtherCustomersItemsAndCannotEditForeignItem() {
        long r=request(),p=purchase(shopper);dao.add(p,shopper,r);
        long firstAuthor=author; author=user();long second=request();dao.add(p,shopper,second);
        var view=dao.get(p,firstAuthor);
        var requests=(List<?>)view.get("requests");assertEquals(1,requests.size());
        long another=request(),p2=purchase(shopper);dao.add(p2,shopper,another);
        dao.start(p,shopper);
        assertThrows(IllegalArgumentException.class,()->dao.item(p,shopper,item(another),"BOUGHT","2","10",""));
        assertThrows(IllegalArgumentException.class,()->dao.item(p,shopper,item(r),"BOUGHT","1","10",""));
        assertThrows(IllegalArgumentException.class,()->dao.item(p,shopper,item(r),"REPLACEMENT","2","10",""));
        assertThrows(IllegalArgumentException.class,()->dao.item(p,shopper,item(r),null,"2","10",""));
    }
    @Test void failedGroupStartLeavesAllRequestsInTheirOriginalPurchases() {
        long first=request(),second=request(),p=purchase(shopper),p2=purchase(other);
        dao.add(p,shopper,first);dao.add(p,shopper,second);dao.add(p2,other,first);
        db.update("UPDATE buy_requests SET created_at=CURRENT_TIMESTAMP-interval '2 days',valid_until=CURRENT_TIMESTAMP-interval '1 day' WHERE id=?",second);
        assertThrows(IllegalArgumentException.class,()->dao.start(p,shopper));
        assertEquals("ADDED",status(first));assertEquals("PLANNED",dao.get(p,shopper).get("code"));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM assigned_buy_requests WHERE buy_request_id=?",Integer.class,first));
    }
    @Test void onlyRecipientsCanAddAndExpiredRequestCannotStart() {
        long r=request(),stranger=user(),strangerPurchase=purchase(stranger);
        assertThrows(IllegalArgumentException.class,()->dao.add(strangerPurchase,stranger,r));
        long p=purchase(shopper);long selected=p;dao.add(p,shopper,r);
        db.update("UPDATE buy_requests SET created_at=CURRENT_TIMESTAMP-interval '2 days',valid_until=CURRENT_TIMESTAMP-interval '1 day' WHERE id=?",r);
        assertThrows(IllegalArgumentException.class,()->dao.start(selected,shopper));
        dao.remove(p,shopper,r);assertEquals("PENDING",status(r));
    }
}
