package com.arcbank.cbs.transaccion.mapper;

import com.arcbank.cbs.transaccion.dto.TransaccionRequestDTO;
import com.arcbank.cbs.transaccion.dto.TransaccionResponseDTO;
import com.arcbank.cbs.transaccion.model.Transaccion;
import org.springframework.stereotype.Component;

@Component
public class TransaccionMapper {

    public Transaccion toEntity(TransaccionRequestDTO dto) {
        if (dto == null) return null;
        
        return Transaccion.builder()
                .referencia(dto.getReferencia())
                .tipoOperacion(dto.getTipoOperacion())
                .idCuentaOrigen(dto.getIdCuentaOrigen())
                .idCuentaDestino(dto.getIdCuentaDestino())
                .cuentaExterna(dto.getCuentaExterna())
                .idBancoExterno(dto.getIdBancoExterno())
                .idTransaccionReversa(dto.getIdTransaccionReversa())
                .monto(dto.getMonto())
                .descripcion(dto.getDescripcion())
                .canal(dto.getCanal())
                .idSucursal(dto.getIdSucursal())
                .build();
    }

    public TransaccionResponseDTO toDto(Transaccion entity) {
        if (entity == null) return null;

        return TransaccionResponseDTO.builder()
                .idTransaccion(entity.getIdTransaccion())
                .referencia(entity.getReferencia())
                .tipoOperacion(entity.getTipoOperacion())
                .idCuentaOrigen(entity.getIdCuentaOrigen())
                .idCuentaDestino(entity.getIdCuentaDestino())
                .cuentaExterna(entity.getCuentaExterna())
                .idBancoExterno(entity.getIdBancoExterno())
                .monto(entity.getMonto())
                .saldoResultante(entity.getSaldoResultante())
                .fechaCreacion(entity.getFechaCreacion())
                .descripcion(entity.getDescripcion())
                .canal(entity.getCanal())
                .estado(entity.getEstado())
                .build();
    }
}