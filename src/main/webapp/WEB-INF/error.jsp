<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="statusCode" value="${requestScope['jakarta.servlet.error.status_code']}"/>
<!DOCTYPE html>
<html lang="ru">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Ошибка <c:out value="${statusCode}"/></title>
        <link rel="stylesheet" href="<c:url value='/styles.css'/>">
    <script src="<c:url value='/scripts/theme.js'/>"></script></head>
    <body>
        <%@ include file="/WEB-INF/fragments/header.jspf" %>
        <main class="margin main-container">
            <h1>Ошибка <c:out value="${statusCode}"/></h1>
            <a class="button-link" href="<c:url value='/'/>">На главную</a>
        </main>
    </body>
</html>
