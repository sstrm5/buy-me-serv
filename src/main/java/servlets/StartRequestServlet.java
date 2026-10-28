package servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import forms.BuyRequestForm;
import domain.BuyRequest;
import domain.User;
import listeners.MainListener;
import org.springframework.dao.DataAccessException;
import services.BuyRequestService;
import services.interfaces.IBuyRequestService;
import services.interfaces.IUserService;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@WebServlet({"/start-request", "/edit-request"})
public class StartRequestServlet extends HttpServlet {
    private IBuyRequestService buyRequestService;
    private IUserService userService;

    @Override
    public void init() throws ServletException {
        buyRequestService = (IBuyRequestService) getServletContext().getAttribute(MainListener.BUY_REQUEST_SERVICE_ATTRIBUTE);
        userService = (IUserService) getServletContext().getAttribute(MainListener.USER_SERVICE_ATTRIBUTE);
        if (buyRequestService == null || userService == null) {
            throw new ServletException("Сервисы запросов и пользователей не инициализированы");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = (Long) req.getSession(false).getAttribute("userId");
        if (isEditing(req)) {
            BuyRequest buyRequest = editingRequest(req, resp, userId);
            if (buyRequest == null) {
                return;
            }
            req.setAttribute("form", BuyRequestForm.from(buyRequest));
        } else {
            req.setAttribute("form", new BuyRequestForm());
        }
        renderForm(req, resp, userId);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = (Long) req.getSession(false).getAttribute("userId");
        HttpSession session = req.getSession(false);
        Object csrfToken = session.getAttribute("buyRequestCsrfToken");
        if (csrfToken == null || !csrfToken.equals(req.getParameter("csrfToken"))) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Обновите страницу и отправьте форму ещё раз.");
            return;
        }
        BuyRequest existing = null;
        if (isEditing(req)) {
            existing = editingRequest(req, resp, userId);
            if (existing == null) {
                return;
            }
        }
        BuyRequestForm form = new BuyRequestForm();
        req.setAttribute("form", form);
        try {
            readForm(req, form);
            Long requestId;
            if (existing != null) {
                requestId = existing.getId();
                if (!buyRequestService.update(requestId, userId, form)) {
                    resp.setStatus(HttpServletResponse.SC_CONFLICT);
                    req.setAttribute("error", "Запрос уже изменён или взят в закупку. Откройте запрос заново перед редактированием.");
                    renderForm(req, resp, userId);
                    return;
                }
                session.setAttribute("updatedBuyRequestId", requestId);
            } else {
                requestId = buyRequestService.create(userId, form);
                session.setAttribute("createdBuyRequestId", requestId);
            }
            resp.sendRedirect(req.getContextPath() + "/requests/" + requestId);
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            req.setAttribute("error", e.getMessage());
            renderForm(req, resp, userId);
        } catch (DataAccessException e) {
            getServletContext().log("Не удалось сохранить запрос на покупку", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            req.setAttribute("error", "Не удалось сохранить запрос. Попробуйте отправить его ещё раз.");
            renderForm(req, resp, userId);
        }
    }

    private static void readForm(HttpServletRequest req, BuyRequestForm form) {
        form.setUpdatedAt(req.getParameter("updatedAt"));
        form.setComment(req.getParameter("comment"));
        form.setValidUntil(req.getParameter("validUntil"));
        form.setRecipientIds(Arrays.asList(values(req, "recipientId")));
        String[][] columns = {
                values(req, "itemName"), values(req, "quantity"), values(req, "unitId"),
                values(req, "maxUnitPrice"), values(req, "maxTotalPrice"),
                values(req, "replacementPolicyId"), values(req, "itemComment")
        };
        int size = columns[0].length;
        if (size < 1 || size > BuyRequestService.MAX_ITEMS) {
            throw new IllegalArgumentException("Добавьте от 1 до " + BuyRequestService.MAX_ITEMS + " товаров.");
        }
        for (String[] column : columns) {
            if (column.length != size) {
                throw new IllegalArgumentException("Данные товаров переданы не полностью. Проверьте форму.");
            }
        }
        form.getItems().clear();
        for (int i = 0; i < size; i++) {
            BuyRequestForm.Item item = new BuyRequestForm.Item();
            item.setName(columns[0][i]);
            item.setQuantity(columns[1][i]);
            item.setUnitId(columns[2][i]);
            item.setMaxUnitPrice(columns[3][i]);
            item.setMaxTotalPrice(columns[4][i]);
            item.setReplacementPolicyId(columns[5][i]);
            item.setComment(columns[6][i]);
            form.getItems().add(item);
        }
    }

    private static String[] values(HttpServletRequest req, String name) {
        String[] values = req.getParameterValues(name);
        return values == null ? new String[0] : values;
    }

    private void renderForm(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session.getAttribute("buyRequestCsrfToken") == null) {
            session.setAttribute("buyRequestCsrfToken", UUID.randomUUID().toString());
        }
        List<User> recipients = new ArrayList<>(userService.getFriends(userId));
        BuyRequest existing = (BuyRequest) req.getAttribute("buyRequest");
        if (existing != null) {
            for (var recipient : existing.getRecipients()) {
                if (recipients.stream().noneMatch(user -> user.getId().equals(recipient.getId()))) {
                    recipients.add(new User(recipient.getId(), recipient.getName(), null, null, null));
                }
            }
        }
        req.setAttribute("friendList", recipients);
        req.setAttribute("units", buyRequestService.getUnits());
        req.setAttribute("replacementPolicies", buyRequestService.getReplacementPolicies());
        req.getRequestDispatcher("/WEB-INF/requests/start-request.jsp").forward(req, resp);
    }

    private static boolean isEditing(HttpServletRequest req) {
        return "/edit-request".equals(req.getServletPath());
    }

    private BuyRequest editingRequest(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws IOException {
        Long requestId;
        try {
            String id = req.getParameter("id");
            if (id == null || !id.matches("[1-9][0-9]*")) {
                throw new NumberFormatException();
            }
            requestId = Long.valueOf(id);
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        BuyRequest buyRequest = buyRequestService.findByIdForUser(requestId, userId);
        if (buyRequest == null || !userId.equals(buyRequest.getUserId())) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!buyRequest.isEditable()) {
            resp.sendError(HttpServletResponse.SC_CONFLICT, "Редактировать можно только запрос, который ещё не взят в закупку.");
            return null;
        }
        req.setAttribute("editing", true);
        req.setAttribute("requestId", requestId);
        req.setAttribute("buyRequest", buyRequest);
        return buyRequest;
    }

}
