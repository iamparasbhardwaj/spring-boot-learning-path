INSERT INTO project (id, name) VALUES (1, 'Interview Prep');
INSERT INTO project (id, name) VALUES (2, 'Side Project');

INSERT INTO task (id, title, description, status, due_date, project_id, created_at) VALUES
 (1, 'Learn auto-configuration', 'Read the --debug report', 'IN_PROGRESS', '2026-08-18', 1, CURRENT_TIMESTAMP),
 (2, 'Fix the N+1',              'Use a fetch join',        'TODO',        '2026-08-18', 1, CURRENT_TIMESTAMP),
 (3, 'Write slice tests',        '@WebMvcTest + @DataJpaTest','TODO',      '2026-08-19', 1, CURRENT_TIMESTAMP),
 (4, 'Deploy to Fly.io',         null,                      'DONE',        '2026-08-01', 2, CURRENT_TIMESTAMP);
