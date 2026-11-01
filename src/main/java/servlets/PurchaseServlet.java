package servlets;

import dao.PurchaseDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

@WebServlet({"/start-purchase", "/purchases", "/purchases/*"})
@MultipartConfig(maxFileSize=5242880,maxRequestSize=6291456)
public class PurchaseServlet extends HttpServlet {
    private PurchaseDao dao;
    @Override public void init() { dao=(PurchaseDao)getServletContext().getAttribute("purchaseDao"); }
    private long id(HttpServletRequest req) {
        try { return Long.parseLong(req.getPathInfo().split("/")[1]); }
        catch(Exception e) { throw new IllegalArgumentException("Поход не найден."); }
    }
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        Long user=(Long)req.getSession(false).getAttribute("userId");
        if(req.getSession().getAttribute("purchaseCsrf")==null) req.getSession().setAttribute("purchaseCsrf",UUID.randomUUID().toString());
        if("/start-purchase".equals(req.getServletPath())) {
            req.setAttribute("defaults",dao.defaults(user));
            req.setAttribute("profileRequisites",dao.profileRequisites(user));
            req.getRequestDispatcher("/WEB-INF/purchases/create.jsp").forward(req,resp); return;
        }
        if(req.getPathInfo()==null || "/".equals(req.getPathInfo())) {
            boolean history="history".equals(req.getParameter("section"));
            req.setAttribute("history",history); req.setAttribute("purchases",dao.list(user,history));
            req.getRequestDispatcher("/WEB-INF/purchases/list.jsp").forward(req,resp); return;
        }
        long id;
        try { id=id(req); } catch(IllegalArgumentException e) { resp.sendError(404); return; }
        if(!req.getPathInfo().matches("/[1-9][0-9]*(/receipt)?")) {resp.sendError(404);return;}
        var purchase=dao.get(id,user); if(purchase==null) {resp.sendError(404);return;}
        if(req.getPathInfo().endsWith("/receipt")) {
            var receipt=dao.receipt(id,user); byte[] bytes=(byte[])receipt.get("receipt_data");
            if(bytes==null) {resp.sendError(404);return;}
            resp.setContentType((String)receipt.get("receipt_type"));
            resp.setHeader("X-Content-Type-Options","nosniff"); resp.setHeader("Cache-Control","private, no-store");
            resp.getOutputStream().write(bytes);return;
        }
        req.setAttribute("purchase",purchase);
        boolean owner=user.equals(purchase.get("shopper_id")); req.setAttribute("owner",owner);
        if(owner && "PLANNED".equals(purchase.get("code")))
            req.setAttribute("availableGroups",dao.available(user,id));
        req.getRequestDispatcher("/WEB-INF/purchases/detail.jsp").forward(req,resp);
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        req.setCharacterEncoding("UTF-8");
        Long user=(Long)req.getSession(false).getAttribute("userId");
        try {
            Object csrf=req.getSession().getAttribute("purchaseCsrf");
            if(csrf==null || !csrf.equals(req.getParameter("csrfToken"))) {resp.sendError(403);return;}
            if("/start-purchase".equals(req.getServletPath())) {
                boolean temporary="on".equals(req.getParameter("temporary"));
                String selected=req.getParameter("requisiteId");
                Long requisiteId=temporary || selected==null || selected.isBlank()?null:Long.valueOf(selected);
                long id=dao.create(user,requisiteId,temporary,req.getParameter("bank"),req.getParameter("phone"));
                resp.sendRedirect(req.getContextPath()+"/purchases/"+id);return;
            }
            String action=req.getParameter("action");
            if("decide".equals(action) || "repeat".equals(action)) {
                long request=dao.decide(Long.parseLong(req.getParameter("requestId")),user,"repeat".equals(action));
                resp.sendRedirect(req.getContextPath()+("repeat".equals(action)?"/edit-request?id=":"/requests/")+request);return;
            }
            long id=id(req);
            switch(action==null?"":action) {
                case "add" -> dao.add(id,user,Long.parseLong(req.getParameter("requestId")));
                case "remove" -> dao.remove(id,user,Long.parseLong(req.getParameter("requestId")));
                case "start" -> dao.start(id,user);
                case "item" -> dao.item(id,user,Long.parseLong(req.getParameter("itemId")),req.getParameter("status"),req.getParameter("quantity"),req.getParameter("price"),req.getParameter("actualName"));
                case "finish" -> {
                    Part file=req.getPart("receipt"); byte[] data=null; String type=null;
                    if(file!=null && file.getSize()>0) {
                        try(var stream=file.getInputStream()) {data=stream.readAllBytes();}
                        boolean png=data.length>8 && data[0]==(byte)137 && data[1]==80 && data[2]==78 && data[3]==71;
                        boolean jpg=data.length>3 && data[0]==(byte)255 && data[1]==(byte)216 && data[2]==(byte)255;
                        if((!png && !jpg) || ImageIO.read(new ByteArrayInputStream(data))==null) throw new IllegalArgumentException("Чек должен быть изображением PNG или JPEG до 5 МБ.");
                        type=png?"image/png":"image/jpeg";
                    }
                    dao.finish(id,user,data,type);
                }
                default -> throw new IllegalArgumentException("Неизвестное действие.");
            }
            if (("paid".equals(action) || "unpaid".equals(action)) && "true".equals(req.getParameter("returnToRequest"))) {
                resp.sendRedirect(req.getContextPath()+"/requests/"+Long.parseLong(req.getParameter("requestId")));
                return;
            }
            resp.sendRedirect(req.getContextPath()+"/purchases/"+id);
        } catch(IllegalArgumentException e) {
            resp.setStatus(400);req.setAttribute("error", e instanceof NumberFormatException?"Некорректный идентификатор.":e.getMessage());doGet(req,resp);
        } catch(IllegalStateException e) {
            resp.setStatus(400); req.setAttribute("error","Размер чека не должен превышать 5 МБ.");doGet(req,resp);
        } catch(org.springframework.dao.DataAccessException e) {
            getServletContext().log("Не удалось сохранить закупку",e);resp.setStatus(500);
            req.setAttribute("error","Не удалось сохранить изменения. Попробуйте ещё раз.");doGet(req,resp);
        }
    }
}
