-- Restricciones alimentarias para las que el comercio declara apto cada producto.
-- Sin etiquetas, un producto no es apto para ninguna restricción: las recomendaciones de un
-- estudiante con restricciones solo incluyen productos etiquetados.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS etiquetas_dieteticas TEXT[] NOT NULL DEFAULT '{}';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_products_etiquetas_dieteticas'
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_etiquetas_dieteticas
            CHECK (etiquetas_dieteticas <@ ARRAY['VEGETARIANO', 'VEGANO', 'SIN_GLUTEN', 'SIN_LACTOSA']::TEXT[]);
    END IF;
END $$;
