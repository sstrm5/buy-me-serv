package servlets;

import domain.BuyRequest;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import listeners.MainListener;
import services.interfaces.IBuyRequestService;

import java.io.IOException;
import java.util.List;

@WebServlet({"/requests", "/requests/*"})
public class BuyRequestsServlet extends HttpServlet {
    private IBuyRequestService buyRequestService;
    private static final int PAGE_SIZE = 6;

    private static int parsePage(String value) {
        try {
            return value != null && value.matches("[0-9]+") ? Math.max(1, Integer.parseInt(value)) : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public void init() throws ServletException {
        buyRequestService = (IBuyRequestService) getServletContext().getAttribute(MainListener.BUY_REQUEST_SERVICE_ATTRIBUTE);
        if (buyRequestService == null) {
            throw new ServletException("Сервис запросов не инициализирован");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = (Long) req.getSession(false).getAttribute("userId");
        String path = req.getPathInfo();
        if (path == null || "/".equals(path)) {
            boolean incoming = "incoming".equals(req.getParameter("section"));
            req.setAttribute("incoming", incoming);
            int page = parsePage(req.getParameter("page"));
            List<BuyRequest> results = buyRequestService.findForUser(userId, incoming,
                    PAGE_SIZE + 1, (long) (page - 1) * PAGE_SIZE);
            req.setAttribute("page", page);
            req.setAttribute("hasNextPage", results.size() > PAGE_SIZE);
            req.setAttribute("buyRequests", results.subList(0, Math.min(PAGE_SIZE, results.size())));
            req.getRequestDispatcher("/WEB-INF/requests/requests.jsp").forward(req, resp);
            return;
        }
        Long requestId;
        try {
            if (!path.matches("/[1-9][0-9]*")) {
                throw new NumberFormatException();
            }
            requestId = Long.valueOf(path.substring(1));
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        BuyRequest buyRequest = buyRequestService.findByIdForUser(requestId, userId);
        if (buyRequest == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (req.getSession(false).getAttribute("purchaseCsrf") == null)
            req.getSession(false).setAttribute("purchaseCsrf", java.util.UUID.randomUUID().toString());
        req.setAttribute("buyRequest", buyRequest);
        dao.PurchaseDao purchases = (dao.PurchaseDao)getServletContext().getAttribute("purchaseDao");
        if (purchases != null) req.setAttribute("requestPurchases", purchases.requestPurchases(requestId, userId));
        if (requestId.equals(req.getSession(false).getAttribute("createdBuyRequestId"))) {
            req.setAttribute("requestCreated", true);
            req.getSession(false).removeAttribute("createdBuyRequestId");
        }
        if (requestId.equals(req.getSession(false).getAttribute("updatedBuyRequestId"))) {
            req.setAttribute("requestUpdated", true);
            req.getSession(false).removeAttribute("updatedBuyRequestId");
        }
        req.getRequestDispatcher("/WEB-INF/requests/request.jsp").forward(req, resp);
    }
}
