package servlets;

import domain.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import listeners.MainListener;
import services.interfaces.IAuthService;

import java.io.IOException;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private IAuthService authService;

    @Override
    public void init() throws ServletException {
        authService = (IAuthService) getServletContext().getAttribute(MainListener.AUTH_SERVICE_ATTRIBUTE);
        if (authService == null) {
            throw new ServletException("Сервис авторизации не инициализирован");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String loginOrEmail = req.getParameter("login");
        String password = req.getParameter("password");

        User user = authService.authenticate(loginOrEmail, password);

        HttpSession session = req.getSession();
        req.changeSessionId();
        session.setAttribute("userId", user.getId());
        session.setAttribute("login", user.getLogin());
        session.setAttribute("userAvatarUrl", user.getUserAvatarUrl());
        session.setAttribute("email", user.getEmail());
        resp.sendRedirect(req.getContextPath());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("WEB-INF/auth/login.jsp").forward(req, resp);
    }
}
