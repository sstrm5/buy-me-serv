package servlets;

import dao.enums.FriendRequestStatus;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import listeners.MainListener;
import org.springframework.dao.DataIntegrityViolationException;
import services.interfaces.IRequisiteService;
import services.interfaces.IUserService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

@WebServlet("/profile/*")
public class ProfileServlet extends HttpServlet {
    private static final String CSRF = "profileCsrf";
    private final Pattern PROFILE_ID_PATTERN = Pattern.compile("/id[0-9]{1,19}");
    private IRequisiteService requisiteService;
    private IUserService userService;

    private static String parameter(HttpServletRequest req, String name) {
        String value = req.getParameter(name);
        return value == null ? "" : value.strip();
    }

    private static Long getRequestedId(String pathInfo) {
        return Long.valueOf(pathInfo.substring(3));
    }

    @Override
    public void init() throws ServletException {
        requisiteService = (IRequisiteService) getServletContext().getAttribute(MainListener.REQUISITE_SERVICE_ATTRIBUTE);
        if (requisiteService == null) {
            throw new ServletException("Сервис реквизитов не инициализирован");
        }

        userService = (IUserService) getServletContext().getAttribute(MainListener.USER_SERVICE_ATTRIBUTE);
        if (userService == null) {
            throw new ServletException("Сервис пользователей не инициализирован");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        Long userId = (Long) session.getAttribute("userId");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || !PROFILE_ID_PATTERN.matcher(pathInfo).matches()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Long requestedUserId;
        try {
            requestedUserId = getRequestedId(pathInfo);
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean areFriends = userService.areUsersFriendsById(userId, requestedUserId);
        String friendshipRequestStatusString = null;
        if (!areFriends) {
            FriendRequestStatus userRequestStatus = userService.getFriendRequestStatusById(userId, requestedUserId);
            FriendRequestStatus friendRequestStatus = userService.getFriendRequestStatusById(requestedUserId, userId);
            boolean bothNotNull = userRequestStatus != null && friendRequestStatus != null;
            if (bothNotNull && userRequestStatus.equals(FriendRequestStatus.PENDING) && friendRequestStatus.equals(FriendRequestStatus.PENDING)) {
                throw new ServletException("Невозможен случай одновременного ожидания принятия в друзья");
            }
            if (userRequestStatus != null && userRequestStatus.equals(FriendRequestStatus.PENDING)) {
                friendshipRequestStatusString = "pendingUser";
            }
            if (friendshipRequestStatusString == null && friendRequestStatus != null && friendRequestStatus.equals(FriendRequestStatus.PENDING)) {
                friendshipRequestStatusString = "pendingRequestedUser";
            }
        } else {
            friendshipRequestStatusString = "friends";
        }
        req.setAttribute("friendshipRequestStatusString", friendshipRequestStatusString);

        if (session != null) {
            synchronized (session) {
                if (session.getAttribute(CSRF) == null) {
                    session.setAttribute(CSRF, UUID.randomUUID().toString());
                }
            }
        }


        resp.setHeader("Cache-Control", "no-store");
        req.setAttribute("requestedUser", userService.findById(requestedUserId));
        req.setAttribute("user", userService.findById(userId));
        req.setAttribute("requisites", requisiteService.getRequisiteList(requestedUserId, false));
        req.getRequestDispatcher("/WEB-INF/profile/profile.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        Long userId = (Long) session.getAttribute("userId");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || !PROFILE_ID_PATTERN.matcher(pathInfo).matches()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        req.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (!(session.getAttribute(CSRF) instanceof String token) || !token.equals(req.getParameter("csrf"))) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Long requestedUserId;
        try {
            requestedUserId = getRequestedId(pathInfo);
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            String action = parameter(req, "action");
            if (action.startsWith("friend-")) {
                if (userService.changeFriendship(userId, requestedUserId, action)) {
                    String query = req.getQueryString();
                    resp.sendRedirect(req.getContextPath() + "/profile/id" + requestedUserId
                            + (query == null ? "" : "?" + query));
                    return;
                }
                req.setAttribute("error", "Заявка уже обработана или действие недоступно. Обновите страницу.");
                doGet(req, resp);
                return;
            }
            boolean saved = switch (action) {
                case "add", "edit" -> saveRequisite(req, requestedUserId, userId, action);
                case "delete" -> requisiteService.delete(requestedUserId, userId, Long.parseLong(parameter(req, "id")));
                case "default" ->
                        requisiteService.setDefault(requestedUserId, userId, Long.parseLong(parameter(req, "id")));
                default -> throw new IllegalArgumentException("Неизвестное действие.");
            };
            if (saved) {
                resp.sendRedirect(req.getContextPath() + "/profile/id" + userId);
                return;
            }
            req.setAttribute("error", "Реквизиты не найдены или уже используются в походе.");
        } catch (NumberFormatException e) {
            req.setAttribute("error", "Некорректный номер записи.");
        } catch (IllegalArgumentException e) {
            req.setAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            req.setAttribute("error", "Не удалось сохранить изменение. Проверьте, используются ли реквизиты в походе.");
        }
        doGet(req, resp);
    }

    private boolean saveRequisite(HttpServletRequest req, Long requestedUserId, Long userId, String action) {
        String bank = parameter(req, "bank");
        String phone = parameter(req, "phone");
        if (bank.isBlank() || bank.length() > 255) {
            throw new IllegalArgumentException("Введите название банка длиной до 255 символов.");
        }
        if (phone.length() > 32 || !phone.matches("\\+?[0-9 ()-]+") || !phone.replaceAll("\\D", "").matches("[0-9]{10,15}")) {
            throw new IllegalArgumentException("Введите телефон с 10–15 цифрами.");
        }

        if ("edit".equals(action)) {
            return requisiteService.update(requestedUserId, userId, Long.parseLong(parameter(req, "id")), bank, phone);
        }
        return requisiteService.create(requestedUserId, userId, bank, phone, false) != null;
    }
}
