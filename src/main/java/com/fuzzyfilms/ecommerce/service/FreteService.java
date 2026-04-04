package com.fuzzyfilms.ecommerce.service;

import com.fuzzyfilms.ecommerce.model.FreteConfig;
import com.fuzzyfilms.ecommerce.repository.FreteConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class FreteService {
    @Autowired private FreteConfigRepository configRepo;
    public BigDecimal calcularFrete(String cep) {
        FreteConfig config = configRepo.findById(1L).orElse(new FreteConfig());
        if (config.getTipoFrete() == FreteConfig.TipoFrete.GRATIS) return BigDecimal.ZERO;
        if (config.getTipoFrete() == FreteConfig.TipoFrete.FIXO && config.getValorFixo() != null)
            return config.getValorFixo();
        return BigDecimal.ZERO;
    }
}