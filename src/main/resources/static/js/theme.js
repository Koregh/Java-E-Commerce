// theme.js - controle de tema escuro
(function() {
    const initTheme = () => {
        const themeToggle = document.getElementById('themeToggle');
        if (!themeToggle) return;
        
        const storedTheme = localStorage.getItem('theme');
        const systemPrefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        const isDark = storedTheme === 'dark' || (storedTheme === null && systemPrefersDark);
        
        if (isDark) {
            document.body.classList.add('dark');
            themeToggle.innerHTML = '☀️';
            themeToggle.setAttribute('aria-label', 'Alternar tema claro');
        } else {
            themeToggle.innerHTML = '🌙';
            themeToggle.setAttribute('aria-label', 'Alternar tema escuro');
        }
        
        themeToggle.addEventListener('click', () => {
            document.body.classList.toggle('dark');
            const nowDark = document.body.classList.contains('dark');
            localStorage.setItem('theme', nowDark ? 'dark' : 'light');
            themeToggle.innerHTML = nowDark ? '☀️' : '🌙';
            themeToggle.setAttribute('aria-label', nowDark ? 'Alternar tema claro' : 'Alternar tema escuro');
        });
        
        // Sincronizar com mudanças no sistema (se não houver preferência salva)
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
            if (localStorage.getItem('theme') === null) {
                document.body.classList.toggle('dark', e.matches);
                if (themeToggle) themeToggle.innerHTML = e.matches ? '☀️' : '🌙';
            }
        });
    };
    
    // Executar após o DOM carregar
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initTheme);
    } else {
        initTheme();
    }
})();