<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="requestTitle" value="${editing ? 'Редактирование запроса' : 'Сделать запрос'}"/>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><c:out value="${requestTitle}"/> — Купи-мне!</title>
    <link rel="stylesheet" href="<c:url value='/styles.css'/>">
    <script src="<c:url value='/scripts/buy-request.js'/>" defer></script>
</head>
<body>
<%@ include file="/WEB-INF/fragments/header.jspf" %>
<main class="requests-container">
    <c:url var="cancelUrl" value="/requests"/>
    <c:if test="${editing}"><c:url var="cancelUrl" value="/requests/${requestId}"/></c:if>
    <a href="<c:out value='${cancelUrl}'/>" class="button-link-plain">← ${editing ? 'К запросу' : 'Мои запросы'}</a>
    <h2><c:out value="${requestTitle}"/><c:if test="${editing}"> №${requestId}</c:if></h2>

    <%@ include file="/WEB-INF/error-notice.jspf" %>
    <c:choose>
        <c:when test="${empty friendList}">
            <section class="request-card">
                <p>Чтобы отправить запрос, сначала добавьте друзей.</p>
                <a href="<c:url value='/friends?section=search'/>" class="button-link">Найти друзей</a>
            </section>
        </c:when>
        <c:otherwise>
            <form action="<c:url value="${editing ? '/edit-request' : '/start-request'}"/>" method="post" id="buy-request-form">
                <input type="hidden" name="csrfToken" value="<c:out value='${sessionScope.buyRequestCsrfToken}'/>">
                <c:if test="${editing}">
                    <input type="hidden" name="id" value="${requestId}">
                    <input type="hidden" name="updatedAt" value="<c:out value='${form.updatedAt}'/>">
                    <p class="request-hint">Последнее обновление:
                        <time datetime="${buyRequest.updatedAt}"><c:out value="${buyRequest.updatedAtLabel}"/> МСК</time>
                    </p>
                </c:if>
                <div class="request-card">
                    <p>Кому отправить</p>
                    <div class="request-recipients">
                        <c:forEach items="${friendList}" var="friend">
                            <label class="request-recipient">
                                <input type="checkbox" name="recipientId" value="${friend.id}"
                                    ${form.recipientIds.contains(friend.id.toString()) ? 'checked' : ''}>
                                <span><c:out value="${friend.login}"/></span>
                            </label>
                        </c:forEach>
                    </div>
                </div>
                <hr class="divider">
                <div class="request-card">
                    <p>Заполните список товаров</p>
                    <div id="request-items">
                        <c:forEach items="${form.items}" var="item" varStatus="row">
                            <section class="request-item">
                                <div class="request-heading">
                                    <h3 class="request-item-title">Товар ${row.count}</h3>
                                    <button type="button" class="button-link-plain remove-request-item">Удалить товар</button>
                                </div>
                                <div class="request-fields">
                                    <div class="request-field-wide">
                                        <label data-for="itemName" for="itemName-${row.index}">Название *</label>
                                        <input id="itemName-${row.index}" name="itemName" class="input-line"
                                               maxlength="255" placeholder="Например, молоко 3,2%" required
                                               value="<c:out value='${item.name}'/>">
                                    </div>
                                    <div>
                                        <label data-for="quantity" for="quantity-${row.index}">Количество *</label>
                                        <input id="quantity-${row.index}" name="quantity" class="input-line" type="number"
                                               min="0.1" max="999999999.9" step="0.1" required
                                               value="<c:out value='${item.quantity}'/>">
                                    </div>
                                    <div>
                                        <label data-for="unitId" for="unitId-${row.index}">Единица *</label>
                                        <select id="unitId-${row.index}" name="unitId" class="input-line" required>
                                            <c:forEach items="${units}" var="unit">
                                                <option value="${unit.id}" ${item.unitId eq unit.id.toString() ? 'selected' : ''}><c:out value="${unit.name}"/></option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div>
                                        <label data-for="maxUnitPrice" for="maxUnitPrice-${row.index}">Цена за единицу, до ₽</label>
                                        <input id="maxUnitPrice-${row.index}" name="maxUnitPrice" class="input-line" type="number"
                                               min="0" max="999999999999.99" step="0.01"
                                               value="<c:out value='${item.maxUnitPrice}'/>">
                                    </div>
                                    <div>
                                        <label data-for="maxTotalPrice" for="maxTotalPrice-${row.index}">Бюджет на товар, до ₽</label>
                                        <input id="maxTotalPrice-${row.index}" name="maxTotalPrice" class="input-line" type="number"
                                               min="0" max="999999999999" step="1"
                                               value="<c:out value='${item.maxTotalPrice}'/>">
                                    </div>
                                    <div class="request-field-wide">
                                        <label data-for="replacementPolicyId" for="replacementPolicyId-${row.index}">Если товара нет *</label>
                                        <select id="replacementPolicyId-${row.index}" name="replacementPolicyId" class="input-line" required>
                                            <c:forEach items="${replacementPolicies}" var="policy">
                                                <option value="${policy.id}" ${item.replacementPolicyId eq policy.id.toString() ? 'selected' : ''}><c:out value="${policy.name}"/></option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="request-field-wide">
                                        <label data-for="itemComment" for="itemComment-${row.index}">Пожелания к товару</label>
                                        <textarea id="itemComment-${row.index}" name="itemComment" class="input-line"
                                                  maxlength="1000" rows="2" placeholder="Бренд, вкус, размер упаковки"><c:out value="${item.comment}"/></textarea>
                                    </div>
                                </div>
                                <hr class="divider">
                            </section>
                        </c:forEach>
                    </div>
                    <button type="button" id="add-request-item" class="button-link">+ Добавить товар</button>
                    <p id="request-item-limit" class="request-hint" role="status"></p>
                </div>
                <hr class="divider">
                <div class="request-card">
                    <p>Детали запроса</p>
                    <label for="valid-until">Актуален до (по мск) *</label>
                    <input type="datetime-local" id="valid-until" name="validUntil" class="input-line" required
                           value="<c:out value='${form.validUntil}'/>">
                    <label for="request-comment">Комментарий для друзей</label>
                    <textarea id="request-comment" name="comment" class="input-line" rows="3" maxlength="2000"
                              placeholder="Например, буду дома после 19:00"><c:out value="${form.comment}"/></textarea>
                </div>
                <div class="request-actions">
                    <button type="submit" class="button-link">${buyRequest.statusCode eq 'DRAFT' ? 'Опубликовать запрос' : (editing ? 'Сохранить изменения' : 'Отправить запрос')}</button>
                    <a href="<c:out value='${cancelUrl}'/>" class="button-link-plain">Отмена</a>
                </div>
            </form>
        </c:otherwise>
    </c:choose>
</main>
</body>
</html>
