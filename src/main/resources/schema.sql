CREATE TABLE IF NOT EXISTS task_record (
    task_id VARCHAR(64) NOT NULL,
    intent VARCHAR(64),
    repo_hint VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    result_data TEXT,
    error_msg TEXT,
    user_message TEXT,
    create_time DATETIME(6),
    update_time DATETIME(6),
    PRIMARY KEY (task_id),
    KEY idx_task_status (status),
    KEY idx_task_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS repo_analysis_record (
    repo_name VARCHAR(255) NOT NULL,
    analysis_json TEXT NOT NULL,
    PRIMARY KEY (repo_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
