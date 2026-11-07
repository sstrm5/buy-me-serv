<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="ru_RU"/>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Запрос №${buyRequest.id} — Купи-мне!</title>
    <link rel="stylesheet" href="<c:url value='/styles.css'/>">
<script src="<c:url value='/scripts/theme.js'/>"></script></head>
<body>
<%@ include file="/WEB-INF/fragments/header.jspf" %>
<main class="requests-container request-detail">
    <c:url var="backUrl" value="/requests">
        <c:if test="${buyRequest.userId ne sessionScope.userId}"><c:param name="section" value="incoming"/></c:if>
    </c:url>
    <a href="<c:out value='${backUrl}'/>" class="button-link-plain">← К запросам</a>
    <c:if test="${requestCreated}"><div class="request-success">
        <span role="status">Запрос отправлен!</span>
        <button type="button" class="notice-close" aria-label="Закрыть уведомление"
                onclick="this.closest('.request-success').remove()">×</button>
    </div></c:if>
    <c:if test="${requestUpdated}"><div class="request-success">
        <span role="status">Изменения сохранены.</span>
        <button type="button" class="notice-close" aria-label="Закрыть уведомление"
                onclick="this.closest('.request-success').remove()">×</button>
    </div></c:if>
    <section class="request-card">
        <div class="request-heading">
            <h2>Запрос №${buyRequest.id}</h2>
            <span class="requisite-badge status-badge" data-status="${buyRequest.expired and buyRequest.statusCode eq 'PENDING' ? 'EXPIRED' : buyRequest.statusCode}"><c:out value="${buyRequest.expired and buyRequest.statusCode eq 'PENDING' ? 'Срок истёк' : buyRequest.statusName}"/></span>
        </div>
        <p>Автор: <a href="<c:url value='/profile/id${buyRequest.userId}'/>" class="text-link"><c:out value="${buyRequest.authorLogin}"/></a></p>
        <p>Актуален до: <c:out value="${buyRequest.validUntilLabel}"/> МСК</p>
        <p>Последнее обновление:
            <time datetime="${buyRequest.updatedAt}"><c:out value="${buyRequest.updatedAtLabel}"/> МСК</time>
        </p>
        <p>Получатели:
            <c:forEach items="${buyRequest.recipients}" var="recipient" varStatus="loop">
                <c:out value="${recipient.name}"/>${not loop.last ? ', ' : ''}
            </c:forEach>
        </p>
        <c:if test="${not empty buyRequest.comment}"><p class="request-comment"><c:out value="${buyRequest.comment}"/></p></c:if>
        <c:if test="${buyRequest.userId eq sessionScope.userId and buyRequest.editable}">
            <c:url var="editUrl" value="/edit-request"><c:param name="id" value="${buyRequest.id}"/></c:url>
            <a href="<c:out value='${editUrl}'/>" class="button-link">Редактировать запрос</a>
        </c:if>
    </section>
    <c:if test="${not empty requestPurchases}">
        <section class="request-card"><h3>Походы с этим запросом</h3>
            <c:forEach items="${requestPurchases}" var="p">
                <p><a class="text-link" href="<c:url value='/purchases/${p.id}'/>">Поход №${p.id}</a> · <c:out value="${p.login}"/> · <c:out value="${p.name}"/></p>
                <c:if test="${p.code eq 'COMPLETED'}">
                    <div class="purchase-total"><span>К возврату</span><strong><fmt:formatNumber value="${p.total_amount_due}" maxFractionDigits="2"/> ₽</strong></div>
                    <p><span class="requisite-badge status-badge" data-status="${p.total_amount_due le 0 or p.is_paid_back ? 'PAID' : 'UNPAID'}">${p.total_amount_due le 0 ? 'Возврат денег не требуется' : (p.is_paid_back ? 'Деньги возвращены' : 'Ожидается возврат денег')}</span></p>
                    <c:if test="${(p.shopper_id eq sessionScope.userId or buyRequest.userId eq sessionScope.userId) and p.total_amount_due gt 0}">
                        <form method="post" action="<c:url value='/purchases/${p.id}'/>">
                            <input type="hidden" name="csrfToken" value="${sessionScope.purchaseCsrf}">
                            <input type="hidden" name="requestId" value="${buyRequest.id}">
                            <input type="hidden" name="returnToRequest" value="true">
                            <input type="hidden" name="action" value="${p.is_paid_back ? 'unpaid' : 'paid'}">
                            <label class="payment-checkbox"><input type="checkbox" ${p.is_paid_back ? 'checked' : ''} onchange="this.form.requestSubmit()"> ${buyRequest.userId eq sessionScope.userId ? 'Я вернул деньги' : 'Деньги получены от заказчика'}</label>
                        </form>
                    </c:if>
                </c:if>
            </c:forEach>
            <p class="request-hint">Результаты покупки и реквизиты доступны на странице похода.</p>
        </section>
    </c:if>
    <h2 class="request-shopping-title">Список покупок</h2>
    <c:forEach items="${buyRequest.items}" var="item">
        <article class="request-card shopping-item">
            <div class="request-heading">
                <h3><c:out value="${item.name}"/></h3>
                <span class="shopping-item-quantity">· <fmt:formatNumber value="${item.unitName eq 'шт.' or item.unitName eq 'шт' ? item.quantity - item.quantity % 1 : item.quantity}" maxFractionDigits="3"/> <c:out value="${item.unitName}"/></span>
            </div>
            <c:if test="${item.maxUnitPrice ne null}"><p>Цена за единицу: до <fmt:formatNumber value="${item.maxUnitPrice}" minFractionDigits="0" maxFractionDigits="0"/> ₽</p></c:if>
            <c:if test="${item.maxTotalPrice ne null}"><p>Бюджет на товар: до <fmt:formatNumber value="${item.maxTotalPrice}" minFractionDigits="0" maxFractionDigits="0"/> ₽</p></c:if>
            <p>Если товара нет: <c:out value="${item.replacementPolicyName}"/>.</p>
            <c:if test="${not empty item.comment}"><p class="request-comment"><c:out value="${item.comment}"/></p></c:if>
        </article>
    </c:forEach>
    <c:if test="${buyRequest.userId eq sessionScope.userId}">
        <a href="<c:url value='/start-request'/>" class="button-link">Новый запрос</a>
    </c:if>
</main>
</body>
</html>
