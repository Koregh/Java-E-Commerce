package com.fuzzyfilms.ecommerce.service;

import com.fuzzyfilms.ecommerce.model.AuditLog;
import com.fuzzyfilms.ecommerce.model.User;
import com.fuzzyfilms.ecommerce.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    @Autowired
    private AuditLogRepository auditRepo;

    public void log(User user, AuditLog.TipoAcao tipo, String descricao, String ip) {
        AuditLog log = new AuditLog();
        log.setUsuarioEmail(user != null ? user.getEmail() : "Sistema/Anônimo");
        log.setTipo(tipo);
        log.setDescricao(descricao);
        log.setIp(ip);
        auditRepo.save(log);
    }
}