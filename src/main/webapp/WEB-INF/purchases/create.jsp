<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="ru_RU"/>
<!DOCTYPE html><html lang="ru"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>Закупки — Купи-мне!</title><link rel="stylesheet" href="<c:url value='/styles.css'/>"><script src="<c:url value='/scripts/theme.js'/>"></script></head><body>
<%@ include file="/WEB-INF/fragments/header.jspf" %>
<main class="requests-container request-detail purchase-page">
<c:if test="${not empty error}"><div class="request-card" role="alert"><c:out value="${error}"/></div></c:if>

<a class="button-link-plain" href="<c:url value='/purchases'/>">← Мои походы</a>
<h2>Начать закупку</h2><hr class="divider">
<form method="post" class="request-card purchase-form">
<h3>Реквизиты для перевода</h3>
<input type="hidden" name="csrfToken" value="${sessionScope.purchaseCsrf}">
<label>Реквизиты из профиля
<select name="requisiteId" id="purchase-requisite" required>
<c:if test="${empty profileRequisites}"><option value="">Нет сохранённых реквизитов</option></c:if>
<c:forEach items="${profileRequisites}" var="r">
<option value="${r.id}" ${(param.requisiteId ne null and param.requisiteId eq r.id) or (param.requisiteId eq null and r.is_default) ? 'selected' : ''}><c:out value="${r.bank}"/> · <c:out value="${r.phone_number}"/>${r.is_default ? ' — по умолчанию' : ''}</option>
</c:forEach>
</select></label>
<label class="purchase-temporary"><input type="checkbox" name="temporary" id="purchase-temporary" ${param.temporary eq 'on' ? 'checked' : ''}> Временные реквизиты — только для этой закупки</label>
<fieldset id="temporary-requisites" class="purchase-form" hidden disabled>
<legend>Временные реквизиты</legend>
<p class="request-hint">Эти реквизиты не добавляются в профиль.</p>
<label>Банк<input name="bank" maxlength="255" required value="<c:out value='${param.bank ne null ? param.bank : defaults.bank}'/>"></label>
<label>Телефон<input name="phone" type="tel" maxlength="32" required value="<c:out value='${param.phone ne null ? param.phone : defaults.phone_number}'/>"></label>
</fieldset>
<c:if test="${empty profileRequisites}"><p class="request-hint">Добавьте реквизиты в профиль или отметьте галочку и укажите временные.</p></c:if>
<button type="submit" class="button-link">Создать поход</button>
</form>
<script>
const temporary = document.getElementById('purchase-temporary');
const fields = document.getElementById('temporary-requisites');
const saved = document.getElementById('purchase-requisite');
function updateRequisites() {
    fields.hidden = !temporary.checked;
    fields.disabled = !temporary.checked;
    saved.disabled = temporary.checked;
}
temporary.addEventListener('change', updateRequisites);
updateRequisites();
</script></main></body></html>
