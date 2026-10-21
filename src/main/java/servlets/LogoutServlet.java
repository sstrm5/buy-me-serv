package servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;

@WebServlet("/profile/logout")
public class LogoutServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String sessionId = req.getRequestedSessionId();
        if (sessionId != null && req.isRequestedSessionIdFromCookie()) {
            Cookie cookie = new Cookie("JSESSIONID", sessionId);
            String contextPath = req.getContextPath();
            cookie.setPath(contextPath.isEmpty() ? "/" : contextPath);
            cookie.setHttpOnly(true);
            cookie.setSecure(req.isSecure());
            cookie.setMaxAge(1);
            resp.addCookie(cookie);
        }

        HttpSession session = req.getSession(false);
        if (session != null) {
            session.removeAttribute("userId");
        }

        resp.sendRedirect(req.getContextPath() + "/");
    }
}
