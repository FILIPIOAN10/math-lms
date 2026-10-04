-- Accounts the Selenium E2E suite logs in with (password of all = Admin123!). TEST DATA ONLY:
-- never load this into a real production database. Idempotent.
INSERT INTO users (email, full_name, role, password, email_verified, status, requested_role) VALUES
 ('admin@mathlms.local',        'Prof Admin',    'ADMIN',   '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'ACTIVE', NULL),
 ('parinte@mathlms.local',      'Maria Parinte', 'PARENT',  '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'ACTIVE', NULL),
 ('student.activ@mathlms.local','Ana Student',   'STUDENT', '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'ACTIVE', NULL),
 ('student.nou@mathlms.local',  'Radu Nou',       NULL,     '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'PENDING_APPROVAL', 'STUDENT')
ON CONFLICT (email) DO NOTHING;
