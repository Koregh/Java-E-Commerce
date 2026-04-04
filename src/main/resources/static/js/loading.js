// Função para aplicar loading em qualquer formulário
function initLoadingForms() {
    const forms = document.querySelectorAll('form[data-loading]'); // marca os forms que devem ter loading
    forms.forEach(form => {
        form.addEventListener('submit', function(e) {
            const submitBtn = this.querySelector('button[type="submit"], input[type="submit"]');
            if (!submitBtn) return;

            // Evita múltiplos envios
            if (submitBtn.disabled) {
                e.preventDefault();
                return;
            }

            // Guarda o texto original e desabilita
            const originalText = submitBtn.innerHTML;
            submitBtn.disabled = true;
            submitBtn.classList.add('btn-loading');
            submitBtn.innerHTML = '<span class="spinner"></span> Processando...';

            // Opcional: reativa após 30s (fallback) – mas normalmente a página recarrega
            setTimeout(() => {
                if (submitBtn.disabled) {
                    submitBtn.disabled = false;
                    submitBtn.classList.remove('btn-loading');
                    submitBtn.innerHTML = originalText;
                }
            }, 30000);
        });
    });
}

// Inicializa após o DOM carregar
document.addEventListener('DOMContentLoaded', initLoadingForms);