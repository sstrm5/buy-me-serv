package servlets;

import exceptions.ContactPhoneAlreadyInUse;
import exceptions.EmailAlreadyInUse;
import exceptions.LoginAlreadyInUse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import listeners.MainListener;
import services.interfaces.IAuthService;

import java.io.IOException;

@WebServlet("/sign-up")
public class SignUpServlet extends HttpServlet {
    private IAuthService authService;

    @Override
    public void init() throws ServletException {
        authService = (IAuthService) getServletContext()
                .getAttribute(MainListener.AUTH_SERVICE_ATTRIBUTE);
        if (authService == null) {
            throw new ServletException("Сервис авторизации не инициализирован");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/auth/sign-up.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String login = request.getParameter("login");
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        try {
            authService.register(login, email, password);
            response.sendRedirect(request.getContextPath() + "/login");
        } catch (IllegalArgumentException | LoginAlreadyInUse | EmailAlreadyInUse
                 | ContactPhoneAlreadyInUse e) {
            request.setAttribute("error", e.getMessage());
            request.setAttribute("login", login);
            request.setAttribute("email", email);
            doGet(request, response);
        }
    }
}
