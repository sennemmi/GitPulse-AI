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

CREATE TABLE IF NOT EXISTS radar_watch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    repo_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    template_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_time DATETIME(6),
    updated_time DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_radar_watch_repo_name (repo_name),
    KEY idx_radar_watch_enabled (enabled),
    KEY idx_radar_watch_updated_time (updated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS radar_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    watch_id BIGINT NOT NULL,
    repo_name VARCHAR(255) NOT NULL,
    score INT,
    decision VARCHAR(32),
    summary TEXT,
    source_revision VARCHAR(128),
    metrics_json TEXT NOT NULL,
    criteria_scores_json TEXT NOT NULL,
    evidence_json TEXT NOT NULL,
    created_time DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_radar_snapshot_watch_time (watch_id, created_time),
    KEY idx_radar_snapshot_repo_time (repo_name, created_time),
    CONSTRAINT fk_radar_snapshot_watch
        FOREIGN KEY (watch_id) REFERENCES radar_watch (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
