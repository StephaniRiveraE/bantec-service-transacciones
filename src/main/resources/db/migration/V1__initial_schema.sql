-- DATABASE: db_transacciones



DROP TABLE IF EXISTS public."Transaccion" CASCADE;



CREATE TABLE public."Transaccion" (

    "IdTransaccion"          SERIAL PRIMARY KEY,

   

    -- REFERENCIAS Y TRAZABILIDAD

    "Referencia"             VARCHAR(50) UNIQUE, -- Vital para Idempotencia del Switch

    "IdTransaccionReversa"   INTEGER NULL,       -- Solo lleno si TipoOperacion = 'REVERSO'



    -- CLASIFICACIÓN

    "TipoOperacion"          VARCHAR(30) NOT NULL CHECK ("TipoOperacion" IN (

                                'DEPOSITO',

                                'RETIRO',

                                'TRANSFERENCIA_INTERNA',

                                'TRANSFERENCIA_SALIDA',  -- Envío a otro banco

                                'TRANSFERENCIA_ENTRADA', -- Recibo de otro banco

                                'PAGO_SERVICIOS',

                                'REVERSO'

                             )),



    -- ACTORES DE LA TRANSACCIÓN

    -- 1. Origen Local (Quién paga/envía desde nuestro banco).

    --    NULL si es un Depósito en efectivo o Entrada del Switch.

    "IdCuentaOrigen"         INTEGER NULL,



    -- 2. Destino Local (Quién recibe en nuestro banco).

    --    NULL si es un Retiro o Salida a otro banco.

    "IdCuentaDestino"        INTEGER NULL,

    "SaldoResultanteDestino" NUMERIC(15,2) NULL, -- Para transferencias internas




    -- 3. Actores Externos (Solo para Interbancarias)

    "CuentaExterna"          VARCHAR(50) NULL, -- Puede ser origen o destino según el flujo

    "IdBancoExterno"         VARCHAR(20) NULL,     -- Cambiado de INTEGER a VARCHAR para soportar 'BANTEC', 'ECUSOL', etc.




    -- DATOS FINANCIEROS

    "Monto"                  NUMERIC(15,2) NOT NULL CHECK ("Monto" > 0),

    "SaldoResultante"        NUMERIC(15,2) NULL, -- Auditoría del saldo post-tx (Opcional pero recomendado)

    "FechaCreacion"          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    "Descripcion"            VARCHAR(255),



    -- CONTEXTO

    "Canal"                  VARCHAR(20) DEFAULT 'WEB'

                             CHECK ("Canal" IN ('WEB', 'MOVIL', 'VENTANILLA', 'SWITCH', 'ATM')),

   

    "IdSucursal"             INTEGER, -- Referencia lógica a MongoDB (Sin FK)



    "Estado"                 VARCHAR(20) DEFAULT 'COMPLETADA' NOT NULL

                             CHECK ("Estado" IN ('COMPLETADA', 'FALLIDA', 'REVERTIDA', 'PENDIENTE')),



    -- CONSTRAINTS RELACIONALES

    CONSTRAINT "FK_Transaccion_Reversa"

        FOREIGN KEY ("IdTransaccionReversa")

        REFERENCES public."Transaccion"("IdTransaccion"),



    -- =================================================================================

    -- REGLAS DE NEGOCIO (EL CORAZÓN DEL SISTEMA)

    -- =================================================================================

    CONSTRAINT "CK_Transaccion_LogicaFlujo" CHECK (

        -- CASO 1: SALIDA DE DINERO (Retiro o Envío a otro banco)

        -- Debe existir una Cuenta Origen Local que pague.

        (

            "TipoOperacion" IN ('RETIRO', 'TRANSFERENCIA_SALIDA')

            AND "IdCuentaOrigen" IS NOT NULL

        )

        OR

        -- CASO 2: ENTRADA DE DINERO (Depósito o Recepción de Switch)

        -- Debe existir una Cuenta Destino Local que reciba.

        (

            "TipoOperacion" IN ('DEPOSITO', 'TRANSFERENCIA_ENTRADA')

            AND "IdCuentaDestino" IS NOT NULL

        )

        OR

        -- CASO 3: TRANSFERENCIA INTERNA

        -- Deben existir ambas cuentas locales.

        (

            "TipoOperacion" = 'TRANSFERENCIA_INTERNA'

            AND "IdCuentaOrigen" IS NOT NULL

            AND "IdCuentaDestino" IS NOT NULL

        )

        OR

        -- CASO 4: REVERSO

        -- Debe apuntar a la transacción original.

        (

            "TipoOperacion" = 'REVERSO'

            AND "IdTransaccionReversa" IS NOT NULL

        )

    )

);



-- ÍNDICE ÚNICO PARA EVITAR DOBLE REVERSO

CREATE UNIQUE INDEX "UX_Transaccion_UnReversoPorOriginal"

ON public."Transaccion"("IdTransaccionReversa")

WHERE "IdTransaccionReversa" IS NOT NULL;

