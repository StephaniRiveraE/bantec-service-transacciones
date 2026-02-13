package com.arcbank.cbs.transaccion.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcbank.cbs.transaccion.client.CuentaCliente;
import com.arcbank.cbs.transaccion.client.SwitchClient;
import com.arcbank.cbs.transaccion.client.ClienteClient;
import com.arcbank.cbs.transaccion.dto.SaldoDTO;
import com.arcbank.cbs.transaccion.dto.RefoundRequestDTO;
import com.arcbank.cbs.transaccion.dto.SwitchRefundRequest;
import com.arcbank.cbs.transaccion.dto.SwitchTransferRequest;
import com.arcbank.cbs.transaccion.dto.SwitchTransferResponse;
import com.arcbank.cbs.transaccion.dto.TransaccionRequestDTO;
import com.arcbank.cbs.transaccion.dto.TransaccionResponseDTO;
import com.arcbank.cbs.transaccion.exception.BusinessException;
import com.arcbank.cbs.transaccion.model.Transaccion;
import com.arcbank.cbs.transaccion.repository.TransaccionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransaccionServiceImpl implements TransaccionService {

    private final TransaccionRepository transaccionRepository;
    private final CuentaCliente cuentaCliente;
    private final SwitchClient switchClient;
    private final ClienteClient clienteClient;

    @Value("${app.banco.codigo:BANTEC}")
    private String codigoBanco;

    @Override
    @Transactional
    public TransaccionResponseDTO crearTransaccion(TransaccionRequestDTO request) {
        log.info("Iniciando transacción Tipo: {} | Ref: {}", request.getTipoOperacion(), request.getReferencia());

        if (request.getReferencia() != null) {
            var existingTx = transaccionRepository.findByReferencia(request.getReferencia());
            if (existingTx.isPresent()) {
                return mapearADTO(existingTx.get(), null);
            }
        }

        String tipoOp = request.getTipoOperacion().toUpperCase();

        Transaccion trx = Transaccion.builder()
                .referencia(request.getReferencia() != null ? request.getReferencia() : UUID.randomUUID().toString())
                .tipoOperacion(tipoOp)
                .monto(request.getMonto())
                .descripcion(request.getDescripcion())
                .canal(request.getCanal() != null ? request.getCanal() : "WEB")
                .idSucursal(request.getIdSucursal())
                .cuentaExterna(request.getCuentaExterna())
                .idBancoExterno(request.getIdBancoExterno())
                .idTransaccionReversa(request.getIdTransaccionReversa())
                .estado("PENDIENTE")
                .build();

        BigDecimal saldoImpactado = null;

        try {
            switch (tipoOp) {
                case "DEPOSITO" -> {
                    if (request.getIdCuentaDestino() == null)
                        throw new BusinessException("El DEPOSITO requiere una cuenta destino obligatoria.");
                    trx.setIdCuentaDestino(request.getIdCuentaDestino());
                    trx.setIdCuentaOrigen(null);
                    saldoImpactado = procesarSaldo(trx.getIdCuentaDestino(), request.getMonto());
                }

                case "RETIRO" -> {
                    if (request.getIdCuentaOrigen() == null)
                        throw new BusinessException("El RETIRO requiere una cuenta origen obligatoria.");
                    trx.setIdCuentaOrigen(request.getIdCuentaOrigen());
                    trx.setIdCuentaDestino(null);
                    saldoImpactado = procesarSaldo(trx.getIdCuentaOrigen(), request.getMonto().negate());
                }

                case "TRANSFERENCIA_INTERNA" -> {
                    if (request.getIdCuentaOrigen() == null || request.getIdCuentaDestino() == null) {
                        throw new BusinessException(
                                "La TRANSFERENCIA INTERNA requiere cuenta origen y cuenta destino.");
                    }
                    if (request.getIdCuentaOrigen().equals(request.getIdCuentaDestino())) {
                        throw new BusinessException("No se puede transferir a la misma cuenta.");
                    }
                    trx.setIdCuentaOrigen(request.getIdCuentaOrigen());
                    trx.setIdCuentaDestino(request.getIdCuentaDestino());

                    BigDecimal saldoOrigen = procesarSaldo(trx.getIdCuentaOrigen(), request.getMonto().negate());
                    BigDecimal saldoDestino = procesarSaldo(trx.getIdCuentaDestino(), request.getMonto());

                    trx.setSaldoResultanteDestino(saldoDestino);
                    saldoImpactado = saldoOrigen;
                }

                case "TRANSFERENCIA_SALIDA" -> {
                    if (request.getIdCuentaOrigen() == null)
                        throw new BusinessException("Falta cuenta origen para transferencia externa.");
                    if (request.getCuentaExterna() == null || request.getCuentaExterna().isBlank())
                        throw new BusinessException("Falta cuenta destino externa para transferencia interbancaria.");

                    BigDecimal montoTotal = request.getMonto();

                    trx.setIdCuentaOrigen(request.getIdCuentaOrigen());
                    trx.setIdCuentaDestino(null);
                    trx.setCuentaExterna(request.getCuentaExterna());
                    trx.setIdBancoExterno(request.getIdBancoExterno());
                    trx.setMonto(montoTotal);
                    trx.setDescripcion(request.getDescripcion());

                    BigDecimal saldoDebitado = null;
                    try {
                        saldoDebitado = procesarSaldo(trx.getIdCuentaOrigen(), montoTotal.negate());
                    } catch (Exception e) {
                        throw new BusinessException("Error al debitar cuenta origen: " + e.getMessage());
                    }

                    String numeroCuentaOrigen = obtenerNumeroCuenta(request.getIdCuentaOrigen());
                    String nombreDebtor = "Cliente Bantec";
                    String tipoCuentaDebtor = "SAVINGS";

                    try {
                        Map<String, Object> cuentaInfo = cuentaCliente.obtenerCuenta(request.getIdCuentaOrigen());
                        if (cuentaInfo != null && cuentaInfo.get("idCliente") != null) {
                            Integer idCliente = (Integer) cuentaInfo.get("idCliente");
                            Map<String, Object> clienteInfo = clienteClient.obtenerCliente(idCliente);
                            if (clienteInfo != null && clienteInfo.get("nombre") != null) {
                                nombreDebtor = clienteInfo.get("nombre").toString();
                            }
                        }
                    } catch (Exception e) {
                        log.warn("No se pudo obtener detalle completo del cliente/cuenta: {}", e.getMessage());
                    }

                    try {
                        String messageId = "MSG-BANTEC-" + System.currentTimeMillis();
                        String creationTime = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                                .format(java.time.format.DateTimeFormatter.ISO_INSTANT);

                        String beneficiario = request.getBeneficiario() != null ? request.getBeneficiario()
                                : "Beneficiario Externo";

                        trx.setBeneficiario(beneficiario);

                        SwitchTransferRequest switchRequest = SwitchTransferRequest.builder()
                                .header(SwitchTransferRequest.Header.builder()
                                        .messageId(messageId)
                                        .creationDateTime(creationTime)
                                        .originatingBankId(codigoBanco)
                                        .build())
                                .body(SwitchTransferRequest.Body.builder()
                                        .instructionId(trx.getReferencia())
                                        .endToEndId("REF-BANTEC-" + trx.getReferencia())
                                        .amount(SwitchTransferRequest.Amount.builder()
                                                .currency("USD")
                                                .value(request.getMonto())
                                                .build())
                                        .debtor(SwitchTransferRequest.Party.builder()
                                                .name(nombreDebtor)
                                                .accountId(numeroCuentaOrigen)
                                                .accountType(tipoCuentaDebtor)
                                                .bankId(codigoBanco)
                                                .build())
                                        .creditor(SwitchTransferRequest.Party.builder()
                                                .name(beneficiario)
                                                .accountId(request.getCuentaExterna())
                                                .accountType("SAVINGS")
                                                .targetBankId(request.getIdBancoExterno() != null
                                                        ? request.getIdBancoExterno()
                                                        : "UNKNOWN")
                                                .build())
                                        .remittanceInformation(request.getDescripcion() != null
                                                ? request.getDescripcion()
                                                : "Transferencia interbancaria BANTEC")
                                        .build())
                                .build();

                        SwitchTransferResponse switchResp = switchClient.enviarTransferencia(switchRequest);

                        if (switchResp.getData() != null && switchResp.getData().getCodigoReferencia() != null) {
                            trx.setCodigoReferencia(switchResp.getData().getCodigoReferencia());
                        }

                        if (switchResp == null || !switchResp.isSuccess()) {
                            BigDecimal saldoRevertido = procesarSaldo(trx.getIdCuentaOrigen(), montoTotal);

                            String switchError = "Error desconocido";
                            if (switchResp != null && switchResp.getError() != null) {
                                switchError = switchResp.getError().getMessage();
                            }

                            String friendlyError = "Error en destino";
                            String lowerError = switchError.toLowerCase();

                            if (lowerError.contains("ac01"))
                                friendlyError = "Cuenta destino inválida / inexistente";
                            else if (lowerError.contains("ac03"))
                                friendlyError = "Cuenta destino inválida (AC03)";
                            else if (lowerError.contains("timeout") || lowerError.contains("504"))
                                friendlyError = "Tiempo de espera agotado en destino";
                            else if (lowerError.contains("fondos"))
                                friendlyError = "Fondos insuficientes";
                            else {
                                friendlyError = "Error de comunicación con la entidad financiera";
                            }

                            trx.setEstado("FALLIDA");
                            trx.setSaldoResultante(saldoRevertido);
                            trx.setDescripcion("RECHAZADA: " + friendlyError);

                            Transaccion fallida = transaccionRepository.save(trx);
                            return mapearADTO(fallida, null);
                        }

                        boolean confirmado = false;
                        String ultimoEstado = "PENDING";
                        String motivoFallo = "";

                        // --- FIX: Verificar si el POST ya retornó estado final ---
                        if (switchResp.getData() != null) {
                            String estadoInicial = switchResp.getData().getEstado();
                            if ("COMPLETED".equalsIgnoreCase(estadoInicial)
                                    || "EXITOSA".equalsIgnoreCase(estadoInicial)) {
                                log.info("Switch retornó COMPLETED en respuesta inicial. Omitiendo polling.");
                                confirmado = true;
                            }
                        }

                        for (int i = 0; i < 10 && !confirmado; i++) {
                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }

                            try {
                                SwitchTransferResponse statusResp = switchClient
                                        .consultarEstadoTransferencia(trx.getReferencia());
                                if (statusResp != null && statusResp.getData() != null) {
                                    ultimoEstado = statusResp.getData().getEstado();

                                    if ("COMPLETED".equalsIgnoreCase(ultimoEstado)
                                            || "EXITOSA".equalsIgnoreCase(ultimoEstado)
                                            || "QUEUED".equalsIgnoreCase(ultimoEstado)
                                            || "ACCEPTED".equalsIgnoreCase(ultimoEstado)) {
                                        confirmado = true;
                                        break;
                                    }

                                    if ("FAILED".equalsIgnoreCase(ultimoEstado)
                                            || "FALLIDA".equalsIgnoreCase(ultimoEstado)
                                            || "RECHAZADA".equalsIgnoreCase(ultimoEstado)) {
                                        motivoFallo = (statusResp.getError() != null)
                                                ? statusResp.getError().getMessage()
                                                : "Rechazo desconocido";

                                        BigDecimal saldoRevertido = procesarSaldo(trx.getIdCuentaOrigen(), montoTotal);
                                        trx.setEstado("FALLIDA");
                                        trx.setSaldoResultante(saldoRevertido);
                                        trx.setDescripcion("RECHAZADA POR DESTINO: " + motivoFallo);
                                        Transaccion fallida = transaccionRepository.save(trx);

                                        throw new BusinessException("Transacción Rechazada: " + motivoFallo);
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Wait", e);
                            }
                        }

                        if (confirmado) {
                            saldoImpactado = saldoDebitado;
                            trx.setEstado("COMPLETADA");
                        } else {
                            saldoImpactado = saldoDebitado;
                            trx.setEstado("PENDIENTE");
                            trx.setDescripcion("En proceso de validación. Le notificaremos.");
                            trx.setSaldoResultante(saldoDebitado);

                            Transaccion pendiente = transaccionRepository.save(trx);
                            TransaccionResponseDTO respDto = mapearADTO(pendiente, null);
                            respDto.setMensajeUsuario("En proceso de validación. Le notificaremos.");
                            return respDto;
                        }

                    } catch (Exception e) {
                        try {
                            String revMessageId = "MSG-REV-AUTO-" + System.currentTimeMillis();
                            String revCreationTime = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                                    .format(java.time.format.DateTimeFormatter.ISO_INSTANT);

                            SwitchRefundRequest refundReq = SwitchRefundRequest.builder()
                                    .header(SwitchRefundRequest.Header.builder()
                                            .messageId(revMessageId)
                                            .creationDateTime(revCreationTime)
                                            .originatingBankId(codigoBanco)
                                            .build())
                                    .body(SwitchRefundRequest.Body.builder()
                                            .returnInstructionId(UUID.randomUUID().toString())
                                            .originalInstructionId(trx.getReferencia())
                                            .returnReason("MS03")
                                            .returnAmount(SwitchRefundRequest.Amount.builder()
                                                    .currency("USD")
                                                    .value(trx.getMonto())
                                                    .build())
                                            .build())
                                    .build();

                            try {
                                switchClient.solicitarDevolucion(refundReq);
                            } catch (Exception exRev) {
                                String err = exRev.getMessage();
                                if (err == null || (!err.contains("409")
                                        && !err.contains("Transacción original no encontrada"))) {
                                    throw exRev;
                                }
                            }
                        } catch (Exception ex) {
                        }

                        BigDecimal saldoRevertido = procesarSaldo(trx.getIdCuentaOrigen(), montoTotal);

                        trx.setEstado("FALLIDA");
                        trx.setSaldoResultante(saldoRevertido);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : "Error desconocido";

                        String friendlyEx = "Error de comunicación";
                        String lowerEx = errorMsg.toLowerCase();

                        if (lowerEx.contains("ac01"))
                            friendlyEx = "Cuenta destino inválida";
                        else if (lowerEx.contains("504") || lowerEx.contains("time out")
                                || lowerEx.contains("timed out"))
                            friendlyEx = "El banco destino no responde";
                        else {
                            friendlyEx = "Error de comunicación con la entidad financiera";
                        }

                        trx.setDescripcion("RECHAZADA: " + friendlyEx);

                        Transaccion fallida = transaccionRepository.save(trx);
                        return mapearADTO(fallida, null);
                    }
                }

                case "TRANSFERENCIA_ENTRADA" -> {
                    if (request.getIdCuentaDestino() == null)
                        throw new BusinessException("Falta cuenta destino para recepción externa.");
                    trx.setIdCuentaDestino(request.getIdCuentaDestino());
                    trx.setIdCuentaOrigen(null);
                    saldoImpactado = procesarSaldo(trx.getIdCuentaDestino(), request.getMonto());
                }

                default -> throw new BusinessException("Tipo de operación no soportado: " + tipoOp);
            }

            trx.setSaldoResultante(saldoImpactado);
            trx.setEstado("COMPLETADA");

            Transaccion guardada = transaccionRepository.save(trx);

            return mapearADTO(guardada, null);

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public List<TransaccionResponseDTO> obtenerPorCuenta(Integer idCuenta) {
        return transaccionRepository.findPorCuenta(idCuenta).stream()
                .map(t -> mapearADTO(t, idCuenta))
                .collect(Collectors.toList());
    }

    @Override
    public TransaccionResponseDTO obtenerPorId(Integer id) {
        if (id == null) {
            throw new BusinessException("El ID de la transacción no puede ser nulo.");
        }
        Transaccion t = transaccionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Transacción no encontrada con ID: " + id));
        return mapearADTO(t, null);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public TransaccionResponseDTO buscarPorReferencia(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            throw new BusinessException("La referencia no puede estar vacía.");
        }

        Transaccion t = null;

        // Prioridad 1: Buscar por Código de Referencia (6 dígitos)
        if (referencia.matches("^[0-9]{6}$")) {
            t = transaccionRepository.findByCodigoReferencia(referencia).orElse(null);
        }

        // Prioridad 2: Buscar por ID interno (numérico, pero verifiquemos que no sea un
        // codigoReferencia coincidente)
        if (t == null && referencia.matches("\\d+") && !referencia.matches("^[0-9]{6}$")) {
            try {
                Integer id = Integer.parseInt(referencia);
                t = transaccionRepository.findById(id).orElse(null);
            } catch (Exception e) {
            }
        }

        // Fallback: Si es numérico y no se encontró como CodigoReferencia, intentar
        // como ID
        if (t == null && referencia.matches("\\d+")) {
            try {
                Integer id = Integer.parseInt(referencia);
                t = transaccionRepository.findById(id).orElse(null);
            } catch (Exception e) {
            }
        }

        // Prioridad 3: Buscar por UUID/Referencia larga
        if (t == null) {
            t = transaccionRepository.findByReferencia(referencia)
                    .orElseThrow(
                            () -> new BusinessException("Transacción no encontrada con referencia/ID: " + referencia));
        }

        if ("PENDIENTE".equalsIgnoreCase(t.getEstado()) || "EN_PROCESO".equalsIgnoreCase(t.getEstado())) {
            try {
                SwitchTransferResponse switchResp = switchClient.consultarEstadoTransferencia(t.getReferencia());

                if (switchResp != null && switchResp.getData() != null) {

                    // Actualizar Codigo Referencia si llega en la consulta
                    if (t.getCodigoReferencia() == null && switchResp.getData().getCodigoReferencia() != null) {
                        t.setCodigoReferencia(switchResp.getData().getCodigoReferencia());
                    }

                    String switchStatus = switchResp.getData().getEstado();
                    log.info("Respuesta del Switch para {}: {}", t.getReferencia(), switchStatus);

                    if ("COMPLETED".equalsIgnoreCase(switchStatus) || "EXITOSA".equalsIgnoreCase(switchStatus)
                            || "QUEUED".equalsIgnoreCase(switchStatus) || "ACCEPTED".equalsIgnoreCase(switchStatus)) {
                        t.setEstado("COMPLETADA");
                        t.setDescripcion("Transferencia completada (Sincronizada)");
                        t = transaccionRepository.save(t);

                    } else if ("FAILED".equalsIgnoreCase(switchStatus) || "FALLIDA".equalsIgnoreCase(switchStatus)
                            || "RECHAZADA".equalsIgnoreCase(switchStatus)) {

                        String errorMsg = (switchResp.getError() != null) ? switchResp.getError().getMessage()
                                : "Rechazo confirmado por Switch";

                        if (t.getIdCuentaOrigen() != null && t.getMonto() != null) {
                            try {
                                BigDecimal saldoRevertido = procesarSaldo(t.getIdCuentaOrigen(), t.getMonto());
                                t.setSaldoResultante(saldoRevertido);
                            } catch (Exception e) {
                            }
                        }

                        t.setEstado("FALLIDA");
                        t.setDescripcion("RECHAZADA: " + errorMsg);
                        t = transaccionRepository.save(t);
                    }
                }
            } catch (Exception e) {
            }
        }

        return mapearADTO(t, null);
    }

    private BigDecimal procesarSaldo(Integer idCuenta, BigDecimal montoCambio) {
        BigDecimal saldoActual;

        try {
            saldoActual = cuentaCliente.obtenerSaldo(idCuenta);
            if (saldoActual == null) {
                throw new BusinessException("La cuenta ID " + idCuenta + " existe pero retornó saldo nulo.");
            }
        } catch (Exception e) {
            throw new BusinessException("No se pudo validar la cuenta ID: " + idCuenta + ". Verifique que exista.");
        }

        BigDecimal nuevoSaldo = saldoActual.add(montoCambio);

        if (nuevoSaldo.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(
                    "Fondos insuficientes en la cuenta ID: " + idCuenta + ". Saldo actual: " + saldoActual);
        }

        try {
            cuentaCliente.actualizarSaldo(idCuenta, new SaldoDTO(nuevoSaldo));
        } catch (Exception e) {
            throw new BusinessException("Error al actualizar el saldo de la cuenta ID: " + idCuenta);
        }

        return nuevoSaldo;
    }

    private TransaccionResponseDTO mapearADTO(Transaccion t, Integer idCuentaVisor) {
        BigDecimal saldoAMostrar = t.getSaldoResultante();

        if (idCuentaVisor != null &&
                t.getIdCuentaDestino() != null &&
                t.getIdCuentaDestino().equals(idCuentaVisor) &&
                t.getSaldoResultanteDestino() != null) {

            saldoAMostrar = t.getSaldoResultanteDestino();
        }

        String numeroCuentaOrigen = null;
        String nombreOrigen = null;
        if (t.getIdCuentaOrigen() != null) {
            Map<String, String> infoOrigen = obtenerInfoCuenta(t.getIdCuentaOrigen());
            numeroCuentaOrigen = infoOrigen.get("numeroCuenta");
            nombreOrigen = infoOrigen.get("nombreCliente");
        }

        String numeroCuentaDestino = null;
        String nombreDestino = null;
        if (t.getIdCuentaDestino() != null) {
            Map<String, String> infoDestino = obtenerInfoCuenta(t.getIdCuentaDestino());
            numeroCuentaDestino = infoDestino.get("numeroCuenta");
            nombreDestino = infoDestino.get("nombreCliente");
        } else if (t.getCuentaExterna() != null) {
            numeroCuentaDestino = t.getCuentaExterna();
            if (t.getBeneficiario() != null && !t.getBeneficiario().isBlank()) {
                nombreDestino = t.getBeneficiario();
            } else {
                nombreDestino = t.getIdBancoExterno() != null ? "Cliente " + t.getIdBancoExterno() : "Cuenta Externa";
            }
        }

        return TransaccionResponseDTO.builder()
                .idTransaccion(t.getIdTransaccion())
                .referencia(t.getReferencia())
                .codigoReferencia(t.getCodigoReferencia())
                .tipoOperacion(t.getTipoOperacion())
                .idCuentaOrigen(t.getIdCuentaOrigen())
                .idCuentaDestino(t.getIdCuentaDestino())
                .cuentaExterna(t.getCuentaExterna())
                .idBancoExterno(t.getIdBancoExterno())
                .monto(t.getMonto())
                .saldoResultante(saldoAMostrar)
                .fechaCreacion(t.getFechaCreacion())
                .descripcion(t.getDescripcion())
                .canal(t.getCanal())
                .estado(t.getEstado())
                .numeroCuentaOrigen(numeroCuentaOrigen)
                .nombreOrigen(nombreOrigen)
                .numeroCuentaDestino(numeroCuentaDestino)
                .nombreDestino(nombreDestino)
                .build();
    }

    private Map<String, String> obtenerInfoCuenta(Integer idCuenta) {
        Map<String, String> info = new java.util.HashMap<>();
        try {
            Map<String, Object> cuenta = cuentaCliente.obtenerCuenta(idCuenta);
            if (cuenta != null) {
                if (cuenta.get("numeroCuenta") != null) {
                    info.put("numeroCuenta", cuenta.get("numeroCuenta").toString());
                }
                if (cuenta.get("idCliente") != null) {
                    try {
                        Integer idCliente = Integer.valueOf(cuenta.get("idCliente").toString());
                        Map<String, Object> cliente = clienteClient.obtenerCliente(idCliente);
                        if (cliente != null) {
                            String nombre = "";
                            if (cliente.get("nombreCompleto") != null) {
                                nombre = cliente.get("nombreCompleto").toString();
                            } else if (cliente.get("nombres") != null) {
                                nombre = cliente.get("nombres").toString();
                                if (cliente.get("apellidos") != null) {
                                    nombre += " " + cliente.get("apellidos");
                                }
                            }
                            if (!nombre.trim().isEmpty()) {
                                info.put("nombreCliente", nombre.trim());
                            }
                        }
                    } catch (Exception ex) {
                    }
                }
            }
        } catch (Exception e) {
        }
        return info;
    }

    private String obtenerNumeroCuenta(Integer idCuenta) {
        try {
            Map<String, Object> cuenta = cuentaCliente.obtenerCuenta(idCuenta);
            if (cuenta != null && cuenta.get("numeroCuenta") != null) {
                return cuenta.get("numeroCuenta").toString();
            }
        } catch (Exception e) {
        }
        return String.valueOf(idCuenta);
    }

    private Integer obtenerIdCuentaPorNumero(String numeroCuenta) {
        try {
            Map<String, Object> cuenta = cuentaCliente.buscarPorNumero(numeroCuenta);
            if (cuenta != null) {
                if (cuenta.get("idCuenta") != null) {
                    return Integer.valueOf(cuenta.get("idCuenta").toString());
                } else if (cuenta.get("id") != null) {
                    return Integer.valueOf(cuenta.get("id").toString());
                }
            }
        } catch (Exception e) {
            log.warn("Error buscando cuenta por numero {}: {}", numeroCuenta, e.getMessage());
        }
        return null;
    }

    @Override
    @Transactional
    public void procesarTransferenciaEntrante(String instructionId, String cuentaDestino,
            BigDecimal monto, String bancoOrigen) {
        log.info("📥 Procesando transferencia entrante desde {} a cuenta {}, monto: {}",
                bancoOrigen, cuentaDestino, monto);

        Integer idCuentaDestino = obtenerIdCuentaPorNumero(cuentaDestino);

        if (idCuentaDestino == null) {
            throw new BusinessException("AC01", "AC01 - Número de cuenta incorrecto o inexistente en Banco Destino");
        }

        if (transaccionRepository.findByReferencia(instructionId).isPresent()) {
            return;
        }

        BigDecimal nuevoSaldo = procesarSaldo(idCuentaDestino, monto);

        Transaccion trx = Transaccion.builder()
                .referencia(instructionId)
                .tipoOperacion("TRANSFERENCIA_ENTRADA")
                .idCuentaDestino(idCuentaDestino)
                .idCuentaOrigen(null)
                .cuentaExterna(cuentaDestino)
                .monto(monto)
                .saldoResultante(nuevoSaldo)
                .idBancoExterno(bancoOrigen)
                .descripcion("Transferencia recibida desde " + bancoOrigen)
                .canal("SWITCH")
                .estado("COMPLETADA")
                .build();

        transaccionRepository.save(trx);
    }

    @Override
    @Transactional
    public void procesarDevolucionEntrante(SwitchRefundRequest request) {
        try {
            procesarDevolucionEntranteLogic(request);
            transaccionRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
        }
    }

    private void procesarDevolucionEntranteLogic(SwitchRefundRequest request) {
        if (request.getHeader().getOriginatingBankId() != null &&
                request.getHeader().getOriginatingBankId().equalsIgnoreCase(codigoBanco)) {
            return;
        }

        String originalId = request.getBody().getOriginalInstructionId();

        String returnId = request.getBody().getReturnInstructionId() != null
                ? request.getBody().getReturnInstructionId().trim()
                : null;
        String bancoOrigenRef = request.getHeader().getOriginatingBankId();

        if (transaccionRepository.findByReferencia(returnId).isPresent()) {
            return;
        }

        Transaccion originalTx = transaccionRepository.findPorReferenciaForUpdate(originalId)
                .orElse(null);

        if (originalTx == null) {
            throw new BusinessException("Transacción original no encontrada");
        }

        java.time.LocalDateTime fechaReverso = java.time.LocalDateTime.now();
        if (request.getHeader().getCreationDateTime() != null) {
            try {
                fechaReverso = java.time.OffsetDateTime.parse(request.getHeader().getCreationDateTime())
                        .toLocalDateTime();
            } catch (Exception e) {
            }
        }

        BigDecimal amount = request.getBody().getReturnAmount().getValue();

        if ("TRANSFERENCIA_SALIDA".equals(originalTx.getTipoOperacion())) {

            if ("REVERSADA".equals(originalTx.getEstado()) || "DEVUELTA".equals(originalTx.getEstado())) {
                return;
            }

            Integer idCuentaCliente = originalTx.getIdCuentaOrigen();
            BigDecimal nuevoSaldo = procesarSaldo(idCuentaCliente, amount);

            originalTx.setEstado("DEVUELTA");
            originalTx.setDescripcion(originalTx.getDescripcion() + " [DEVUELTA]");
            transaccionRepository.save(originalTx);

            Transaccion returnTx = Transaccion.builder()
                    .referencia(returnId)
                    .tipoOperacion("DEVOLUCION_RECIBIDA")
                    .monto(amount)
                    .idCuentaDestino(idCuentaCliente)
                    .saldoResultante(nuevoSaldo)
                    .descripcion("Devolución recibida: " + request.getBody().getReturnReason())
                    .canal("SWITCH")
                    .estado("COMPLETADA")
                    .idTransaccionReversa(originalTx.getIdTransaccion())
                    .idBancoExterno(bancoOrigenRef)
                    .fechaCreacion(fechaReverso)
                    .codigoMotivo(request.getBody().getReturnReason())
                    .build();

            transaccionRepository.save(returnTx);

        } else if ("TRANSFERENCIA_ENTRADA".equals(originalTx.getTipoOperacion())) {

            if ("REVERSADA".equals(originalTx.getEstado()) || "DEVUELTA".equals(originalTx.getEstado())) {
                return;
            }

            Integer idCuentaCliente = originalTx.getIdCuentaDestino();

            try {
                BigDecimal nuevoSaldo = procesarSaldo(idCuentaCliente, amount.negate());

                originalTx.setEstado("REVERSADA");
                originalTx.setDescripcion(originalTx.getDescripcion() + " [REVERSADA SOLICITUD EXT]");
                transaccionRepository.save(originalTx);

                Transaccion debitTx = Transaccion.builder()
                        .referencia(returnId)
                        .tipoOperacion("REVERSO_DEBITO")
                        .monto(amount)
                        .idCuentaOrigen(idCuentaCliente)
                        .saldoResultante(nuevoSaldo)
                        .descripcion("Reverso solicitado por banco origen: " + request.getBody().getReturnReason())
                        .canal("SWITCH")
                        .estado("COMPLETADA")
                        .idTransaccionReversa(originalTx.getIdTransaccion())
                        .idBancoExterno(bancoOrigenRef)
                        .fechaCreacion(fechaReverso)
                        .codigoMotivo(request.getBody().getReturnReason())
                        .build();

                transaccionRepository.save(debitTx);

            } catch (BusinessException e) {
                throw new BusinessException(
                        "No se puede ejecutar el reverso: Fondos insuficientes en la cuenta del cliente.");
            }

        } else {
        }
    }

    @Override
    @Transactional
    public void solicitarReverso(RefoundRequestDTO requestDTO) {

        Transaccion originalTx = null;

        if (requestDTO.getIdTransaccion() != null) {
            originalTx = transaccionRepository.findById(requestDTO.getIdTransaccion())
                    .orElse(null);
        }

        if (originalTx == null && requestDTO.getCodigoReferencia() != null) {
            originalTx = transaccionRepository
                    .findByCodigoReferencia(requestDTO.getCodigoReferencia())
                    .orElse(null);
        }

        if (originalTx == null) {
            throw new BusinessException(
                    "Transacción no encontrada. Debe proporcionar un ID válido o un Código de Referencia.");
        }

        if ("PENDIENTE".equals(originalTx.getEstado()) || "EN_PROCESO".equals(originalTx.getEstado())) {
            try {
                SwitchTransferResponse statusResp = switchClient
                        .consultarEstadoTransferencia(originalTx.getReferencia());
                if (statusResp != null && statusResp.getData() != null) {
                    String nuevoEstado = statusResp.getData().getEstado();

                    if ("COMPLETED".equalsIgnoreCase(nuevoEstado) || "EXITOSA".equalsIgnoreCase(nuevoEstado)) {
                        originalTx.setEstado("COMPLETADA");
                        transaccionRepository.save(originalTx);
                    } else if ("FAILED".equalsIgnoreCase(nuevoEstado) || "FALLIDA".equalsIgnoreCase(nuevoEstado)) {
                        originalTx.setEstado("FALLIDA");
                        originalTx.setDescripcion("FALLIDA (Sync): " + statusResp.getError().getMessage());
                        transaccionRepository.save(originalTx);
                        throw new BusinessException("La transacción falló en el Switch, no es necesario revertir.");
                    }
                }
            } catch (Exception e) {
            }
        }

        if (!"COMPLETADA".equals(originalTx.getEstado()) && !"EXITOSA".equals(originalTx.getEstado())) {
            throw new BusinessException(
                    "Solo se pueden revertir transacciones completadas. Estado actual: " + originalTx.getEstado());
        }

        if (originalTx.getIdTransaccionReversa() != null) {
            throw new BusinessException("Esta transacción ya fue revertida anteriormente.");
        }

        String messageId = UUID.randomUUID().toString();
        String creationTime = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                .format(java.time.format.DateTimeFormatter.ISO_INSTANT);
        String returnId = UUID.randomUUID().toString();

        SwitchRefundRequest switchRequest = SwitchRefundRequest.builder()
                .header(SwitchRefundRequest.Header.builder()
                        .messageId(messageId)
                        .creationDateTime(creationTime)
                        .originatingBankId(codigoBanco)
                        .build())
                .body(SwitchRefundRequest.Body.builder()
                        .returnInstructionId(returnId)
                        .originalInstructionId(originalTx.getReferencia())
                        .returnReason(requestDTO.getMotivo())
                        .returnAmount(SwitchRefundRequest.Amount.builder()
                                .currency("USD")
                                .value(originalTx.getMonto())
                                .build())

                        .build())
                .build();

        SwitchTransferResponse response = null;
        String motivoIso = mapearCodigoErrorInternalToISO(requestDTO.getMotivo());

        try {
            switchRequest.getBody().setReturnReason(motivoIso);
            response = switchClient.solicitarDevolucion(switchRequest);

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("409")
                    && errorMsg.contains("Transacción original no encontrada")) {
                response = SwitchTransferResponse.builder()
                        .success(true)
                        .build();
            } else {
                throw new BusinessException("Error de comunicación con el Switch: " + errorMsg);
            }
        }

        try {
            if (response != null && response.isSuccess()) {

                BigDecimal nuevoSaldo = procesarSaldo(originalTx.getIdCuentaOrigen(), originalTx.getMonto());

                originalTx.setEstado("REVERSADA");
                originalTx.setDescripcion(originalTx.getDescripcion() + " [REVERSADA: " + requestDTO.getMotivo() + "]");
                transaccionRepository.save(originalTx);

                Transaccion reversaTx = Transaccion.builder()
                        .referencia(returnId)
                        .tipoOperacion("DEVOLUCION_RECIBIDA")
                        .monto(originalTx.getMonto())
                        .descripcion(
                                "Devolución de Tx " + originalTx.getIdTransaccion() + ": " + requestDTO.getMotivo())
                        .canal("WEB")
                        .idCuentaDestino(originalTx.getIdCuentaOrigen())
                        .saldoResultante(nuevoSaldo)
                        .estado("COMPLETADA")
                        .idTransaccionReversa(originalTx.getIdTransaccion())
                        .codigoMotivo(motivoIso)
                        .build();

                try {
                    transaccionRepository.save(reversaTx);
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                }

            } else {
                String errorMsg = (response != null && response.getError() != null)
                        ? response.getError().getMessage()
                        : "Rechazo desconocido del Switch";
                throw new BusinessException("El Switch rechazó la devolución: " + errorMsg);
            }

        } catch (Exception e) {
            if (e instanceof BusinessException)
                throw e;
            throw new BusinessException("Error interno al procesar reverso: " + e.getMessage());
        }
    }

    @Override
    public java.util.List<java.util.Map<String, String>> obtenerMotivosDevolucion() {
        try {
            return switchClient.obtenerMotivosDevolucion();
        } catch (Exception e) {
            return java.util.List.of(
                    java.util.Map.of("codigo", "AC03", "descripcion", "Cuenta Inexistente (Invalid Creditor Account)"),
                    java.util.Map.of("codigo", "AC06", "descripcion", "Cuenta Bloqueada (Blocked Account)"),
                    java.util.Map.of("codigo", "AC04", "descripcion", "Cuenta Cerrada (Closed Account)"),
                    java.util.Map.of("codigo", "AM05", "descripcion", "Duplicidad (Duplication)"),
                    java.util.Map.of("codigo", "FRAD", "descripcion", "Fraude (Fraudulent Origin)"),
                    java.util.Map.of("codigo", "MS03", "descripcion", "Error Técnico (Not Specified Reason Agent)"),
                    java.util.Map.of("codigo", "AG01", "descripcion", "Operación Prohibida (Transaction Forbidden)"),
                    java.util.Map.of("codigo", "CUST", "descripcion",
                            "Solicitado por Cliente (Requested By Customer)"));
        }
    }

    private String mapearCodigoErrorInternalToISO(String codigoInterno) {
        if (codigoInterno == null)
            return "MS03";

        switch (codigoInterno.trim().toUpperCase()) {
            case "TECH":
                return "MS03";
            case "CUENTA_INVALIDA":
            case "CUENTA_INEXISTENTE":
                return "AC03";
            case "CUENTA_BLOQUEADA":
                return "AC06";
            case "CUENTA_CERRADA":
                return "AC04";
            case "SALDO_INSUFICIENTE":
                return "AM04"; // Mantenemos AM04 por compatibilidad interna, aunque no sea ISO switch return
            case "DUPLICADO":
                return "AM05";
            case "FRAUDE":
                return "FRAD";
            case "CLIENTE":
            case "SOLICITUD_CLIENTE":
                return "CUST";
            case "PROHIBIDO":
                return "AG01";
            default:
                return "MS03";
        }
    }

    @Override
    public com.arcbank.cbs.transaccion.dto.AccountLookupResponse validarCuentaExterna(String targetBankId,
            String targetAccountNumber) {
        log.info("Validando cuenta externa: Bank={}, Account={}", targetBankId, targetAccountNumber);

        boolean esBancoLocal = (codigoBanco != null && codigoBanco.equalsIgnoreCase(targetBankId));

        if (esBancoLocal) {
            return validarCuentaLocal(targetAccountNumber);
        }

        com.arcbank.cbs.transaccion.dto.AccountLookupRequest request = com.arcbank.cbs.transaccion.dto.AccountLookupRequest
                .builder()
                .header(com.arcbank.cbs.transaccion.dto.AccountLookupRequest.Header.builder()
                        .originatingBankId(codigoBanco)
                        .build())
                .body(com.arcbank.cbs.transaccion.dto.AccountLookupRequest.Body.builder()
                        .targetBankId(targetBankId)
                        .targetAccountNumber(targetAccountNumber)
                        .build())
                .build();

        try {
            return switchClient.lookupAccount(request);
        } catch (Throwable e) {
            log.error("Error validando cuenta externa: {}", e.getMessage());
            com.arcbank.cbs.transaccion.dto.AccountLookupResponse.Data data = new com.arcbank.cbs.transaccion.dto.AccountLookupResponse.Data();
            data.setExists(false);
            data.setMensaje("Error de comunicacion: " + e.getMessage());
            return new com.arcbank.cbs.transaccion.dto.AccountLookupResponse("FAILED", data);
        }
    }

    @Override
    public com.arcbank.cbs.transaccion.dto.AccountLookupResponse validarCuentaLocal(String numeroCuenta) {
        log.info("Validación Local Solicitada para cuenta: {}", numeroCuenta);

        com.arcbank.cbs.transaccion.dto.AccountLookupResponse.Data data = new com.arcbank.cbs.transaccion.dto.AccountLookupResponse.Data();

        try {
            Map<String, Object> cuenta = cuentaCliente.buscarPorNumero(numeroCuenta);

            if (cuenta != null && cuenta.get("idCuenta") != null) {
                data.setExists(true);
                data.setStatus("ACTIVE");
                data.setCurrency("USD");

                try {
                    Integer idCliente = Integer.valueOf(cuenta.get("idCliente").toString());
                    Map<String, Object> cliente = clienteClient.obtenerCliente(idCliente);

                    if (cliente != null) {
                        String nombre = "";

                        if (cliente.get("nombreCompleto") != null) {
                            nombre = cliente.get("nombreCompleto").toString();
                        } else if (cliente.get("nombres") != null || cliente.get("apellidos") != null) {
                            if (cliente.get("nombres") != null)
                                nombre += cliente.get("nombres");
                            if (cliente.get("apellidos") != null)
                                nombre += " " + cliente.get("apellidos");
                        } else if (cliente.get("nombre") != null) {
                            nombre = cliente.get("nombre").toString();
                        }

                        if (!nombre.trim().isEmpty()) {
                            data.setOwnerName(nombre.trim());
                        } else {
                            data.setOwnerName("Cliente BANTEC");
                        }
                    } else {
                        data.setOwnerName("Cliente BANTEC");
                    }
                } catch (Exception ex) {
                    data.setOwnerName("Cliente BANTEC");
                }

                return new com.arcbank.cbs.transaccion.dto.AccountLookupResponse("SUCCESS", data);

            } else {
                data.setExists(false);
                data.setMensaje("Cuenta no encontrada");
                return new com.arcbank.cbs.transaccion.dto.AccountLookupResponse("FAILED", data);
            }

        } catch (Exception e) {
            log.error("Error validando cuenta local {}: {}", numeroCuenta, e.getMessage());
            data.setExists(false);
            data.setMensaje("Error interno en validación local: " + e.getMessage());
            return new com.arcbank.cbs.transaccion.dto.AccountLookupResponse("FAILED", data);
        }
    }

    @Override
    @Transactional
    public String consultarEstadoTransferencia(String instructionId) {
        Transaccion tx = transaccionRepository.findByReferencia(instructionId).orElse(null);

        if (tx == null) {
            return "NOT_FOUND";
        }

        if ("PENDIENTE".equals(tx.getEstado())) {
            long minutesDiff = java.time.temporal.ChronoUnit.MINUTES.between(tx.getFechaCreacion(),
                    java.time.LocalDateTime.now());
            if (minutesDiff >= 3) {
                tx.setEstado("FALLIDA");
                tx.setDescripcion("RECHAZADA: Expiró tiempo de validación");
                transaccionRepository.save(tx);
                return "FAILED";
            }

            try {
                SwitchTransferResponse resp = switchClient.consultarEstadoTransferencia(instructionId);
                if (resp != null && resp.getData() != null) {
                    String estadoSwitch = resp.getData().getEstado();

                    if ("COMPLETED".equalsIgnoreCase(estadoSwitch) || "EXITOSA".equalsIgnoreCase(estadoSwitch)) {
                        tx.setEstado("COMPLETADA");
                        tx.setDescripcion(tx.getDescripcion().replace("En proceso de validación. Le notificaremos.",
                                "Transferencia Finalizada"));
                        transaccionRepository.save(tx);
                        return "COMPLETED";
                    }

                    if ("FAILED".equalsIgnoreCase(estadoSwitch) || "FALLIDA".equalsIgnoreCase(estadoSwitch)
                            || "RECHAZADA".equalsIgnoreCase(estadoSwitch)) {
                        tx.setEstado("FALLIDA");
                        String motivo = (resp.getError() != null) ? resp.getError().getMessage()
                                : "Fallo confirmado por Switch";
                        tx.setDescripcion("RECHAZADA: " + motivo);
                        transaccionRepository.save(tx);
                        return "FAILED";
                    }
                }
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

                if (errorMsg.contains("404") || errorMsg.contains("not found") ||
                        errorMsg.contains("500") || errorMsg.contains("502") || errorMsg.contains("503")
                        || errorMsg.contains("504") ||
                        errorMsg.contains("refused") || errorMsg.contains("time out")
                        || errorMsg.contains("timed out")) {

                    tx.setEstado("FALLIDA");
                    if (errorMsg.length() > 200)
                        errorMsg = errorMsg.substring(0, 200) + "...";
                    tx.setDescripcion("RECHAZADA: Problema de conexión (" + errorMsg + ")");
                    transaccionRepository.save(tx);
                    return "FAILED";
                }
            }
            return "PENDING";
        }

        String estado = tx.getEstado();
        if ("COMPLETADA".equals(estado) || "EXITOSA".equals(estado) || "DEVUELTA".equals(estado)) {
            return "COMPLETED";
        } else if ("FALLIDA".equals(estado) || "REVERSADA".equals(estado)) {
            return "FAILED";
        }

        return "PENDING";
    }

    @Override
    @Transactional
    public void procesarDeposito(String cuentaDestino, BigDecimal monto, String ordenante, String instructionId) {
        log.info("Procesando depósito entrante: {} a cuenta {}", monto, cuentaDestino);

        Integer idCuenta = obtenerIdCuentaPorNumero(cuentaDestino);

        if (idCuenta == null) {
            throw new BusinessException("AC03", "AC03 - Cuenta no existe");
        }

        BigDecimal nuevoSaldo = procesarSaldo(idCuenta, monto);

        Transaccion tx = Transaccion.builder()
                .referencia(instructionId)
                .tipoOperacion("TRANSFERENCIA_ENTRADA")
                .monto(monto)
                .idCuentaDestino(idCuenta)
                .saldoResultante(nuevoSaldo)
                .descripcion("Transferencia recibida de: " + ordenante)
                .canal("SWITCH")
                .estado("COMPLETADA")
                .fechaCreacion(java.time.LocalDateTime.now())
                .build();

        transaccionRepository.save(tx);
    }
}