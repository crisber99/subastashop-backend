-- 1. Renombrar tablas (SQL Server)
-- TicketRifa -> ParticipacionesConcurso
EXEC sp_rename 'TicketRifa', 'ParticipacionesConcurso';
-- GanadorRifa -> GanadoresConcurso
EXEC sp_rename 'GanadorRifa', 'GanadoresConcurso';

-- 2. Añadir columnas de tiempo y finalización
ALTER TABLE ParticipacionesConcurso ADD duration_ms BIGINT NULL;
ALTER TABLE ParticipacionesConcurso ADD completion_timestamp DATETIME2 NULL;

-- 3. (Opcional) Renombrar columnas para consistencia si es necesario
-- EXEC sp_rename 'ParticipacionesConcurso.numeroTicket', 'identificadorInscripcion', 'COLUMN';

-- 4. Actualizar estado de rifas activas si es necesario (limpieza)
UPDATE Productos SET estado = 'ACTIVO' WHERE tipo = 'RIFA' AND estado = 'PENDIENTE';
