<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ru">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Регистрация</title>
        <link rel="stylesheet" href="<c:url value='/styles.css'/>">
    <script src="<c:url value='/scripts/theme.js'/>"></script></head>
    <body>
        <%@ include file="/WEB-INF/fragments/header.jspf" %>
        <main>
            <%@ include file="/WEB-INF/error-notice.jspf" %>
        </main>
        <form action="<c:url value="/login"/>" method="post">
            <div class="container-column-center">
                <h1>Вход</h1>
                <div class="container-column">
                    <p>
                        <label for="login">Логин или почта</label>
                        <input type="text" name="login" id="login" minlength="2"
                               maxlength="254" value="${login}" class="input-line" required>
                    </p>
                    <p>
                        <label for="password">Пароль</label>
                        <input type="password" name="password" id="password"
                               minlength="8" maxlength="50" class="input-line" required>
                    </p>
                </div>
                <button type="submit" class="button-link">Войти</button>
            </div>
        </form>
    </body>
</html>
