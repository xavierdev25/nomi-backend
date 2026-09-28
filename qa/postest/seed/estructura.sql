-- Seed de estructura del postest de Nomi (ver seed/DISENO_SEED.md).
--
-- DATOS SINTÉTICOS DE PRUEBA: no son información real del ISTPC ni del pretest.
-- Se carga solo en la base `nomi_postest`, después de registrar las cuentas por la API (las
-- contraseñas necesitan el hash de la aplicación). Es idempotente.

BEGIN;

-- Datos heredados de otro contexto (migración V14), que el flujo de compra no usa.
DELETE FROM puntos_entrega;
DELETE FROM campus_mapas;
DELETE FROM campus;
DELETE FROM universidades;

-- T1: único establecimiento de las observaciones oficiales.
INSERT INTO stores (nombre, descripcion, owner_role, owner_id, activo, horario_apertura, horario_cierre)
SELECT 'Quiosco ISTPC (entorno de prueba)',
       'Quiosco sintético para la evaluación controlada de Nomi. Datos de prueba.',
       'COMERCIO', u.id, true, '07:00', '19:00'
FROM users u
WHERE u.email = 'comercio.postest@nomi.test'
  AND NOT EXISTS (SELECT 1 FROM stores WHERE nombre = 'Quiosco ISTPC (entorno de prueba)');

-- T2 y T3: solo para la batería VAL.
INSERT INTO stores (nombre, descripcion, owner_role, owner_id, activo, horario_apertura, horario_cierre)
SELECT 'Cafetería Taller (prueba)', 'Tienda sintética para pruebas de validación.',
       'COMERCIO', u.id, true, '07:00', '19:00'
FROM users u
WHERE u.email = 'comercio2.postest@nomi.test'
  AND NOT EXISTS (SELECT 1 FROM stores WHERE nombre = 'Cafetería Taller (prueba)');

INSERT INTO stores (nombre, descripcion, owner_role, owner_id, activo, horario_apertura, horario_cierre)
SELECT 'Quiosco Cerrado (prueba)', 'Tienda sintética inactiva para pruebas de validación.',
       'COMERCIO', u.id, false, '07:00', '19:00'
FROM users u
WHERE u.email = 'comercio2.postest@nomi.test'
  AND NOT EXISTS (SELECT 1 FROM stores WHERE nombre = 'Quiosco Cerrado (prueba)');

UPDATE stores SET activo = true  WHERE nombre IN ('Quiosco ISTPC (entorno de prueba)', 'Cafetería Taller (prueba)');
UPDATE stores SET activo = false WHERE nombre = 'Quiosco Cerrado (prueba)';

-- Aulas sintéticas.
INSERT INTO aulas (codigo, nombre, piso, pabellon, activo)
SELECT v.codigo, v.nombre, v.piso, v.pabellon, true
FROM (VALUES
    ('PRB-A101', 'Aula 101 – Pabellón A (prueba)', '1', 'A'),
    ('PRB-A102', 'Aula 102 – Pabellón A (prueba)', '1', 'A'),
    ('PRB-B201', 'Aula 201 – Pabellón B (prueba)', '2', 'B'),
    ('PRB-B202', 'Aula 202 – Pabellón B (prueba)', '2', 'B'),
    ('PRB-LAB1', 'Laboratorio de Cómputo 1 (prueba)', '1', 'C'),
    ('PRB-TAL1', 'Taller de Electrónica (prueba)', '1', 'D')
) AS v(codigo, nombre, piso, pabellon)
WHERE NOT EXISTS (SELECT 1 FROM aulas a WHERE a.codigo = v.codigo);

-- Solo las aulas sintéticas quedan activas en la base del postest.
UPDATE aulas SET activo = (codigo LIKE 'PRB-%');

-- Productos de T2 y T3 para la batería VAL. Durante las jornadas están todos no publicados:
-- la búsqueda del sistema no filtra por tienda activa y aparecerían en los POST.
INSERT INTO products (nombre, descripcion, precio, stock, categoria, store_id, activo, disponible, etiquetas_dieteticas)
SELECT v.nombre, 'Producto sintético para pruebas de validación.', v.precio, v.stock, v.categoria,
       s.id, false, v.disponible, '{}'::text[]
FROM (VALUES
    ('Cafetería Taller (prueba)', 'Sándwich mixto (T2)',       5.00, 20, 'COMIDA', true),
    ('Cafetería Taller (prueba)', 'Refresco de maracuyá (T2)', 2.50, 20, 'BEBIDA', true),
    ('Cafetería Taller (prueba)', 'Keke de naranja (T2)',      2.00,  0, 'POSTRE', false),
    ('Cafetería Taller (prueba)', 'Queque de chocolate (T2)',  2.50, 15, 'POSTRE', false),
    ('Cafetería Taller (prueba)', 'Combo taller (T2)',         7.50, 10, 'COMIDA', true),
    ('Quiosco Cerrado (prueba)',  'Galletas de avena (T3)',    1.50, 20, 'SNACK',  true),
    ('Quiosco Cerrado (prueba)',  'Agua sin gas (T3)',         1.50, 20, 'BEBIDA', true)
) AS v(tienda, nombre, precio, stock, categoria, disponible)
JOIN stores s ON s.nombre = v.tienda
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.store_id = s.id AND p.nombre = v.nombre);

UPDATE products SET activo = false
WHERE store_id IN (SELECT id FROM stores WHERE nombre IN ('Cafetería Taller (prueba)', 'Quiosco Cerrado (prueba)'));

COMMIT;
