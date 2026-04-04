package com.fuzzyfilms.ecommerce.service;

import com.fuzzyfilms.ecommerce.model.FreteConfig;
import com.fuzzyfilms.ecommerce.model.FreteConfig.TipoFrete;
import com.fuzzyfilms.ecommerce.model.Produto;
import com.fuzzyfilms.ecommerce.model.Produto.TipoFreteP;
import com.fuzzyfilms.ecommerce.repository.FreteConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class FreteService {
    @Autowired private FreteConfigRepository configRepo;
   public BigDecimal calcularFrete(Produto produto, String cep) {
    if (produto != null && produto.getTipoFrete() != null) {
        if (produto.getTipoFrete() == TipoFreteP.GRATIS) return BigDecimal.ZERO;
        if (produto.getTipoFrete() == TipoFreteP.FIXO && produto.getValorFrete() != null)
            return produto.getValorFrete();
    }
    // fallback para configuração global
    FreteConfig config = freteConfigRepo.findById(1L).orElse(new FreteConfig());
    if (config.getTipoFrete() == TipoFrete.GRATIS) return BigDecimal.ZERO;
    if (config.getTipoFrete() == TipoFrete.FIXO && config.getValorFixo() != null)
        return config.getValorFixo();
    return BigDecimal.ZERO;
}
}