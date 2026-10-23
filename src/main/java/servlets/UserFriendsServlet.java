package servlets;

import domain.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import listeners.MainListener;
import services.interfaces.IUserService;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

@WebServlet("/friends")
public class UserFriendsServlet extends HttpServlet {
    private IUserService userService;
    private static final int DEFAULT_LIMIT = 10;
    private static final String PAGE_PATTERN = "[0-9]+";

    private static int parsePage(String page) {
        System.out.println(page);
        if (page != null && Pattern.compile(PAGE_PATTERN).matcher(page).matches()) {
            int parsed = Integer.parseInt(page);
            return Math.max(parsed, 1);
        }
        return 1;
    }


    @Override
    public void init() throws ServletException {
        userService = (IUserService) getServletContext().getAttribute(MainListener.USER_SERVICE_ATTRIBUTE);
        if (userService == null) {
            throw new ServletException("Сервис пользователей не инициализирован");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        Long userId = (Long) session.getAttribute("userId");

        String section = req.getParameter("section");
        if (!"search".equals(section) && !"requests".equals(section)) {
            section = "friends";
        }

        int page = parsePage(req.getParameter("page"));
        req.setAttribute("page", page);
        int offset = (page - 1) * DEFAULT_LIMIT;
        int searchLimit  = DEFAULT_LIMIT + 1;

        req.setAttribute("friendsSection", section);
        if ("search".equals(section)) {
            String login = req.getParameter("login");
            req.setAttribute("searchLogin", login == null ? "" : login.strip());
            List<User> searchResults = userService.searchByLogin(login, searchLimit, offset);
            List<User> visibleResults = searchResults.subList(0, Math.min(DEFAULT_LIMIT, searchResults.size()));
            boolean hasNextPage = searchResults.size() > DEFAULT_LIMIT;
            req.setAttribute("hasNextPage", hasNextPage);
            req.setAttribute("searchResults", visibleResults);
        } else if ("requests".equals(section)) {
            req.setAttribute("incomingRequests", userService.getIncomingFriendRequests(userId));
        } else {
            List<User> friendList = userService.getFriends(userId);
            req.setAttribute("friendList", friendList);
        }
        req.getRequestDispatcher("WEB-INF/friends/user-friends.jsp").forward(req, resp);
    }

}
