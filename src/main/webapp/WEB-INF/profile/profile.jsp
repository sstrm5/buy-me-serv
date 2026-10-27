<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ru">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Профиль пользователя ${login}}</title>
        <link rel="stylesheet" href="<c:url value='/styles.css'/>">
    </head>
    <body>
        <%@ include file="/WEB-INF/fragments/header.jspf" %>
        <main>
            <%@ include file="/WEB-INF/error-notice.jspf" %>
            <div class="main-container profile-main">
                <a href="<c:url value='/'/>" class="button-link-plain"><span aria-hidden="true">←</span> На главную</a>
                <c:if test="${param.from eq 'search'}">
                    <c:url var="backToSearchUrl" value="/friends">
                        <c:param name="section" value="search"/>
                        <c:param name="login" value="${param.login}"/>
                        <c:param name="page" value="${param.page}"/>
                    </c:url>
                    <a href="<c:out value='${backToSearchUrl}'/>" class="button-link-plain">
                        <span aria-hidden="true">←</span> Обратно к поиску
                    </a>
                </c:if>
                <c:if test="${param.from eq 'requests'}">
                    <a href="<c:url value='/friends?section=requests'/>" class="button-link-plain">← К заявкам в друзья</a>
                </c:if>
                <c:if test="${param.from eq 'friends'}">
                    <a href="<c:url value='/friends'/>" class="button-link-plain">
                        <span aria-hidden="true">←</span> Обратно к моим друзьям
                    </a>
                </c:if>
                <div class="profile-container">
                    <img src="<c:url value="${requestedUser.userAvatarUrl}"/>" alt="Изображение пользователя"
                         class="profile-image">
                    <div class="profile-name-container">
                        <div class="profile-name-mini-container">
                            <p>Логин:</p>
                            <p class="profile-value"><c:out value="${requestedUser.login}"/></p>
                        </div>
                        <c:if test="${not empty sessionScope.userId and sessionScope.userId eq requestedUser.id}">
                            <div class="profile-name-mini-container">
                                <p>Почта:</p>
                                <p class="profile-value"><c:out value="${requestedUser.email}"/></p>
                            </div>
                        </c:if>
                    </div>
                </div>
                <c:if test="${not empty sessionScope.userId and sessionScope.userId ne requestedUser.id}">
                    <c:url var="friendActionUrl" value="/profile/id${requestedUser.id}">
                        <c:if test="${param.from eq 'search' or param.from eq 'friends' or param.from eq 'requests'}">
                            <c:param name="from" value="${param.from}"/>
                            <c:param name="login" value="${param.login}"/>
                            <c:param name="page" value="${param.page}"/>
                        </c:if>
                    </c:url>
                    <form method="post" action="<c:out value='${friendActionUrl}'/>" class="friendship-actions">
                        <input type="hidden" name="csrf" value="${sessionScope.profileCsrf}">
                        <c:choose>
                            <c:when test="${empty friendshipRequestStatusString}">
                                <button type="submit" name="action" value="friend-send" class="button-link-plain">
                                    <span aria-hidden="true">+</span> Добавить в друзья
                                </button>
                            </c:when>
                            <c:when test="${friendshipRequestStatusString eq 'friends'}">
                                <span class="friendship-status">У вас в друзьях</span>
                                <button type="submit" name="action" value="friend-remove" class="button-link-plain">Удалить из друзей</button>
                            </c:when>
                            <c:when test="${friendshipRequestStatusString eq 'pendingUser'}">
                                <span class="friendship-status">Заявка в друзья отправлена</span>
                                <button type="submit" name="action" value="friend-cancel" class="button-link-plain"
                                        aria-label="Отменить заявку в друзья" title="Отменить заявку">×</button>
                            </c:when>
                            <c:when test="${friendshipRequestStatusString eq 'pendingRequestedUser'}">
                                <span class="friendship-status">Пользователь отправил вам заявку в друзья</span>
                                <button type="submit" name="action" value="friend-accept" class="button-link">Принять</button>
                                <button type="submit" name="action" value="friend-reject" class="button-link-plain">Отклонить</button>
                            </c:when>
                        </c:choose>
                            </form>
                </c:if>
                <section class="requisites" aria-labelledby="requisites-heading">
                    <div class="requisites-heading">
                        <h2 id="requisites-heading">Куда переводить деньги</h2>
                        <c:if test="${not empty sessionScope.userId and sessionScope.userId eq requestedUser.id}">
                            <button type="button" class="button-link" id="add-requisite">+ Добавить</button>
                        </c:if>
                    </div>
                    <c:choose>
                        <c:when test="${empty requisites and not empty sessionScope.userId and sessionScope.userId eq requestedUser.id}">
                            <p class="requisites-empty">Добавьте банк и номер телефона для переводов</p>
                        </c:when>
                        <c:when test="${empty requisites and not empty sessionScope.userId and sessionScope.userId ne requestedUser.id}">
                            <p class="requisites-empty">Пользователь не указал реквизиты</p>
                        </c:when>
                        <c:otherwise>
                            <ul class="requisites-list">
                                <c:forEach items="${requisites}" var="requisite" varStatus="row">
                                    <li class="requisite-row">
                                        <div class="requisite-info">
                                            <strong><c:out value="${requisite.bank}"/></strong>
                                            <span class="requisite-phone"><c:out
                                                    value="${requisite.phone_number}"/></span>
                                            <c:if test="${row.first}">
                                                <span class="requisite-badge">По умолчанию</span>
                                            </c:if>
                                        </div>
                                        <c:if test="${sessionScope.userId eq requestedUser.id}"><details class="requisite-menu">
                                            <summary aria-label="Действия для банка <c:out value='${requisite.bank}'/>">
                                                ⋯
                                            </summary>
                                            <div class="requisite-actions">
                                                <button type="button" class="edit-requisite"
                                                        data-id="${requisite.id}"
                                                        data-bank="<c:out value='${requisite.bank}'/>"
                                                        data-phone="<c:out value='${requisite.phone_number}'/>">Изменить
                                                </button>
                                                <form method="post" action="<c:url value='/profile/id${sessionScope.userId}'/>">
                                                    <input type="hidden" name="csrf"
                                                           value="${sessionScope.profileCsrf}">
                                                    <input type="hidden" name="id" value="${requisite.id}">
                                                    <c:if test="${not row.first}">
                                                        <button type="submit" name="action" value="default">Сделать
                                                            основным
                                                        </button>
                                                    </c:if>
                                                    <button type="button" class="delete-requisite">Удалить</button>
                                                </form>
                                            </div>
                                        </details></c:if>
                                    </li>
                                </c:forEach>
                            </ul>
                        </c:otherwise>
                    </c:choose>
                </section>
                <c:if test="${not empty sessionScope.userId and sessionScope.userId eq requestedUser.id}">
                    <button type="button" class="button-link" id="open-logout-dialog">Выйти</button>
                </c:if>
            </div>
        </main>
        <dialog class="requisite-dialog" id="requisite-dialog" aria-labelledby="requisite-dialog-title">
            <form method="post" action="<c:url value='/profile/id${sessionScope.userId}'/>" id="requisite-form">
                <h2 id="requisite-dialog-title">Добавить реквизиты</h2>
                <input type="hidden" name="csrf" value="${sessionScope.profileCsrf}">
                <input type="hidden" name="action" value="add">
                <input type="hidden" name="id">
                <label for="requisite-bank">Банк</label>
                <input class="input-line" id="requisite-bank" name="bank" maxlength="255" required autofocus>
                <label for="requisite-phone">Телефон</label>
                <input class="input-line" id="requisite-phone" name="phone" type="tel" maxlength="32"
                       autocomplete="tel" placeholder="+7 900 123-45-67" required>
                <div class="requisite-dialog-actions">
                    <button type="button" class="button-link" id="cancel-requisite">Отмена</button>
                    <button type="submit" class="button-link">Сохранить</button>
                </div>
            </form>
        </dialog>
        <dialog class="requisite-dialog" id="delete-requisite-dialog"
                aria-labelledby="delete-requisite-title" aria-describedby="delete-requisite-description">
            <form method="post" action="<c:url value='/profile/id${sessionScope.userId}'/>">
                <h2 id="delete-requisite-title">Удалить реквизиты?</h2>
                <p id="delete-requisite-description" class="requisite-delete-description">
                    Из профиля будут удалены реквизиты <strong id="delete-requisite-details"></strong>.
                </p>
                <input type="hidden" name="csrf" value="${sessionScope.profileCsrf}">
                <input type="hidden" name="action" value="delete">
                <input type="hidden" name="id">
                <div class="requisite-dialog-actions">
                    <button type="button" class="button-link" id="cancel-delete-requisite" autofocus>Отмена</button>
                    <button type="submit" class="button-link requisite-delete-confirm">Удалить</button>
                </div>
            </form>
        </dialog>
        <dialog id="logout-dialog" aria-labelledby="logout-question">
            <p id="logout-question">Вы действительно хотите выйти?</p>
            <form method="dialog">
                <button type="submit" class="button-link" autofocus>Отмена</button>
                <a href="<c:url value="/profile/logout"/>" class="button-link">Выйти</a>
            </form>
        </dialog>
        <script>
            const requisiteDialog = document.getElementById('requisite-dialog');
            const requisiteForm = document.getElementById('requisite-form');
            const requisiteTitle = document.getElementById('requisite-dialog-title');

            function showRequisiteForm(requisite) {
                requisiteTitle.textContent = requisite ? 'Изменить реквизиты' : 'Добавить реквизиты';
                requisiteForm.elements.namedItem('action').value = requisite ? 'edit' : 'add';
                requisiteForm.elements.namedItem('id').value = requisite?.id ?? '';
                requisiteForm.elements.bank.value = requisite?.bank ?? '';
                requisiteForm.elements.phone.value = requisite?.phone ?? '';
                requisiteDialog.showModal();
            }

            document.getElementById('add-requisite')?.addEventListener('click', () => showRequisiteForm());
            document.getElementById('cancel-requisite').addEventListener('click', () => requisiteDialog.close());
            document.querySelectorAll('.edit-requisite').forEach(button => {
                button.addEventListener('click', () => showRequisiteForm(button.dataset));
            });
            const deleteDialog = document.getElementById('delete-requisite-dialog');
            const deleteDetails = document.getElementById('delete-requisite-details');
            const deleteId = deleteDialog.querySelector('input[name="id"]');

            document.querySelectorAll('.delete-requisite').forEach(button => {
                button.addEventListener('click', () => {
                    const row = button.closest('.requisite-row');
                    const {id, bank, phone} = row.querySelector('.edit-requisite').dataset;
                    deleteId.value = id;
                    deleteDetails.textContent = bank + ' · ' + phone;
                    deleteDialog.showModal();
                });
            });
            document.getElementById('cancel-delete-requisite').addEventListener('click', () => deleteDialog.close());
            document.addEventListener('click', event => {
                if (deleteDialog.contains(event.target)) return;
                document.querySelectorAll('.requisite-menu[open]').forEach(menu => {
                    if (!menu.contains(event.target)) menu.open = false;
                });
            });
            document.addEventListener('keydown', event => {
                if (event.key === 'Escape' && !requisiteDialog.open && !deleteDialog.open) {
                    const menu = event.target.closest('.requisite-menu[open]');
                    if (menu) {
                        menu.open = false;
                        menu.querySelector('summary').focus();
                    }
                }
            });

            const logoutDialog = document.getElementById('logout-dialog');
            document.getElementById('open-logout-dialog')?.addEventListener('click', () => {
                logoutDialog.showModal();
            });
        </script>
    </body>
</html>
