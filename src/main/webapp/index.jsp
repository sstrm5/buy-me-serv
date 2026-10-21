<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ru">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Совместные покупки</title>
        <link rel="stylesheet" href="<c:url value='/styles.css'/>">
    </head>
    <body class="home-page">
        <%@ include file="/WEB-INF/fragments/header.jspf" %>
        <main>
            <div class="main-container">
                <p>Это сервис для создания ваших запросов на покупку в магазине вашим соседям по комнате</p>
                <p>Просто найдите их через внутренний поиск и начните делать заказы!</p>
                <div class="button-container">
                    <a href="<c:url value="/start-request"/>" class="button-link">Сделать запрос</a>
                    <a href="<c:url value="/start-purchase"/>" class="button-link">Начать закупку</a>
                </div>
            </div>
        </main>
    </body>
</html>
