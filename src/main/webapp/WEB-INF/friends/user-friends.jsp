
<%@ page contentType="text/html;charset=UTF-8" language="java" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<html lang="ru">
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link rel="stylesheet" href="<c:url value='/styles.css'/>">
        <title>Друзья — Купи-мне!</title>
    <script src="<c:url value='/scripts/theme.js'/>"></script></head>
    <body>
        <%@ include file="/WEB-INF/fragments/header.jspf" %>
        <main>
            <%@ include file="/WEB-INF/error-notice.jspf" %>
            <div class="main-container">
                <a href="<c:url value='/'/>" class="button-link-plain"><span aria-hidden="true">←</span> На главную</a>
            </div>
            <div class="friends-container">
                <nav class="friends-nav" aria-label="Разделы друзей">
                    <a href="<c:url value='/friends'/>" class="friends-nav-link"
                    ${friendsSection == 'friends' ? 'aria-current="page"' : ''}>Мои друзья</a>
                    <a href="<c:url value='/friends?section=search'/>" class="friends-nav-link"
                    ${friendsSection == 'search' ? 'aria-current="page"' : ''}>Поиск</a>
                    <a href="<c:url value='/friends?section=requests'/>" class="friends-nav-link"
                    ${friendsSection == 'requests' ? 'aria-current="page"' : ''}>Заявки в друзья</a>
                </nav>
                <hr class="divider">
                <section class="friends-content" aria-labelledby="friends-section-title">
                    <c:choose>
                        <c:when test="${friendsSection == 'search'}">
                            <div class="column-flex-gap-5px">
                                <h2 id="friends-section-title">Поиск</h2>
                                <form action="<c:url value='/friends'/>" method="get">
                                    <div class="column-flex-gap-5px">
                                        <input type="hidden" name="section" value="search">
                                        <label for="search-login">Логин пользователя</label>
                                        <input id="search-login" name="login" type="search"
                                               value="<c:out value='${searchLogin}'/>"
                                               placeholder="Введите логин" class="input-line" required>
                                        <button type="submit" class="button-link margin-auto min-width-150px">Найти
                                        </button>
                                    </div>
                                </form>
                            </div>
                            <c:if test="${not empty searchLogin}">
                                <div class="friend-search-results">
                                    <c:choose>
                                        <c:when test="${empty searchResults}">
                                            <p>Пользователи не найдены.</p>
                                        </c:when>
                                        <c:otherwise>
                                            <p>Найденные пользователи</p>
                                            <ul class="friends-list">
                                                <c:forEach items="${searchResults}" var="foundUser">
                                                    <c:url var="searchProfileUrl" value="/profile/id${foundUser.id}">
                                                        <c:param name="from" value="search"/>
                                                        <c:param name="login" value="${searchLogin}"/>
                                                        <c:param name="page" value="${page}"/>
                                                    </c:url>
                                                    <li>
                                                        <c:choose>
                                                            <c:when test="${foundUser.id ne sessionScope.userId}">
                                                                <a href="<c:out value='${searchProfileUrl}'/>"
                                                                   class="button-link-plain search-result-link">
                                                                    <c:out value="${foundUser.login}"/>
                                                                </a>
                                                            </c:when>
                                                            <c:otherwise>
                                                                <a href="<c:out value='${searchProfileUrl}'/>"
                                                                   class="button-link-plain search-result-link">
                                                                    <div class="container-row it-is-me">

                                                                        <p><c:out value="${foundUser.login}"/></p>

                                                                        <p>–</p>

                                                                        <p>Это же я!</p>
                                                                    </div>
                                                                </a>
                                                            </c:otherwise>
                                                        </c:choose>
                                                    </li>
                                                </c:forEach>
                                            </ul>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                                <nav class="friends-pagination" aria-label="Страницы результатов поиска">
                                    <div class="friends-pagination-slot">
                                        <c:if test="${page-1 gt 0}">
                                            <a href="<c:url value="/friends?section=search&login=${searchLogin}&page=${page-1}"/>"
                                               class="text-link" aria-label="Предыдущая страница">←</a>
                                        </c:if>
                                    </div>
                                    <span class="friends-pagination-current" aria-current="page"><c:out
                                            value="${page}"/></span>
                                    <div class="friends-pagination-slot">
                                        <c:if test="${hasNextPage}">
                                            <a href="<c:url value="/friends?section=search&login=${searchLogin}&page=${page+1}"/>"
                                               class="text-link" aria-label="Следующая страница">→</a>
                                        </c:if>
                                    </div>
                                </nav>
                            </c:if>
                        </c:when>
                        <c:when test="${friendsSection == 'requests'}">
                            <h2 id="friends-section-title">Заявки в друзья</h2>
                            <c:choose>
                                <c:when test="${empty incomingRequests}">
                                    <p>У вас нет входящих заявок в друзья.</p>
                                </c:when>
                                <c:otherwise>
                                    <p>Откройте профиль, чтобы принять или отклонить заявку</p>
                                    <ul class="friends-list">
                                        <c:forEach items="${incomingRequests}" var="sender">
                                            <c:url var="senderUrl" value="/profile/id${sender.id}">
                                                <c:param name="from" value="requests"/>
                                            </c:url>
                                            <li><a href="<c:out value='${senderUrl}'/>" class="button-link-plain search-result-link"><c:out value="${sender.login}"/></a></li>
                                        </c:forEach>
                                    </ul>
                                </c:otherwise>
                            </c:choose>
                        </c:when>
                        <c:otherwise>
                            <h2 id="friends-section-title">Мои друзья</h2>
                            <c:choose>
                                <c:when test="${empty friendList}">
                                    <p>У вас пока нет друзей.</p>
                                </c:when>
                                <c:otherwise>
                                    <ul class="friends-list">
                                        <c:forEach items="${friendList}" var="friend">
                                            <c:url var="friendProfileUrl" value="/profile/id${friend.id}">
                                                <c:param name="from" value="friends"/>
                                            </c:url>
                                            <li><a href="<c:out value='${friendProfileUrl}'/>" class="button-link-plain search-result-link"><c:out
                                                    value="${friend.login}"/></a></li>
                                        </c:forEach>
                                    </ul>
                                </c:otherwise>
                            </c:choose>
                        </c:otherwise>
                    </c:choose>
                </section>
            </div>
        </main>

    </body>
</html>
