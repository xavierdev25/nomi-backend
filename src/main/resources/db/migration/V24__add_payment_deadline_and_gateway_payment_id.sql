-- Plazo para pagar un pedido. Un pedido PENDIENTE sin pago aprobado después de esta hora se
-- cancela y su stock vuelve a estar disponible (antes retenía el stock indefinidamente).
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS pago_expira_en TIMESTAMP;

-- Los pedidos pendientes anteriores reciben el plazo por defecto (15 minutos desde su creación):
-- la mayoría caducará en la primera revisión, que es lo esperado para pedidos abandonados.
UPDATE orders
SET pago_expira_en = creado_en + INTERVAL '15 minutes'
WHERE status = 'PENDIENTE' AND pago_expira_en IS NULL;

CREATE INDEX IF NOT EXISTS idx_orders_pending_payment_deadline
    ON orders (pago_expira_en)
    WHERE status = 'PENDIENTE';

-- Id del pago de MercadoPago que decide el estado del registro. external_id guarda el de la
-- preferencia (el checkout), en el que puede haber varios intentos; sin este id no se podía
-- reembolsar ni distinguir un pago duplicado del bueno.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS gateway_payment_id VARCHAR(40);
