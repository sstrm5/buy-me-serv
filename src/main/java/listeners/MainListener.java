package listeners;

import dao.RequisiteDao;
import dao.BuyRequestDao;
import dao.UserDao;
import dao.interfaces.IRequisiteDao;
import dao.interfaces.IUserDao;
import infrastructure.db.postgres.DatabaseConfig;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.loader.WebappClassLoaderBase;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.springframework.jdbc.core.JdbcTemplate;
import services.AuthService;
import services.BuyRequestService;
import services.RequisiteService;
import services.interfaces.IAuthService;
import services.interfaces.IRequisiteService;
import services.interfaces.IUserService;
import services.UserService;

import java.lang.reflect.Field;

@WebListener
public class MainListener implements ServletContextListener {
    public static final String AUTH_SERVICE_ATTRIBUTE = "authService";
    public static final String REQUISITE_SERVICE_ATTRIBUTE = "requisiteService";
    public static final String USER_SERVICE_ATTRIBUTE = "userService";
    public static final String BUY_REQUEST_SERVICE_ATTRIBUTE = "buyRequestService";
    private static final boolean SHOW_STYLED_ERROR_PAGES = false;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        if (SHOW_STYLED_ERROR_PAGES) {
            registerStyledErrorPage(sce.getServletContext().getClassLoader());
        }

        JdbcTemplate jdbcTemplate = DatabaseConfig.createJdbcTemplate(sce.getServletContext().getRealPath("/"));
        DatabaseConfig.initializeSchema(jdbcTemplate);

        IUserDao userDao = new UserDao(jdbcTemplate);
        IRequisiteDao requisiteDao = new RequisiteDao(jdbcTemplate);

        IAuthService authService = new AuthService(userDao);
        IRequisiteService requisiteService = new RequisiteService(requisiteDao);
        IUserService userService = new UserService(userDao);

        
        sce.getServletContext().setAttribute("purchaseDao", new dao.PurchaseDao(jdbcTemplate));
        sce.getServletContext().setAttribute(AUTH_SERVICE_ATTRIBUTE, authService);
        sce.getServletContext().setAttribute(REQUISITE_SERVICE_ATTRIBUTE, requisiteService);
        sce.getServletContext().setAttribute(USER_SERVICE_ATTRIBUTE, userService);
        sce.getServletContext().setAttribute(BUY_REQUEST_SERVICE_ATTRIBUTE,
                new BuyRequestService(new BuyRequestDao(jdbcTemplate)));
    }

    private void registerStyledErrorPage(ClassLoader classLoader) {
        try {
            Field resourcesField = WebappClassLoaderBase.class.getDeclaredField("resources");
            resourcesField.setAccessible(true);
            WebResourceRoot resources = (WebResourceRoot) resourcesField.get(classLoader);

            ErrorPage errorPage = new ErrorPage();
            errorPage.setLocation("/WEB-INF/error.jsp");
            resources.getContext().addErrorPage(errorPage);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось зарегистрировать страницу ошибок Tomcat", e);
        }
    }
}
