(() => {
    const dark = document.cookie.split(';').some(entry => entry.trim() === 'theme=dark');
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    document.addEventListener('DOMContentLoaded', () => {
        const toggle = document.getElementById('theme-toggle');
        if (!toggle) return;
        toggle.checked = document.documentElement.dataset.theme === 'dark';
        toggle.addEventListener('change', () => {
            const theme = toggle.checked ? 'dark' : 'light';
            document.documentElement.dataset.theme = theme;
            document.cookie = `theme=${theme}; Path=/; Max-Age=31536000; SameSite=Lax${location.protocol === 'https:' ? '; Secure' : ''}`;
        });
    });
})();
