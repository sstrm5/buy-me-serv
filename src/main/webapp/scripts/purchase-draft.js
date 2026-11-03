(() => {
    const context = document.getElementById('purchase-draft');
    if (!context) return;
    const key = `purchase-draft:${location.pathname}:${context.dataset.user}:${context.dataset.purchase}`;
    if (context.dataset.state === 'COMPLETED') {
        try { localStorage.removeItem(key); } catch (_) {   }
        return;
    }
    const forms = [...document.querySelectorAll('.purchase-item-form')];
    const finish = document.getElementById('purchase-finish');
    if (!forms.length || !finish) return;
    const fields = ['status', 'quantity', 'price', 'actualName'];
    function notice(message) {
        let box = document.getElementById('purchase-save-error');
        if (!box) {
            box = document.createElement('div');
            box.id = 'purchase-save-error';
            box.className = 'error';
            const text = document.createElement('span');
            text.className = 'error-message';
            text.setAttribute('role', 'alert');
            box.append(text);
            const close = document.createElement('button');
            close.type = 'button';
            close.className = 'error-close';
            close.setAttribute('aria-label', 'Закрыть уведомление');
            close.textContent = '×';
            close.onclick = () => box.remove();
            box.append(close);
            document.querySelector('main').prepend(box);
        }
        box.querySelector('.error-message').textContent = message;
        box.scrollIntoView({block: 'nearest'});
    }
    let draft = {};
    try { draft = JSON.parse(localStorage.getItem(key) || '{}') || {}; } catch (_) {   }
    function persist() {
        const values = {};
        forms.forEach(form => {
            values[form.elements.itemId.value] = Object.fromEntries(fields.map(name => [name, form.elements[name].value]));
        });
        try { localStorage.setItem(key, JSON.stringify(values)); }
        catch (_) { notice('Браузер не смог сохранить введённые данные. Не обновляйте страницу до завершения закупки.'); }
    }
    forms.forEach(form => {
        const saved = draft[form.elements.itemId.value];
        if (saved && typeof saved === 'object') fields.forEach(name => {
            if (typeof saved[name] === 'string') form.elements[name].value = saved[name];
        });
        form.addEventListener('input', persist);
        form.addEventListener('change', persist);
        form.addEventListener('submit', event => { event.preventDefault(); persist(); });
    });
    let submitting = false;
    finish.addEventListener('submit', async event => {
        event.preventDefault();
        if (submitting) return;
        persist();
        for (const form of forms) {
            const bought = ['BOUGHT', 'QUANTITY_CHANGED', 'REPLACEMENT'].includes(form.elements.status.value);
            form.elements.quantity.required = bought;
            form.elements.price.required = bought;
            form.elements.actualName.required = form.elements.status.value === 'REPLACEMENT';
            if (form.elements.status.value === 'PENDING') {
                notice('Выберите результат для каждого товара перед завершением закупки.');
                form.elements.status.focus();
                return;
            }
            if (!form.reportValidity()) return;
        }
        submitting = true;
        const button = finish.querySelector('button');
        button.disabled = true;
        try {
            for (const form of forms) {
                const response = await fetch(location.pathname, {
                    method: 'POST', body: new URLSearchParams(new FormData(form)), credentials: 'same-origin'
                });
                if (!response.ok || new URL(response.url).pathname !== location.pathname) {
                    const html = new DOMParser().parseFromString(await response.text(), 'text/html');
                    throw new Error(html.querySelector('.error-message')?.textContent || 'Не удалось отправить результаты. Проверьте соединение и вход в аккаунт. Введённые данные сохранены в браузере.');
                }
            }
            
            HTMLFormElement.prototype.submit.call(finish);
        } catch (error) {
            notice(error.message || 'Не удалось отправить результаты. Введённые данные сохранены в браузере.');
            submitting = false;
            button.disabled = false;
        }
    });
})();
