-- V3: Fix CHECK constraints to match Java code values
-- Estado: add REVERSADA, DEVUELTA (Java uses these instead of REVERTIDA)
-- TipoOperacion: add DEVOLUCION_RECIBIDA, REVERSO_DEBITO for return flows

-- 1. Drop and recreate Estado CHECK
ALTER TABLE public."Transaccion" DROP CONSTRAINT IF EXISTS "Transaccion_Estado_check";
ALTER TABLE public."Transaccion"
    ADD CONSTRAINT "Transaccion_Estado_check"
    CHECK ("Estado" IN ('COMPLETADA', 'FALLIDA', 'REVERTIDA', 'REVERSADA', 'DEVUELTA', 'PENDIENTE'));

-- 2. Drop and recreate TipoOperacion CHECK
ALTER TABLE public."Transaccion" DROP CONSTRAINT IF EXISTS "Transaccion_TipoOperacion_check";
ALTER TABLE public."Transaccion"
    ADD CONSTRAINT "Transaccion_TipoOperacion_check"
    CHECK ("TipoOperacion" IN (
        'DEPOSITO', 'RETIRO', 'TRANSFERENCIA_INTERNA',
        'TRANSFERENCIA_SALIDA', 'TRANSFERENCIA_ENTRADA',
        'PAGO_SERVICIOS', 'REVERSO',
        'DEVOLUCION_RECIBIDA', 'REVERSO_DEBITO'
    ));

-- 3. Drop and recreate LogicaFlujo CHECK to handle new types
ALTER TABLE public."Transaccion" DROP CONSTRAINT IF EXISTS "CK_Transaccion_LogicaFlujo";
ALTER TABLE public."Transaccion"
    ADD CONSTRAINT "CK_Transaccion_LogicaFlujo" CHECK (
        (
            "TipoOperacion" IN ('RETIRO', 'TRANSFERENCIA_SALIDA')
            AND "IdCuentaOrigen" IS NOT NULL
        )
        OR
        (
            "TipoOperacion" IN ('DEPOSITO', 'TRANSFERENCIA_ENTRADA')
            AND "IdCuentaDestino" IS NOT NULL
        )
        OR
        (
            "TipoOperacion" = 'TRANSFERENCIA_INTERNA'
            AND "IdCuentaOrigen" IS NOT NULL
            AND "IdCuentaDestino" IS NOT NULL
        )
        OR
        (
            "TipoOperacion" = 'REVERSO'
            AND "IdTransaccionReversa" IS NOT NULL
        )
        OR
        (
            "TipoOperacion" IN ('DEVOLUCION_RECIBIDA', 'REVERSO_DEBITO')
        )
    );
