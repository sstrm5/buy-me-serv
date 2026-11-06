package dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;

 
public class PurchaseDao {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    public PurchaseDao(JdbcTemplate db) {
        this.db = db;
        tx = new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource()));
    }
    public List<Map<String,Object>> list(long user, boolean history) {
        return db.queryForList("""
                SELECT p.id, s.code, s.name, p.created_at,
                NOT EXISTS(SELECT 1 FROM assigned_buy_requests a JOIN buy_requests b ON b.id=a.buy_request_id WHERE a.purchase_id=p.id AND b.total_amount_due>0 AND NOT b.is_paid_back) AS all_paid,
                EXISTS(SELECT 1 FROM assigned_buy_requests a JOIN buy_requests b ON b.id=a.buy_request_id WHERE a.purchase_id=p.id AND b.total_amount_due>0) AS payment_required
                FROM purchase p JOIN purchase_statuses s ON s.id=p.status_id WHERE p.shopper_id=? AND (s.code='COMPLETED')=? ORDER BY p.id DESC
                """, user, history);
    }
    public Map<String,Object> defaults(long user) {
        var rows=db.queryForList("SELECT r.id, r.bank, r.phone_number FROM users u JOIN requisites r ON r.id=u.default_requisite_id AND r.user_id=u.id WHERE u.id=?", user);
        return rows.isEmpty()? Map.of():rows.getFirst();
    }
    public Map<String,List<Map<String,Object>>> available(long user, long purchase) {
        var rows = db.queryForList("""
            SELECT r.id, u.login, r.comment, r.valid_until FROM buy_requests r
            JOIN users u ON u.id=r.user_id JOIN buy_request_statuses s ON s.id=r.status_id
            JOIN buy_requests_recipients rr ON rr.buy_request_id=r.id
            WHERE rr.user_id=? AND s.code IN ('PENDING','ADDED') AND r.valid_until>CURRENT_TIMESTAMP
              AND NOT EXISTS (SELECT 1 FROM assigned_buy_requests a WHERE a.buy_request_id=r.id AND a.purchase_id=?)
            ORDER BY u.login, r.id DESC
            """, user, purchase);
        Map<String,List<Map<String,Object>>> groups = new LinkedHashMap<>();
        for (var row : rows) groups.computeIfAbsent((String)row.get("login"), key -> new ArrayList<>()).add(row);
        return groups;
    }
    public List<Map<String,Object>> profileRequisites(long user) {
        return db.queryForList("""
            SELECT r.id, r.bank, r.phone_number, r.id=u.default_requisite_id AS is_default
            FROM requisites r JOIN users u ON u.id=r.user_id
            WHERE r.user_id=? AND NOT r.is_temporary
            ORDER BY (r.id=u.default_requisite_id) DESC NULLS LAST, r.id
            """,user);
    }
    public long create(long user, Long requisiteId, boolean temporary, String bank, String phone) {
        if (temporary) return create(user,bank,phone);
        if (requisiteId==null) throw new IllegalArgumentException("Выберите реквизиты профиля или укажите временные.");
        return tx.execute(t -> {
            var rows=db.queryForList("SELECT id FROM requisites WHERE id=? AND user_id=? AND NOT is_temporary FOR UPDATE",requisiteId,user);
            if(rows.isEmpty()) throw new IllegalArgumentException("Выбранные реквизиты недоступны. Обновите страницу.");
            return db.queryForObject("INSERT INTO purchase(shopper_id,requisite_id,status_id) VALUES(?,?,(SELECT id FROM purchase_statuses WHERE code='PLANNED')) RETURNING id",Long.class,user,requisiteId);
        });
    }
    public long create(long user, String bank, String phone) {
        if(bank==null || bank.isBlank() || bank.length()>255 || phone==null || !phone.matches("[-+0-9() ]{7,32}"))
            throw new IllegalArgumentException("Укажите банк и корректный номер телефона для перевода.");
        return tx.execute(t -> {
            Long requisite=db.queryForObject("INSERT INTO requisites(user_id,bank,phone_number,is_temporary) VALUES(?,?,?,true) RETURNING id",Long.class,user,bank.strip(),phone.strip());
            return db.queryForObject("INSERT INTO purchase(shopper_id,requisite_id,status_id) VALUES(?,?,(SELECT id FROM purchase_statuses WHERE code='PLANNED')) RETURNING id",Long.class,user,requisite);
        });
    }
    public Map<String,Object> get(long id,long user) {
        var rows=db.queryForList("""
            SELECT p.id, p.shopper_id, s.code, s.name, r.bank, r.phone_number,
                   p.receipt_data IS NOT NULL AS has_receipt
            FROM purchase p JOIN purchase_statuses s ON s.id=p.status_id JOIN requisites r ON r.id=p.requisite_id
            WHERE p.id=? AND (p.shopper_id=? OR EXISTS(SELECT 1 FROM assigned_buy_requests a JOIN buy_requests b ON b.id=a.buy_request_id WHERE a.purchase_id=p.id AND b.user_id=?))
            """, id,user,user);
        if(rows.isEmpty()) return null;
        var p=rows.getFirst();
        var requests=db.queryForList("""
            SELECT b.id, b.user_id, u.login, b.comment, b.total_amount_due, b.is_paid_back, b.paid_back_at, s.code, s.name
            FROM assigned_buy_requests a JOIN buy_requests b ON b.id=a.buy_request_id
            JOIN users u ON u.id=b.user_id JOIN buy_request_statuses s ON s.id=b.status_id
            WHERE a.purchase_id=? AND (?= ? OR b.user_id=?) ORDER BY b.id
            """,id,user,p.get("shopper_id"),user);
        for(var r:requests) r.put("items",db.queryForList("""
            SELECT i.*, s.code, s.name AS status_name, u.name AS unit_name, q.max_unit_price, q.max_total_price, rp.name AS replacement_name
            FROM buy_request_items i JOIN buy_item_statuses s ON s.id=i.buy_status_id JOIN units u ON u.id=i.unit_id
            JOIN item_requirements q ON q.buy_request_item_id=i.id JOIN replacement_rules rp ON rp.id=q.replacement_policy_id
            WHERE i.buy_request_id=? ORDER BY i.id
            """,r.get("id")));
        p.put("requests", requests);
        return p;
    }
    
    
    private String lock(long id,long user) {
        db.execute("SELECT pg_advisory_xact_lock(726491230985117604)");
        var rows=db.queryForList("SELECT s.code FROM purchase p JOIN purchase_statuses s ON s.id=p.status_id WHERE p.id=? AND p.shopper_id=? FOR UPDATE OF p",id,user);
        if(rows.isEmpty()) throw new IllegalArgumentException("Поход не найден или недоступен.");
        return (String)rows.getFirst().get("code");
    }
    private void require(String actual,String expected) {
        if(!expected.equals(actual)) throw new IllegalArgumentException("Состояние похода изменилось. Обновите страницу.");
    }
    public void add(long id,long user,long request) {
        tx.executeWithoutResult(t -> {
            require(lock(id,user),"PLANNED");
            db.queryForList("SELECT id FROM buy_requests WHERE id=? FOR UPDATE",request);
            int n=db.update("""
                INSERT INTO assigned_buy_requests(buy_request_id,purchase_id,status_id)
                SELECT r.id,?,(SELECT id FROM assigned_buy_request_statuses WHERE code='ADDED') FROM buy_requests r
                JOIN buy_request_statuses s ON s.id=r.status_id
                WHERE r.id=? AND r.valid_until>CURRENT_TIMESTAMP AND s.code IN ('PENDING','ADDED')
                AND EXISTS(SELECT 1 FROM buy_requests_recipients rr WHERE rr.buy_request_id=r.id AND rr.user_id=?)
                ON CONFLICT DO NOTHING
                """,id,request,user);
            if(n!=1) throw new IllegalArgumentException("Запрос уже добавлен, просрочен или недоступен.");
            db.update("UPDATE buy_requests SET status_id=(SELECT id FROM buy_request_statuses WHERE code='ADDED') WHERE id=?",request);
        });
    }
    public void remove(long id,long user,long request) {
        tx.executeWithoutResult(t -> {
            require(lock(id,user),"PLANNED");
            db.queryForList("SELECT id FROM buy_requests WHERE id=? FOR UPDATE",request);
            db.update("DELETE FROM assigned_buy_requests WHERE purchase_id=? AND buy_request_id=?",id,request);
            db.update("UPDATE buy_requests SET status_id=(SELECT id FROM buy_request_statuses WHERE code='PENDING') WHERE id=? AND status_id=(SELECT id FROM buy_request_statuses WHERE code='ADDED') AND NOT EXISTS(SELECT 1 FROM assigned_buy_requests WHERE buy_request_id=?)",request,request);
        });
    }
    public void start(long id,long user) {
        tx.executeWithoutResult(t -> {
            require(lock(id,user),"PLANNED");
            var ids=db.queryForList("SELECT b.id FROM buy_requests b JOIN assigned_buy_requests a ON a.buy_request_id=b.id WHERE a.purchase_id=? ORDER BY b.id FOR UPDATE OF b",Long.class,id);
            if(ids.isEmpty()) throw new IllegalArgumentException("Добавьте хотя бы один запрос.");
            Integer invalid=db.queryForObject("SELECT count(*) FROM buy_requests b JOIN assigned_buy_requests a ON a.buy_request_id=b.id JOIN buy_request_statuses s ON s.id=b.status_id WHERE a.purchase_id=? AND (b.valid_until<=CURRENT_TIMESTAMP OR s.code NOT IN ('ADDED','PENDING'))",Integer.class,id);
            if(invalid!=0) throw new IllegalArgumentException("Удалите просроченные или уже недоступные запросы перед началом сборки.");
            for(Long request:ids) {
                db.update("DELETE FROM assigned_buy_requests WHERE buy_request_id=? AND purchase_id<>?",request,id);
                db.update("UPDATE buy_requests SET status_id=(SELECT id FROM buy_request_statuses WHERE code='COLLECTING') WHERE id=?",request);
            }
            db.update("UPDATE assigned_buy_requests SET status_id=(SELECT id FROM assigned_buy_request_statuses WHERE code='COLLECTING') WHERE purchase_id=?",id);
            db.update("UPDATE purchase SET status_id=(SELECT id FROM purchase_statuses WHERE code='COLLECTING') WHERE id=?",id);
        });
    }
    public static BigDecimal decimal(String value,int scale,boolean positive) {
        try {
            BigDecimal n=new BigDecimal(value);
            if(n.scale()>scale || n.signum()<(positive?1:0) || n.compareTo(new BigDecimal("999999999"))>0) throw new NumberFormatException();
            return n;
        } catch(Exception e) { throw new IllegalArgumentException("Укажите корректные количество и стоимость товара."); }
    }
    public void item(long id,long user,long item,String code,String quantity,String price,String actualName) {
        if(code==null || !Set.of("BOUGHT","QUANTITY_CHANGED","REPLACEMENT","NOT_FOUND","NOT_BOUGHT","PENDING").contains(code)) throw new IllegalArgumentException("Неизвестный статус товара.");
        boolean bought=Set.of("BOUGHT","QUANTITY_CHANGED","REPLACEMENT").contains(code);
        BigDecimal q=bought?decimal(quantity,3,true):null;
        BigDecimal cost=bought?decimal(price,2,false):null;
        if(actualName!=null && actualName.length()>255) throw new IllegalArgumentException("Название товара слишком длинное.");
        if("REPLACEMENT".equals(code) && (actualName==null || actualName.isBlank())) throw new IllegalArgumentException("Укажите название замены.");
        tx.executeWithoutResult(t -> {
            require(lock(id,user),"COLLECTING");
            var rows=db.queryForList("SELECT i.requested_quantity FROM buy_request_items i JOIN assigned_buy_requests a ON a.buy_request_id=i.buy_request_id WHERE i.id=? AND a.purchase_id=?",item,id);
            if(rows.isEmpty()) throw new IllegalArgumentException("Товар не принадлежит этому походу.");
            if("BOUGHT".equals(code) && q.compareTo((BigDecimal)rows.getFirst().get("requested_quantity"))!=0) throw new IllegalArgumentException("Для другого количества выберите соответствующий статус.");
            db.update("UPDATE buy_request_items SET buy_status_id=(SELECT id FROM buy_item_statuses WHERE code=?),actual_quantity=?,actual_price=?,actual_name=?,decision_at=CURRENT_TIMESTAMP WHERE id=?",code,q,cost,actualName,item);
        });
    }
    public void finish(long id,long user,byte[] receipt,String type) {
        tx.executeWithoutResult(t -> {
            require(lock(id,user),"COLLECTING");
            Integer pending=db.queryForObject("SELECT count(*) FROM buy_request_items i JOIN assigned_buy_requests a ON a.buy_request_id=i.buy_request_id JOIN buy_item_statuses s ON s.id=i.buy_status_id WHERE a.purchase_id=? AND s.code='PENDING'",Integer.class,id);
            if(pending!=0) throw new IllegalArgumentException("Обработайте все товары перед завершением похода.");
            db.update("""
                UPDATE buy_requests b SET total_amount_due=(SELECT COALESCE(sum(i.actual_price),0) FROM buy_request_items i WHERE i.buy_request_id=b.id),
                status_id=(SELECT id FROM buy_request_statuses WHERE code=CASE WHEN EXISTS(SELECT 1 FROM buy_request_items i JOIN buy_item_statuses s ON s.id=i.buy_status_id WHERE i.buy_request_id=b.id AND s.code<>'BOUGHT') THEN 'AWAITING_DECISION' ELSE 'COMPLETED' END)
                WHERE b.id IN(SELECT buy_request_id FROM assigned_buy_requests WHERE purchase_id=?)
                """,id);
            db.update("UPDATE assigned_buy_requests SET status_id=(SELECT id FROM assigned_buy_request_statuses WHERE code='COMPLETED') WHERE purchase_id=?",id);
            db.update("UPDATE purchase SET status_id=(SELECT id FROM purchase_statuses WHERE code='COMPLETED'),completed_at=CURRENT_TIMESTAMP,receipt_data=?,receipt_type=? WHERE id=?",receipt,type,id);
        });
    }
    public void payment(long purchase,long user,long request,boolean paid) {
        tx.executeWithoutResult(t -> {
            db.execute("SELECT pg_advisory_xact_lock(726491230985117604)");
            var allowed=db.queryForList("""
                SELECT p.id FROM purchase p JOIN purchase_statuses s ON s.id=p.status_id
                JOIN assigned_buy_requests a ON a.purchase_id=p.id JOIN buy_requests b ON b.id=a.buy_request_id
                WHERE p.id=? AND b.id=? AND s.code='COMPLETED' AND (p.shopper_id=? OR b.user_id=?)
                FOR UPDATE OF p, b
                """,purchase,request,user,user);
            if(allowed.isEmpty()) throw new IllegalArgumentException("Запрос недоступен или закупка ещё не завершена.");
            int changed=db.update("""
                UPDATE buy_requests SET is_paid_back=?, paid_back_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END
                WHERE id=? AND total_amount_due>0
                  AND EXISTS(SELECT 1 FROM assigned_buy_requests WHERE buy_request_id=? AND purchase_id=?)
                """,paid,paid,request,request,purchase);
            if(changed!=1) throw new IllegalArgumentException("Запрос недоступен или оплата не требуется.");
        });
    }
    public List<Map<String,Object>> requestPurchases(long request,long user) {
        return db.queryForList("""
            SELECT p.id, p.shopper_id, u.login, s.code, s.name, b.is_paid_back, b.total_amount_due FROM assigned_buy_requests a JOIN purchase p ON p.id=a.purchase_id
            JOIN users u ON u.id=p.shopper_id JOIN purchase_statuses s ON s.id=p.status_id
            JOIN buy_requests b ON b.id=a.buy_request_id WHERE b.id=? AND (b.user_id=? OR p.shopper_id=?) ORDER BY p.id
            """,request,user,user);
    }
    public Map<String,Object> receipt(long id,long user) {
        if(get(id,user)==null) return null;
        return db.queryForMap("SELECT receipt_data,receipt_type FROM purchase WHERE id=?",id);
    }
    public long decide(long request,long user,boolean repeat) {
        return tx.execute(t -> {
            db.execute("SELECT pg_advisory_xact_lock(726491230985117604)");
            var rows=db.queryForList("SELECT b.id FROM buy_requests b JOIN buy_request_statuses s ON s.id=b.status_id WHERE b.id=? AND b.user_id=? AND s.code='AWAITING_DECISION' FOR UPDATE OF b",request,user);
            if(rows.isEmpty()) throw new IllegalArgumentException("Запрос уже обработан или недоступен.");
            long result=request;
            if(repeat) {
                result=db.queryForObject("INSERT INTO buy_requests(user_id,comment,valid_until,status_id) SELECT user_id,comment,CURRENT_TIMESTAMP+interval '1 day',(SELECT id FROM buy_request_statuses WHERE code='DRAFT') FROM buy_requests WHERE id=? RETURNING id",Long.class,request);
                db.update("INSERT INTO buy_requests_recipients(buy_request_id,user_id) SELECT ?,user_id FROM buy_requests_recipients WHERE buy_request_id=?",result,request);
                var items=db.queryForList("SELECT i.id FROM buy_request_items i JOIN buy_item_statuses s ON s.id=i.buy_status_id WHERE i.buy_request_id=? AND s.code<>'BOUGHT'",Long.class,request);
                for(long old:items) {
                    Long next=db.queryForObject("INSERT INTO buy_request_items(buy_request_id,item_name,buy_status_id,approval_status_id,comment,requested_quantity,unit_id,item_photo_url) SELECT ?,item_name,(SELECT id FROM buy_item_statuses WHERE code='PENDING'),(SELECT id FROM approval_statuses WHERE code='NOT_REQUIRED'),comment,requested_quantity,unit_id,item_photo_url FROM buy_request_items WHERE id=? RETURNING id",Long.class,result,old);
                    db.update("INSERT INTO item_requirements(buy_request_item_id,max_unit_price,max_total_price,replacement_policy_id,quantity_tolerance) SELECT ?,max_unit_price,max_total_price,replacement_policy_id,quantity_tolerance FROM item_requirements WHERE buy_request_item_id=?",next,old);
                }
            }
            db.update("UPDATE buy_requests SET status_id=(SELECT id FROM buy_request_statuses WHERE code='COMPLETED') WHERE id=?",request);
            return result;
        });
    }
}
