-- La plataforma pasó a llamarse Nomi: la comisión de la plataforma se renombra.
-- V12 creó la columna con el nombre anterior y, como toda migración aplicada, no se edita.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'orders' AND column_name = 'comision_foodv'
    ) THEN
        ALTER TABLE orders RENAME COLUMN comision_foodv TO comision_nomi;
    END IF;
END $$;
