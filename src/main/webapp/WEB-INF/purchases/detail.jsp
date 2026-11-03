<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="ru_RU"/>
<!DOCTYPE html><html lang="ru"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>Закупки — Купи-мне!</title><link rel="stylesheet" href="<c:url value='/styles.css'/>"></head><body>
<%@ include file="/WEB-INF/fragments/header.jspf" %>
<main class="requests-container request-detail purchase-page">
<%@ include file="/WEB-INF/error-notice.jspf" %>

<a class="button-link-plain" href="<c:url value='/purchases'/>">← Мои походы</a>
<section class="request-card"><div class="request-heading"><h2>Поход №${purchase.id}</h2><span class="requisite-badge status-badge" data-status="${purchase.code}"><c:out value="${purchase.name}"/></span></div>
<p>Реквизиты: <c:out value="${purchase.bank}"/> · <c:out value="${purchase.phone_number}"/></p>
<c:if test="${purchase.has_receipt}"><a class="text-link" href="<c:url value='/purchases/${purchase.id}/receipt'/>">Посмотреть чек</a></c:if></section>
<c:if test="${owner and purchase.code eq 'PLANNED'}">
<section class="request-card"><h3>Доступные запросы</h3>
<p class="request-hint">Действующие запросы, в которых вас выбрали покупателем, сгруппированы по отправителям.</p>
<c:if test="${empty availableGroups}"><p>Доступных запросов пока нет. Все подходящие запросы уже добавлены в этот поход или ещё не отправлены вам.</p></c:if>
<c:forEach items="${availableGroups}" var="group">
<div class="purchase-sender-group"><h4><c:out value="${group.key}"/></h4>
<c:forEach items="${group.value}" var="r"><form method="post" class="purchase-choice">
<input type="hidden" name="csrfToken" value="${sessionScope.purchaseCsrf}"><input type="hidden" name="action" value="add"><input type="hidden" name="requestId" value="${r.id}">
<a class="text-link" href="<c:url value='/requests/${r.id}'/>">Запрос №${r.id}</a>
<span class="purchase-choice-comment"><c:out value="${r.comment}"/></span><button class="button-link">Добавить</button>
</form></c:forEach></div>
</c:forEach></section>
</c:if>
<c:if test="${empty purchase.requests}"><p class="request-card">В походе пока нет запросов. Запросы, сборку которых начал другой покупатель, удаляются автоматически.</p></c:if>
<c:forEach items="${purchase.requests}" var="r">
<section class="request-card purchase-request ${empty r.comment ? 'purchase-request-without-comment' : ''}"><div class="request-heading"><h3>Запрос №${r.id} · <c:out value="${r.login}"/></h3><span class="requisite-badge status-badge" data-status="${r.code}"><c:out value="${r.name}"/></span></div>
<c:if test="${not empty r.comment}"><p><c:out value="${r.comment}"/></p></c:if>
<c:if test="${owner and purchase.code eq 'PLANNED'}"><form method="post"><input type="hidden" name="csrfToken" value="${sessionScope.purchaseCsrf}"><input type="hidden" name="action" value="remove"><input type="hidden" name="requestId" value="${r.id}"><button type="submit" class="text-link purchase-remove">Убрать из закупки</button></form></c:if>
<c:forEach items="${r.items}" var="i"><article class="purchase-item"><h3><c:out value="${i.item_name}"/> · <fmt:formatNumber value="${i.unit_name eq 'шт.' or i.unit_name eq 'шт' ? i.requested_quantity - i.requested_quantity % 1 : i.requested_quantity}" maxFractionDigits="3"/> <c:out value="${i.unit_name}"/></h3>
<p><c:out value="${i.comment}"/></p><p>Замена: <c:out value="${i.replacement_name}"/></p>
<c:if test="${i.max_unit_price ne null}"><p>Цена за единицу до <fmt:formatNumber value="${i.max_unit_price}" maxFractionDigits="0"/> ₽</p></c:if><c:if test="${i.max_total_price ne null}"><p>Бюджет до <fmt:formatNumber value="${i.max_total_price}" maxFractionDigits="0"/> ₽</p></c:if>
<c:choose><c:when test="${owner and purchase.code eq 'COLLECTING'}">
<form method="post" class="purchase-form purchase-item-form"><input type="hidden" name="csrfToken" value="${sessionScope.purchaseCsrf}"><input type="hidden" name="action" value="item"><input type="hidden" name="itemId" value="${i.id}">
<label>Результат<select name="status">
<option value="PENDING" ${i.code eq 'PENDING' ? 'selected' : ''}>Не обработан</option>
<option value="BOUGHT" ${i.code eq 'BOUGHT' ? 'selected' : ''}>Куплено как просили</option>
<option value="QUANTITY_CHANGED" ${i.code eq 'QUANTITY_CHANGED' ? 'selected' : ''}>Куплено другое количество</option>
<option value="REPLACEMENT" ${i.code eq 'REPLACEMENT' ? 'selected' : ''}>Предложена замена</option>
<option value="NOT_FOUND" ${i.code eq 'NOT_FOUND' ? 'selected' : ''}>Товар не найден</option>
<option value="NOT_BOUGHT" ${i.code eq 'NOT_BOUGHT' ? 'selected' : ''}>Не куплен</option></select></label>
<label>Фактическое количество<input name="quantity" type="number" min="0.001" max="999999999" step="0.001" value="${i.actual_quantity ne null ? i.actual_quantity : i.requested_quantity}"></label>
<label>Стоимость всего количества, ₽<input name="price" type="number" min="0" max="999999999" step="0.01" value="${i.actual_price}"></label>
<label>Название замены<input name="actualName" maxlength="255" value="<c:out value='${i.actual_name}'/>"></label></form>
</c:when><c:otherwise><p>Статус: <c:out value="${i.status_name}"/></p><c:if test="${i.actual_price ne null}"><p>Фактически: <fmt:formatNumber value="${i.unit_name eq 'шт.' or i.unit_name eq 'шт' ? i.actual_quantity - i.actual_quantity % 1 : i.actual_quantity}" maxFractionDigits="3"/> <c:out value="${i.unit_name}"/> · <span class="purchase-item-price"><fmt:formatNumber value="${i.actual_price}" maxFractionDigits="0"/> ₽</span></p><c:if test="${not empty i.actual_name}"><p>Замена: <c:out value="${i.actual_name}"/></p></c:if></c:if></c:otherwise></c:choose>
</article></c:forEach>
<c:if test="${purchase.code eq 'COMPLETED'}"><div class="purchase-total"><span>Итого к оплате</span><strong><fmt:formatNumber value="${r.total_amount_due}" minFractionDigits="0" maxFractionDigits="0"/> ₽</strong></div></c:if>

<c:if test="${r.user_id eq sessionScope.userId and r.code eq 'AWAITING_DECISION'}">
<p>Часть товаров куплена с изменениями или не куплена. Можно завершить запрос или повторить такие позиции; откроется черновик для редактирования. Он станет доступен покупателям после сохранения.</p>
<form method="post"><input type="hidden" name="csrfToken" value="${sessionScope.purchaseCsrf}"><input type="hidden" name="requestId" value="${r.id}"><button class="button-link" name="action" value="decide">Завершить запрос</button> <button class="button-link" name="action" value="repeat">Повторить с изменениями</button></form></c:if>
</section></c:forEach>
<c:if test="${owner and purchase.code eq 'PLANNED'}"><form method="post"><input type="hidden" name="csrfToken" value="${sessionScope.purchaseCsrf}"><button class="button-link" name="action" value="start" ${empty purchase.requests ? 'disabled' : ''}>Начать сборку всех запросов</button></form></c:if>
<c:if test="${owner and purchase.code eq 'COLLECTING'}"><form method="post" enctype="multipart/form-data" id="purchase-finish" class="request-card purchase-form"><input type="hidden" name="csrfToken" value="${sessionScope.purchaseCsrf}"><input type="hidden" name="action" value="finish"><label>Фото чека (необязательно, JPEG или PNG до 5 МБ)<input type="file" name="receipt" accept="image/jpeg,image/png"></label><p class="request-hint">Изменения сохраняются в этом браузере автоматически. При завершении закупки результаты отправятся заказчикам.</p><button class="button-link">Завершить поход</button></form></c:if>
</main>
<div id="purchase-draft" data-user="${sessionScope.userId}" data-purchase="${purchase.id}" data-state="${purchase.code}"></div>
<script src="<c:url value='/scripts/purchase-draft.js'/>" defer></script>
</body></html>
