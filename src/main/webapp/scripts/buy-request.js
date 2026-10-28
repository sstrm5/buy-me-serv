(() => {
    const form = document.getElementById('buy-request-form');
    if (!form) return;

    const items = document.getElementById('request-items');
    const addButton = document.getElementById('add-request-item');
    const maxItems = 50;

    function updateRows() {
        const rows = items.querySelectorAll('.request-item');
        rows.forEach((row, index) => {
            row.querySelector('.request-item-title').textContent = `Товар ${index + 1}`;
            row.querySelector('.remove-request-item').disabled = rows.length === 1;
            row.querySelectorAll('[name]').forEach(input => {
                input.id = `${input.name}-${index}`;
                row.querySelector(`[data-for="${input.name}"]`).htmlFor = input.id;
            });
        });
        addButton.disabled = rows.length >= maxItems;
        document.getElementById('request-item-limit').textContent =
            rows.length >= maxItems ? `Не больше ${maxItems} товаров в одном запросе.` : '';
    }

    addButton.addEventListener('click', () => {
        const rows = items.querySelectorAll('.request-item');
        if (rows.length >= maxItems) return;
        const row = rows[0].cloneNode(true);
        row.querySelectorAll('input, textarea, select').forEach(input => {
            if (input.tagName === 'SELECT') {
                input.selectedIndex = 0;
            } else {
                input.value = input.name === 'quantity' ? '1' : '';
            }
        });
        items.append(row);
        updateRows();
        row.querySelector('[name="itemName"]').focus();
    });

    items.addEventListener('click', event => {
        const removeButton = event.target.closest('.remove-request-item');
        if (removeButton && items.querySelectorAll('.request-item').length > 1) {
            removeButton.closest('.request-item').remove();
            updateRows();
        }
    });

    function validateRecipients() {
        const first = form.querySelector('[name="recipientId"]');
        first.setCustomValidity(form.querySelector('[name="recipientId"]:checked')
            ? '' : 'Выберите хотя бы одного друга.');
    }
    form.querySelectorAll('[name="recipientId"]').forEach(input => {
        input.addEventListener('change', validateRecipients);
    });
    validateRecipients();
    updateRows();
})();
