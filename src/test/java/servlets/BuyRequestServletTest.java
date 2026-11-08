package servlets;

import domain.BuyRequest;
import domain.LookupValue;
import domain.User;
import forms.BuyRequestForm;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import listeners.MainListener;
import org.junit.jupiter.api.Test;
import services.interfaces.IBuyRequestService;
import services.interfaces.IUserService;

import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import domain.BuyRequestItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class BuyRequestServletTest {
    @Test
    void redirectsAnonymousGetAndPostToLoginWithoutCreatingSession() throws Exception {
        Fixture f = new Fixture();
        f.authenticated = false;
        StartRequestServlet servlet = f.startServlet();
        filters.AuthenticationFilter filter = new filters.AuthenticationFilter();
        filter.doFilter(f.request, f.response, (request, response) -> fail("Anonymous GET reached servlet"));
        assertEquals("/sem/login", f.redirect);
        filter.doFilter(f.request, f.response, (request, response) -> fail("Anonymous POST reached servlet"));
        assertEquals("/sem/login", f.redirect);
        assertEquals(0, f.createCalls);
    }

    @Test
    void authenticationFilterAllowsSignedInUser() throws Exception {
        Fixture f = new Fixture();
        boolean[] invoked = {false};
        new filters.AuthenticationFilter().doFilter(f.request, f.response,
                (request, response) -> invoked[0] = true);
        assertTrue(invoked[0]);
        assertNull(f.redirect);
    }

    @Test
    void getRendersFormAndCreatesCsrfToken() throws Exception {
        Fixture f = new Fixture();
        f.sessionAttributes.remove("buyRequestCsrfToken");
        f.startServlet().doGet(f.request, f.response);
        assertEquals("/WEB-INF/requests/start-request.jsp", f.forward);
        assertNotNull(f.sessionAttributes.get("buyRequestCsrfToken"));
        assertInstanceOf(BuyRequestForm.class, f.attributes.get("form"));
        assertEquals(1, ((List<?>) f.attributes.get("friendList")).size());
    }

    @Test
    void rejectsMissingOrIncorrectCsrfTokenBeforeCreatingRequest() throws Exception {
        for (String token : List.of("", "wrong")) {
            Fixture f = new Fixture();
            f.parameters.put("csrfToken", new String[]{token});
            f.startServlet().doPost(f.request, f.response);
            assertEquals(403, f.status);
            assertEquals(0, f.createCalls);
        }
    }

    @Test
    void savesMultipleItemsAndRedirectsToDetailSoRefreshDoesNotResubmit() throws Exception {
        Fixture f = new Fixture();
        f.parameters.put("itemName", new String[]{"Молоко", "Хлеб"});
        f.parameters.put("quantity", new String[]{"1.5", "2"});
        for (String name : List.of("unitId", "maxUnitPrice", "maxTotalPrice", "replacementPolicyId", "itemComment")) {
            f.parameters.put(name, new String[]{"10", "20"});
        }
        f.startServlet().doPost(f.request, f.response);
        assertEquals("/sem/requests/42", f.redirect);
        assertEquals(1, f.createCalls);
        assertEquals(42L, f.sessionAttributes.get("createdBuyRequestId"));
        assertEquals(2, f.savedForm.getItems().size());
        assertEquals("Хлеб", f.savedForm.getItems().get(1).getName());
        assertEquals("1.5", f.savedForm.getItems().getFirst().getQuantity());
    }

    @Test
    void rejectsMismatchedItemColumnsWithoutPersistence() throws Exception {
        Fixture f = new Fixture();
        f.parameters.put("quantity", new String[0]);
        f.startServlet().doPost(f.request, f.response);
        assertEquals(400, f.status);
        assertEquals(0, f.createCalls);
        assertEquals("/WEB-INF/requests/start-request.jsp", f.forward);
        assertNotNull(f.attributes.get("error"));
    }

    @Test
    void preservesUserInputAfterServiceValidationError() throws Exception {
        Fixture f = new Fixture();
        f.validationError = true;
        f.parameters.put("quantity", new String[]{"-5"});
        f.parameters.put("comment", new String[]{"У подъезда"});
        f.startServlet().doPost(f.request, f.response);
        assertEquals(400, f.status);
        assertNull(f.redirect);
        BuyRequestForm form = (BuyRequestForm) f.attributes.get("form");
        assertEquals("-5", form.getItems().getFirst().getQuantity());
        assertEquals("У подъезда", form.getComment());
        assertEquals(List.of("2"), form.getRecipientIds());
    }

    @Test
    void returns404ForMissingOrInaccessibleRequest() throws Exception {
        Fixture f = new Fixture();
        f.pathInfo = "/42";
        f.detail = null;
        f.requestsServlet().doGet(f.request, f.response);
        assertEquals(404, f.status);
        assertEquals(1L, f.readUserId);
        assertNull(f.forward);
    }

    @Test
    void rejectsInvalidAndOverflowingRequestPaths() throws Exception {
        for (String path : List.of("/0", "/-1", "/hello", "/42/extra", "/999999999999999999999999")) {
            Fixture f = new Fixture();
            f.pathInfo = path;
            f.requestsServlet().doGet(f.request, f.response);
            assertEquals(404, f.status);
            assertNull(f.readUserId);
        }
    }

    @Test
    void detailConsumesSuccessMessageOnce() throws Exception {
        Fixture f = new Fixture();
        f.pathInfo = "/42";
        f.sessionAttributes.put("createdBuyRequestId", 42L);
        f.requestsServlet().doGet(f.request, f.response);
        assertEquals("/WEB-INF/requests/request.jsp", f.forward);
        assertEquals(true, f.attributes.get("requestCreated"));
        assertNull(f.sessionAttributes.get("createdBuyRequestId"));
    }

    @Test
    void incomingListUsesAuthenticatedUser() throws Exception {
        Fixture f = new Fixture();
        f.parameters.put("section", new String[]{"incoming"});
        f.requestsServlet().doGet(f.request, f.response);
        assertTrue(f.incoming);
        assertEquals(1L, f.readUserId);
        assertEquals("/WEB-INF/requests/requests.jsp", f.forward);
    }

    @Test
    void authorCanOpenPrefilledEditFormIncludingFormerFriends() throws Exception {
        Fixture f = new Fixture();
        f.editing();
        f.detail.setRecipients(List.of(new LookupValue(3L, "Прежний получатель")));
        f.startServlet().doGet(f.request, f.response);
        assertEquals("/WEB-INF/requests/start-request.jsp", f.forward);
        assertEquals(true, f.attributes.get("editing"));
        BuyRequestForm form = (BuyRequestForm) f.attributes.get("form");
        assertEquals(List.of("3"), form.getRecipientIds());
        assertEquals("Молоко", form.getItems().getFirst().getName());
        assertEquals(f.detail.getUpdatedAt().toString(), form.getUpdatedAt());
        assertEquals(2, ((List<?>) f.attributes.get("friendList")).size());
    }

    @Test
    void rejectsEditingAnotherUsersRequestOnGetAndPost() throws Exception {
        Fixture f = new Fixture();
        f.editing();
        f.detail.setUserId(2L);
        StartRequestServlet servlet = f.startServlet();
        servlet.doGet(f.request, f.response);
        assertEquals(404, f.status);
        servlet.doPost(f.request, f.response);
        assertEquals(404, f.status);
        assertEquals(0, f.updateCalls);
        assertEquals(0, f.createCalls);
        assertNull(f.forward);
    }

    @Test
    void rejectsEditingWhenPurchaseHasStartedOrStatusHasChanged() throws Exception {
        Fixture f = new Fixture();
        f.editing();
        StartRequestServlet servlet = f.startServlet();
        f.detail.setAssigned(true);
        servlet.doGet(f.request, f.response);
        assertEquals(409, f.status);
        servlet.doPost(f.request, f.response);
        assertEquals(409, f.status);
        assertEquals(0, f.updateCalls);
        f.detail.setAssigned(false);
        f.detail.setStatusCode("COMPLETED");
        servlet.doPost(f.request, f.response);
        assertEquals(0, f.updateCalls);
    }

    @Test
    void updateSavesExistingRequestAndRedirectsToItsPage() throws Exception {
        Fixture f = new Fixture();
        f.editing();
        f.startServlet().doPost(f.request, f.response);
        assertEquals(1, f.updateCalls);
        assertEquals(0, f.createCalls);
        assertEquals("/sem/requests/42", f.redirect);
        assertEquals(42L, f.sessionAttributes.get("updatedBuyRequestId"));
        assertEquals(f.detail.getUpdatedAt().toString(), f.savedForm.getUpdatedAt());
    }

    @Test
    void staleEditKeepsSubmittedValuesAndReportsConflict() throws Exception {
        Fixture f = new Fixture();
        f.editing();
        f.updateAllowed = false;
        f.parameters.put("comment", new String[]{"Мой новый комментарий"});
        f.startServlet().doPost(f.request, f.response);
        assertEquals(409, f.status);
        assertNull(f.redirect);
        assertEquals("/WEB-INF/requests/start-request.jsp", f.forward);
        assertEquals("Мой новый комментарий", ((BuyRequestForm) f.attributes.get("form")).getComment());
        assertNotNull(f.attributes.get("error"));
        assertNull(f.sessionAttributes.get("updatedBuyRequestId"));
    }

    @Test
    void updateRequiresCsrfToken() throws Exception {
        Fixture f = new Fixture();
        f.editing();
        f.parameters.remove("csrfToken");
        f.startServlet().doPost(f.request, f.response);
        assertEquals(403, f.status);
        assertEquals(0, f.updateCalls);
    }

    @Test
    void detailConsumesUpdateMessageOnce() throws Exception {
        Fixture f = new Fixture();
        f.pathInfo = "/42";
        f.sessionAttributes.put("updatedBuyRequestId", 42L);
        f.requestsServlet().doGet(f.request, f.response);
        assertEquals(true, f.attributes.get("requestUpdated"));
        assertNull(f.sessionAttributes.get("updatedBuyRequestId"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (object, method, args) -> handler.apply(method.getName(), args));
    }

    private static class Fixture {
        final Map<String, Object> attributes = new HashMap<>();
        final Map<String, Object> sessionAttributes = new HashMap<>();
        final Map<String, String[]> parameters = new HashMap<>();
        boolean authenticated = true;
        boolean validationError;
        boolean incoming;
        int status = 200;
        int createCalls;
        int updateCalls;
        boolean updateAllowed = true;
        String servletPath = "/start-request";
        String redirect;
        String forward;
        String pathInfo;
        Long readUserId;
        BuyRequest detail = new BuyRequest();
        BuyRequestForm savedForm;

        final HttpSession session = proxy(HttpSession.class, (method, args) -> switch (method) {
            case "getAttribute" -> sessionAttributes.get(args[0]);
            case "setAttribute" -> sessionAttributes.put((String) args[0], args[1]);
            case "removeAttribute" -> sessionAttributes.remove(args[0]);
            default -> null;
        });
        final HttpServletRequest request = proxy(HttpServletRequest.class, (method, args) -> switch (method) {
            case "getSession" -> {
                assertEquals(false, args[0], "Endpoints must not create sessions for anonymous visitors");
                yield authenticated ? session : null;
            }
            case "getContextPath" -> "/sem";
            case "getPathInfo" -> pathInfo;
            case "getServletPath" -> servletPath;
            case "getParameter" -> {
                String[] values = parameters.get(args[0]);
                yield values == null || values.length == 0 ? null : values[0];
            }
            case "getParameterValues" -> parameters.get(args[0]);
            case "setAttribute" -> attributes.put((String) args[0], args[1]);
            case "getAttribute" -> attributes.get(args[0]);
            case "getRequestDispatcher" -> proxy(RequestDispatcher.class, (name, values) -> {
                forward = (String) args[0];
                return null;
            });
            default -> null;
        });
        final HttpServletResponse response = proxy(HttpServletResponse.class, (method, args) -> {
            if (method.equals("sendRedirect")) redirect = (String) args[0];
            if (method.equals("sendError") || method.equals("setStatus")) status = (Integer) args[0];
            return null;
        });
        final IBuyRequestService service = proxy(IBuyRequestService.class, (method, args) -> switch (method) {
            case "getUnits", "getReplacementPolicies" -> List.of(new LookupValue(10L, "шт."));
            case "create" -> {
                createCalls++;
                savedForm = (BuyRequestForm) args[1];
                if (validationError) throw new IllegalArgumentException("Проверьте количество");
                yield 42L;
            }
            case "update" -> {
                updateCalls++;
                assertEquals(42L, args[0]);
                assertEquals(1L, args[1]);
                savedForm = (BuyRequestForm) args[2];
                if (validationError) throw new IllegalArgumentException("Проверьте количество");
                yield updateAllowed;
            }
            case "findByIdForUser" -> {
                readUserId = (Long) args[1];
                yield detail;
            }
            case "findForUser" -> {
                readUserId = (Long) args[0];
                incoming = (Boolean) args[1];
                yield List.of();
            }
            default -> null;
        });
        final IUserService users = proxy(IUserService.class, (method, args) ->
                List.of(new User(2L, "Друг", "friend@example.test", null, null)));
        final ServletContext context = proxy(ServletContext.class, (method, args) -> {
            if (!method.equals("getAttribute")) return null;
            return switch ((String) args[0]) {
                case MainListener.BUY_REQUEST_SERVICE_ATTRIBUTE -> service;
                case MainListener.USER_SERVICE_ATTRIBUTE -> users;
                default -> null;
            };
        });
        final ServletConfig config = proxy(ServletConfig.class, (method, args) ->
                method.equals("getServletContext") ? context : null);

        Fixture() {
            sessionAttributes.put("userId", 1L);
            sessionAttributes.put("buyRequestCsrfToken", "token");
            parameters.put("csrfToken", new String[]{"token"});
            parameters.put("recipientId", new String[]{"2"});
            parameters.put("itemName", new String[]{"Молоко"});
            for (String name : List.of("quantity", "unitId", "replacementPolicyId", "maxUnitPrice",
                    "maxTotalPrice", "itemComment")) parameters.put(name, new String[]{"1"});
        }

        void editing() {
            servletPath = "/edit-request";
            parameters.put("id", new String[]{"42"});
            parameters.put("updatedAt", new String[]{"2026-11-07T12:00:00.123456Z"});
            detail.setId(42L);
            detail.setUserId(1L);
            detail.setStatusCode("PENDING");
            detail.setUpdatedAt(OffsetDateTime.parse("2026-11-07T12:00:00.123456Z"));
            detail.setValidUntil(OffsetDateTime.parse("2026-11-08T12:00:00Z"));
            detail.setRecipients(List.of(new LookupValue(2L, "Друг")));
            detail.setItems(List.of(new BuyRequestItem("Молоко", BigDecimal.ONE, 10L, "л",
                    null, null, 20L, "Не покупать", "")));
        }

        StartRequestServlet startServlet() throws Exception {
            StartRequestServlet servlet = new StartRequestServlet();
            servlet.init(config);
            return servlet;
        }

        BuyRequestsServlet requestsServlet() throws Exception {
            BuyRequestsServlet servlet = new BuyRequestsServlet();
            servlet.init(config);
            return servlet;
        }
    }
}
