-- Referencia única de cada checkout en MercadoPago (nomi-<pedido>-<uuid>). Hasta ahora era el id del
-- pedido, que se repite si la base se reinicia u otro entorno comparte la cuenta de MercadoPago: un
-- pago antiguo con la misma referencia daba por pagado un pedido que nadie pagó. Los pagos
-- existentes quedan en NULL y se buscan por el id del pedido, con controles de fecha y monto.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS external_reference VARCHAR(80);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_external_reference
    ON payments (external_reference)
    WHERE external_reference IS NOT NULL;
