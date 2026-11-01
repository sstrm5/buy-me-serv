<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="ru_RU"/>
<!DOCTYPE html><html lang="ru"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>Закупки — Купи-мне!</title><link rel="stylesheet" href="<c:url value='/styles.css'/>"></head><body>
<%@ include file="/WEB-INF/fragments/header.jspf" %>
<main class="requests-container purchase-page">
<c:if test="${not empty error}"><div role="alert"><c:out value="${error}"/></div></c:if>

<div class="request-heading"><h2>Мои походы в магазин</h2><a class="text-link" href="<c:url value='/start-purchase'/>">Начать закупку</a></div><hr class="divider">
<nav class="friends-nav"><a class="friends-nav-link" href="<c:url value='/purchases'/>" ${not history ? 'aria-current="page"' : ''}>Активные</a><a class="friends-nav-link" href="<c:url value='/purchases?section=history'/>" ${history ? 'aria-current="page"' : ''}>История</a></nav>
<c:if test="${empty purchases}"><p>Здесь пока нет походов</p></c:if>
<div class="requests-list purchases-list">
<c:forEach items="${purchases}" var="p"><a class="button-link-plain request" href="<c:url value='/purchases/${p.id}'/>"><article class="request-card"><div class="request-heading"><h3>Поход №${p.id}</h3><span class="requisite-badge status-badge" data-status="${p.code}"><c:out value="${p.name}"/></span></div><c:if test="${p.code eq 'COMPLETED'}"><p><span class="requisite-badge status-badge" data-status="${p.all_paid ? 'PAID' : 'UNPAID'}">${not p.payment_required ? 'Возврат денег не требуется' : (p.all_paid ? 'Все деньги возвращены' : 'Не все деньги возвращены')}</span></p></c:if></article></a></c:forEach>
</div>
</main></body></html>
