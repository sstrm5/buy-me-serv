<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ru">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Запросы — Купи-мне!</title>
        <link rel="stylesheet" href="<c:url value='/styles.css'/>">
    <script src="<c:url value='/scripts/theme.js'/>"></script></head>
    <body>
        <%@ include file="/WEB-INF/fragments/header.jspf" %>
        <main>
            <div class="main-container">
                <a href="<c:url value='/'/>" class="button-link-plain"><span aria-hidden="true">←</span> На главную</a>
            </div>
            <div class="requests-container">
            <div class="request-heading">
                <h2>Запросы на покупку</h2>
                <a href="<c:url value='/start-request'/>" class="text-link">Сделать запрос</a>
            </div>
            <hr class="divider">
            <nav class="friends-nav" aria-label="Разделы запросов">
                <a href="<c:url value='/requests'/>" class="friends-nav-link"
                ${not incoming ? 'aria-current="page"' : ''}>Отправленные</a>
                <a href="<c:url value='/requests?section=incoming'/>" class="friends-nav-link"
                ${incoming ? 'aria-current="page"' : ''}>Полученные</a>
            </nav>
            <div class="requests-list">
            <c:if test="${empty buyRequests}">
                <p">${incoming ? 'Вам пока не отправляли запросов.' : 'Вы ещё не отправили ни одного запроса.'}</p>
            </c:if>
            <c:forEach items="${buyRequests}" var="buyRequest">
                <a href="<c:url value='/requests/${buyRequest.id}'/>" class="button-link-plain request">
                    <article class="request-card">
                        <div class="request-heading">
                            <h3>Запрос №${buyRequest.id}</h3>
                            <span class="requisite-badge status-badge" data-status="${buyRequest.expired and buyRequest.statusCode eq 'PENDING' ? 'EXPIRED' : buyRequest.statusCode}"><c:out
                                    value="${buyRequest.expired and buyRequest.statusCode eq 'PENDING' ? 'Срок истёк' : buyRequest.statusName}"/></span>
                        </div>
                        <c:if test="${incoming}"><p>От: <c:out value="${buyRequest.authorLogin}"/></p></c:if>
                        <p>Актуален до: <c:out value="${buyRequest.validUntilLabel}"/> МСК</p>
                        <p class="request-hint">Последнее обновление:
                            <time datetime="${buyRequest.updatedAt}"><c:out value="${buyRequest.updatedAtLabel}"/> МСК
                            </time>
                        </p>
                        <c:if test="${buyRequest.statusCode eq 'COMPLETED' or buyRequest.statusCode eq 'AWAITING_DECISION'}">
                            <p><span class="requisite-badge status-badge" data-status="${buyRequest.paidBack or buyRequest.totalAmountDue le 0 ? 'PAID' : 'UNPAID'}">${buyRequest.totalAmountDue le 0 ? 'Возврат денег не требуется' : (buyRequest.paidBack ? 'Деньги возвращены' : 'Деньги не возвращены')}</span></p>
                        </c:if>
                        <c:if test="${not empty buyRequest.comment}"><p class="request-comment"><c:out
                                value="${buyRequest.comment}"/></p></c:if>
                    </article>
                </a>
            </c:forEach>
            </div>
            <nav class="friends-pagination" aria-label="Страницы запросов">
                <div class="friends-pagination-slot">
                    <c:if test="${page gt 1}">
                        <c:url var="previousPageUrl" value="/requests">
                            <c:param name="section" value="${incoming ? 'incoming' : 'sent'}"/>
                            <c:param name="page" value="${page - 1}"/>
                        </c:url>
                        <a href="${previousPageUrl}" class="text-link" aria-label="Предыдущая страница">←</a>
                    </c:if>
                </div>
                <span class="friends-pagination-current" aria-current="page"><c:out value="${page}"/></span>
                <div class="friends-pagination-slot">
                    <c:if test="${hasNextPage}">
                        <c:url var="nextPageUrl" value="/requests">
                            <c:param name="section" value="${incoming ? 'incoming' : 'sent'}"/>
                            <c:param name="page" value="${page + 1}"/>
                        </c:url>
                        <a href="${nextPageUrl}" class="text-link" aria-label="Следующая страница">→</a>
                    </c:if>
                </div>
            </nav>
            </div>
        </main>
    </body>
</html>
