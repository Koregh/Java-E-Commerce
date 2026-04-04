package com.fuzzyfilms.ecommerce.dto;

import com.fuzzyfilms.ecommerce.model.Cargo;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContaDTO {
    private Long id;
    private String nome;
    private String emailMascarado;
    private Cargo cargo;
}