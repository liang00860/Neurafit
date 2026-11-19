CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  phone VARCHAR(20) NULL,
  email VARCHAR(100) NULL,
  password_hash VARCHAR(255) NOT NULL,
  avatar_url VARCHAR(255) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  last_login_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_username (username),
  UNIQUE KEY uq_users_phone (phone),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_sessions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_sessions_token_hash (token_hash),
  KEY idx_user_sessions_user (user_id),
  KEY idx_user_sessions_expires_at (expires_at),
  CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_login_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  login_ip VARCHAR(45) NULL,
  device_info VARCHAR(255) NULL,
  login_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user_login_logs_user (user_id),
  CONSTRAINT fk_user_login_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_training_sessions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  client_training_id BIGINT NOT NULL,
  action_name VARCHAR(32) NOT NULL,
  training_started_at DATETIME NULL,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  rep_count INT NOT NULL DEFAULT 0,
  average_score INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uniq_user_training_client (user_id, client_training_id),
  KEY idx_training_sessions_user_id (user_id),
  KEY idx_training_sessions_started_at (training_started_at),
  CONSTRAINT fk_user_training_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_training_stats (
  user_id BIGINT UNSIGNED NOT NULL,
  total_duration_ms BIGINT NOT NULL DEFAULT 0,
  total_calories_kcal DECIMAL(12,1) NOT NULL DEFAULT 0.0,
  session_count INT NOT NULL DEFAULT 0,
  last_synced_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_training_stats_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_training_plans (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(180) NOT NULL,
  goal VARCHAR(120) NOT NULL,
  difficulty VARCHAR(64) NOT NULL,
  summary VARCHAR(1000) NOT NULL,
  source VARCHAR(80) NOT NULL DEFAULT 'ai',
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  starts_on DATE NOT NULL,
  ends_on DATE NOT NULL,
  adopted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user_training_plans_user_status (user_id, status, starts_on),
  CONSTRAINT fk_user_training_plans_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_training_plan_days (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plan_id BIGINT UNSIGNED NOT NULL,
  day_index INT NOT NULL,
  scheduled_date DATE NOT NULL,
  title VARCHAR(180) NOT NULL,
  focus VARCHAR(120) NOT NULL,
  note VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_training_plan_days_index (plan_id, day_index),
  KEY idx_user_training_plan_days_date (scheduled_date),
  CONSTRAINT fk_user_training_plan_days_plan FOREIGN KEY (plan_id) REFERENCES user_training_plans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_training_plan_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plan_day_id BIGINT UNSIGNED NOT NULL,
  sort_order INT NOT NULL,
  name VARCHAR(160) NOT NULL,
  sets INT NOT NULL DEFAULT 1,
  reps INT NULL,
  duration_minutes INT NULL,
  rest_seconds INT NOT NULL DEFAULT 60,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_training_plan_items_order (plan_day_id, sort_order),
  CONSTRAINT fk_user_training_plan_items_day FOREIGN KEY (plan_day_id) REFERENCES user_training_plan_days(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS profiles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  display_name VARCHAR(120) NOT NULL,
  gender VARCHAR(32) NULL,
  birthday DATE NULL,
  height_cm DECIMAL(5,2) NULL,
  weight_kg DECIMAL(5,2) NULL,
  fitness_goal VARCHAR(64) NULL,
  training_level VARCHAR(64) NULL,
  diet_preferences JSON NULL,
  privacy_settings JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS desktop_apps (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  app_key VARCHAR(64) NOT NULL,
  label VARCHAR(120) NOT NULL,
  icon VARCHAR(120) NULL,
  default_open TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_desktop_apps_app_key (app_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS desktop_windows (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  app_key VARCHAR(64) NOT NULL,
  is_open TINYINT(1) NOT NULL DEFAULT 0,
  minimized TINYINT(1) NOT NULL DEFAULT 0,
  maximized TINYINT(1) NOT NULL DEFAULT 0,
  x INT NOT NULL,
  y INT NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  z_index INT NOT NULL DEFAULT 1,
  restore_bounds JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_desktop_windows_profile_app (profile_id, app_key),
  CONSTRAINT fk_desktop_windows_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS desktop_dock_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  app_key VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL,
  pinned TINYINT(1) NOT NULL DEFAULT 1,
  show_indicator TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_desktop_dock_profile_app (profile_id, app_key),
  CONSTRAINT fk_desktop_dock_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_preferences (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  app_key VARCHAR(64) NOT NULL,
  preference_key VARCHAR(120) NOT NULL,
  preference_value JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_app_preferences_profile_key (profile_id, app_key, preference_key),
  CONSTRAINT fk_app_preferences_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS calendar_lists (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  client_id VARCHAR(80) NOT NULL,
  group_name VARCHAR(120) NOT NULL,
  name VARCHAR(160) NOT NULL,
  color VARCHAR(32) NOT NULL,
  visible TINYINT(1) NOT NULL DEFAULT 1,
  shared TINYINT(1) NOT NULL DEFAULT 0,
  subscribed TINYINT(1) NOT NULL DEFAULT 0,
  ignore_alerts TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_calendar_lists_profile_client (profile_id, client_id),
  CONSTRAINT fk_calendar_lists_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS calendar_events (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  calendar_list_id BIGINT UNSIGNED NOT NULL,
  client_id VARCHAR(80) NOT NULL,
  title VARCHAR(240) NOT NULL,
  start_at DATETIME NOT NULL,
  end_at DATETIME NOT NULL,
  all_day TINYINT(1) NOT NULL DEFAULT 0,
  color VARCHAR(32) NULL,
  location VARCHAR(255) NOT NULL DEFAULT '',
  meeting_link VARCHAR(255) NOT NULL DEFAULT '',
  notes TEXT NOT NULL,
  reminder TINYINT(1) NOT NULL DEFAULT 0,
  repeat_rule JSON NOT NULL,
  alert VARCHAR(120) NOT NULL DEFAULT 'None',
  second_alert VARCHAR(120) NOT NULL DEFAULT 'None',
  travel_time VARCHAR(120) NOT NULL DEFAULT '',
  time_zone VARCHAR(120) NOT NULL DEFAULT 'Pacific Time',
  availability VARCHAR(32) NOT NULL DEFAULT 'busy',
  is_private TINYINT(1) NOT NULL DEFAULT 0,
  url VARCHAR(255) NOT NULL DEFAULT '',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_calendar_events_profile_client (profile_id, client_id),
  KEY idx_calendar_events_range (profile_id, start_at, end_at),
  CONSTRAINT fk_calendar_events_profile FOREIGN KEY (profile_id) REFERENCES profiles(id),
  CONSTRAINT fk_calendar_events_list FOREIGN KEY (calendar_list_id) REFERENCES calendar_lists(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS calendar_event_invitees (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_id BIGINT UNSIGNED NOT NULL,
  email VARCHAR(180) NOT NULL,
  name VARCHAR(160) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_calendar_invitees_event FOREIGN KEY (event_id) REFERENCES calendar_events(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS calendar_event_attachments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(255) NOT NULL,
  file_url VARCHAR(500) NOT NULL DEFAULT '',
  file_type VARCHAR(80) NOT NULL DEFAULT '',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_calendar_attachments_event FOREIGN KEY (event_id) REFERENCES calendar_events(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS calendar_event_exceptions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_id BIGINT UNSIGNED NOT NULL,
  exception_date DATE NOT NULL,
  override_event_id BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_calendar_exception_date (event_id, exception_date),
  CONSTRAINT fk_calendar_exceptions_event FOREIGN KEY (event_id) REFERENCES calendar_events(id) ON DELETE CASCADE,
  CONSTRAINT fk_calendar_exceptions_override FOREIGN KEY (override_event_id) REFERENCES calendar_events(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS exercise_actions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  action_key VARCHAR(80) NOT NULL,
  name VARCHAR(160) NOT NULL,
  category VARCHAR(80) NOT NULL,
  difficulty VARCHAR(64) NOT NULL,
  target_muscles JSON NOT NULL,
  standard_points JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_exercise_actions_key (action_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS exercise_action_mistakes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  action_id BIGINT UNSIGNED NOT NULL,
  mistake_key VARCHAR(80) NOT NULL,
  title VARCHAR(180) NOT NULL,
  correction TEXT NOT NULL,
  severity VARCHAR(32) NOT NULL DEFAULT 'warning',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_action_mistake_key (action_id, mistake_key),
  CONSTRAINT fk_action_mistakes_action FOREIGN KEY (action_id) REFERENCES exercise_actions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS devices (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  device_key VARCHAR(120) NOT NULL,
  device_name VARCHAR(180) NOT NULL,
  platform VARCHAR(80) NOT NULL,
  last_seen_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_devices_profile_key (profile_id, device_key),
  CONSTRAINT fk_devices_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS training_sessions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  action_id BIGINT UNSIGNED NULL,
  device_id BIGINT UNSIGNED NULL,
  source VARCHAR(80) NOT NULL DEFAULT 'web-demo',
  started_at DATETIME(3) NOT NULL,
  ended_at DATETIME(3) NULL,
  duration_seconds INT NOT NULL DEFAULT 0,
  calories DECIMAL(8,2) NOT NULL DEFAULT 0,
  total_score DECIMAL(5,2) NULL,
  summary JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_training_sessions_profile_started (profile_id, started_at),
  CONSTRAINT fk_training_sessions_profile FOREIGN KEY (profile_id) REFERENCES profiles(id),
  CONSTRAINT fk_training_sessions_action FOREIGN KEY (action_id) REFERENCES exercise_actions(id),
  CONSTRAINT fk_training_sessions_device FOREIGN KEY (device_id) REFERENCES devices(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS training_sets (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id BIGINT UNSIGNED NOT NULL,
  set_index INT NOT NULL,
  reps INT NOT NULL DEFAULT 0,
  duration_seconds INT NOT NULL DEFAULT 0,
  completion_rate DECIMAL(5,2) NULL,
  average_score DECIMAL(5,2) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_training_sets_session_index (session_id, set_index),
  CONSTRAINT fk_training_sets_session FOREIGN KEY (session_id) REFERENCES training_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pose_frames (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id BIGINT UNSIGNED NOT NULL,
  frame_timestamp DATETIME(3) NOT NULL,
  landmarks JSON NOT NULL,
  connections JSON NOT NULL,
  angles JSON NOT NULL,
  confidence DECIMAL(5,4) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_pose_frames_session_time (session_id, frame_timestamp),
  CONSTRAINT fk_pose_frames_session FOREIGN KEY (session_id) REFERENCES training_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS motion_corrections (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id BIGINT UNSIGNED NOT NULL,
  pose_frame_id BIGINT UNSIGNED NULL,
  correction_type VARCHAR(120) NOT NULL,
  severity VARCHAR(32) NOT NULL,
  message TEXT NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_motion_corrections_session (session_id, occurred_at),
  CONSTRAINT fk_motion_corrections_session FOREIGN KEY (session_id) REFERENCES training_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_motion_corrections_frame FOREIGN KEY (pose_frame_id) REFERENCES pose_frames(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS webrtc_sessions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  room_id VARCHAR(120) NOT NULL,
  receiver_device_id BIGINT UNSIGNED NULL,
  sender_device_id BIGINT UNSIGNED NULL,
  status VARCHAR(48) NOT NULL,
  started_at DATETIME(3) NULL,
  ended_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_webrtc_sessions_room (room_id),
  CONSTRAINT fk_webrtc_sessions_profile FOREIGN KEY (profile_id) REFERENCES profiles(id),
  CONSTRAINT fk_webrtc_sessions_receiver FOREIGN KEY (receiver_device_id) REFERENCES devices(id),
  CONSTRAINT fk_webrtc_sessions_sender FOREIGN KEY (sender_device_id) REFERENCES devices(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS webrtc_signal_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  webrtc_session_id BIGINT UNSIGNED NULL,
  room_id VARCHAR(120) NOT NULL,
  signal_type VARCHAR(48) NOT NULL,
  signal_summary JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_signal_logs_room (room_id, created_at),
  CONSTRAINT fk_signal_logs_session FOREIGN KEY (webrtc_session_id) REFERENCES webrtc_sessions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS weather_locations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(160) NOT NULL,
  latitude DECIMAL(10,7) NULL,
  longitude DECIMAL(10,7) NULL,
  is_default TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_weather_locations_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS weather_snapshots (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  location_id BIGINT UNSIGNED NOT NULL,
  forecast_at DATETIME(3) NOT NULL,
  temperature_c DECIMAL(5,2) NULL,
  humidity INT NULL,
  wind_speed_kmh DECIMAL(7,2) NULL,
  condition_text VARCHAR(120) NOT NULL,
  payload JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_weather_snapshots_location_time (location_id, forecast_at),
  CONSTRAINT fk_weather_snapshots_location FOREIGN KEY (location_id) REFERENCES weather_locations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS training_plans (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(180) NOT NULL,
  goal VARCHAR(80) NOT NULL,
  difficulty VARCHAR(64) NOT NULL,
  source VARCHAR(80) NOT NULL DEFAULT 'system',
  starts_on DATE NULL,
  ends_on DATE NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_training_plans_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS training_plan_days (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plan_id BIGINT UNSIGNED NOT NULL,
  day_index INT NOT NULL,
  scheduled_date DATE NULL,
  title VARCHAR(180) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_training_plan_days_plan FOREIGN KEY (plan_id) REFERENCES training_plans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS training_plan_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plan_day_id BIGINT UNSIGNED NOT NULL,
  action_id BIGINT UNSIGNED NULL,
  sort_order INT NOT NULL,
  sets INT NOT NULL DEFAULT 1,
  reps INT NULL,
  duration_seconds INT NULL,
  rest_seconds INT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_training_plan_items_day FOREIGN KEY (plan_day_id) REFERENCES training_plan_days(id) ON DELETE CASCADE,
  CONSTRAINT fk_training_plan_items_action FOREIGN KEY (action_id) REFERENCES exercise_actions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS recipes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title VARCHAR(180) NOT NULL,
  ingredients JSON NOT NULL,
  calories DECIMAL(8,2) NULL,
  protein_g DECIMAL(8,2) NULL,
  carbs_g DECIMAL(8,2) NULL,
  fat_g DECIMAL(8,2) NULL,
  budget_level VARCHAR(48) NULL,
  tags JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS meal_plans (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  planned_on DATE NOT NULL,
  meal_type VARCHAR(48) NOT NULL,
  recipe_id BIGINT UNSIGNED NULL,
  notes TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_meal_plans_profile FOREIGN KEY (profile_id) REFERENCES profiles(id),
  CONSTRAINT fk_meal_plans_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS meal_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  eaten_at DATETIME(3) NOT NULL,
  meal_type VARCHAR(48) NOT NULL,
  title VARCHAR(180) NOT NULL,
  calories DECIMAL(8,2) NULL,
  nutrition JSON NULL,
  notes TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_meal_logs_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS food_recognition_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  meal_log_id BIGINT UNSIGNED NULL,
  image_url VARCHAR(500) NOT NULL DEFAULT '',
  recognized_items JSON NOT NULL,
  confidence DECIMAL(5,4) NULL,
  nutrition_estimate JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_food_records_profile FOREIGN KEY (profile_id) REFERENCES profiles(id),
  CONSTRAINT fk_food_records_meal FOREIGN KEY (meal_log_id) REFERENCES meal_logs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS water_goals (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  goal_date DATE NOT NULL,
  target_ml INT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_water_goals_profile_date (profile_id, goal_date),
  CONSTRAINT fk_water_goals_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS water_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  drank_at DATETIME(3) NOT NULL,
  amount_ml INT NOT NULL,
  source VARCHAR(80) NOT NULL DEFAULT 'manual',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_water_logs_profile_time (profile_id, drank_at),
  CONSTRAINT fk_water_logs_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS daily_health_summaries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  summary_date DATE NOT NULL,
  training_minutes INT NOT NULL DEFAULT 0,
  calories_burned DECIMAL(8,2) NOT NULL DEFAULT 0,
  calories_intake DECIMAL(8,2) NOT NULL DEFAULT 0,
  water_ml INT NOT NULL DEFAULT 0,
  health_score DECIMAL(5,2) NULL,
  summary_payload JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_daily_summary_profile_date (profile_id, summary_date),
  CONSTRAINT fk_daily_summaries_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS weekly_reports (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  profile_id BIGINT UNSIGNED NOT NULL,
  week_start DATE NOT NULL,
  week_end DATE NOT NULL,
  training_trend JSON NULL,
  diet_trend JSON NULL,
  error_stats JSON NULL,
  advice JSON NULL,
  report_payload JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_weekly_reports_profile_week (profile_id, week_start),
  CONSTRAINT fk_weekly_reports_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO profiles (id, display_name, fitness_goal, training_level, diet_preferences, privacy_settings)
VALUES (1, 'Local User', 'health', 'beginner', JSON_ARRAY(), JSON_OBJECT('camera', true, 'healthData', true))
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO desktop_apps (app_key, label, icon, default_open)
VALUES
  ('calendar', 'Calendar', 'CalendarDays', 0),
  ('motion', 'Motion', 'Dumbbell', 0),
  ('weather', 'Weather', 'CloudSun', 0)
ON DUPLICATE KEY UPDATE label = VALUES(label), icon = VALUES(icon), default_open = VALUES(default_open);

INSERT INTO desktop_windows (profile_id, app_key, is_open, minimized, maximized, x, y, width, height, z_index)
VALUES
  (1, 'calendar', 1, 0, 0, 128, 76, 1344, 792, 9),
  (1, 'motion', 0, 0, 0, 220, 92, 1160, 760, 8),
  (1, 'weather', 0, 0, 0, 110, 58, 1380, 830, 7)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO desktop_dock_items (profile_id, app_key, sort_order, pinned, show_indicator)
VALUES
  (1, 'calendar', 1, 1, 0),
  (1, 'weather', 2, 1, 0),
  (1, 'motion', 3, 1, 0)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), pinned = VALUES(pinned), updated_at = CURRENT_TIMESTAMP(3);

DELETE FROM desktop_dock_items
WHERE profile_id = 1 AND app_key IN ('messages', 'safari', 'mail');

DELETE FROM desktop_windows
WHERE profile_id = 1 AND app_key IN ('messages', 'safari', 'mail');

DELETE FROM desktop_apps
WHERE app_key IN ('messages', 'safari', 'mail');

UPDATE desktop_windows
SET is_open = 1,
    minimized = 0,
    maximized = 0,
    z_index = 9,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE profile_id = 1
  AND app_key = 'calendar'
  AND NOT EXISTS (
    SELECT 1
    FROM (
      SELECT id
      FROM desktop_windows
      WHERE profile_id = 1
        AND app_key IN ('calendar', 'weather', 'motion')
        AND is_open = 1
        AND minimized = 0
    ) AS visible_windows
  );

INSERT INTO calendar_lists
  (profile_id, client_id, group_name, name, color, visible, shared, subscribed, ignore_alerts)
VALUES
  (1, 'home', 'iCloud', 'Home', '#ff3b30', 1, 0, 0, 0),
  (1, 'personal', 'iCloud', 'Personal', '#007aff', 1, 0, 0, 0),
  (1, 'family', 'iCloud', 'Family', '#34c759', 1, 1, 0, 0),
  (1, 'product', 'Work', 'Product Team', '#ff9500', 1, 1, 0, 0),
  (1, 'launches', 'Work', 'Launches', '#af52de', 1, 0, 0, 0),
  (1, 'focus', 'Work', 'Focus Blocks', '#5ac8fa', 0, 0, 0, 0),
  (1, 'holidays', 'Other', 'US Holidays', '#ffcc00', 1, 0, 1, 0),
  (1, 'birthdays', 'Other', 'Birthdays', '#ff2d55', 1, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  group_name = VALUES(group_name),
  name = VALUES(name),
  color = VALUES(color),
  visible = VALUES(visible),
  shared = VALUES(shared),
  subscribed = VALUES(subscribed),
  ignore_alerts = VALUES(ignore_alerts),
  deleted_at = NULL,
  updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO calendar_events
  (profile_id, calendar_list_id, client_id, title, start_at, end_at, all_day, color, location,
   meeting_link, notes, reminder, repeat_rule, alert, second_alert, travel_time, time_zone,
   availability, is_private, url)
VALUES
  (1, (SELECT id FROM calendar_lists WHERE profile_id = 1 AND client_id = 'personal'), 'standup',
   'Daily standup', '2026-06-02 09:30:00', '2026-06-02 10:30:00', 0, NULL, '', '',
   '', 0, JSON_OBJECT('endMode', 'never', 'frequency', 'none', 'interval', 1, 'excludedDates', JSON_ARRAY()),
   '10 minutes before', 'Time to leave', '25 minutes', 'Pacific Time', 'busy', 0, ''),
  (1, (SELECT id FROM calendar_lists WHERE profile_id = 1 AND client_id = 'launches'), 'design-review',
   'Design review', '2026-06-04 11:00:00', '2026-06-04 12:00:00', 0, NULL, '', '',
   '', 0, JSON_OBJECT('endMode', 'never', 'frequency', 'none', 'interval', 1, 'excludedDates', JSON_ARRAY()),
   '10 minutes before', 'Time to leave', '25 minutes', 'Pacific Time', 'busy', 0, ''),
  (1, (SELECT id FROM calendar_lists WHERE profile_id = 1 AND client_id = 'family'), 'family-dinner',
   'Family dinner', '2026-06-06 19:00:00', '2026-06-06 20:00:00', 0, NULL, '', '',
   '', 0, JSON_OBJECT('endMode', 'never', 'frequency', 'none', 'interval', 1, 'excludedDates', JSON_ARRAY()),
   '10 minutes before', 'Time to leave', '25 minutes', 'Pacific Time', 'busy', 0, ''),
  (1, (SELECT id FROM calendar_lists WHERE profile_id = 1 AND client_id = 'product'), 'sprint-planning',
   'Sprint planning', '2026-06-09 10:00:00', '2026-06-09 11:00:00', 0, NULL, '', '',
   '', 0, JSON_OBJECT('endMode', 'never', 'frequency', 'weekly', 'interval', 1, 'weekdays', JSON_ARRAY(2), 'excludedDates', JSON_ARRAY()),
   '10 minutes before', 'Time to leave', '25 minutes', 'Pacific Time', 'busy', 0, ''),
  (1, (SELECT id FROM calendar_lists WHERE profile_id = 1 AND client_id = 'personal'), 'quarterly-planning',
   'Quarterly planning', '2026-06-18 09:00:00', '2026-06-18 10:00:00', 0, NULL, '1 Infinite Loop, Cupertino',
   'https://meet.example.com/quarterly', 'Review roadmap, launch risks, and staffing needs.', 0,
   JSON_OBJECT('endMode', 'never', 'frequency', 'weekly', 'interval', 1, 'weekdays', JSON_ARRAY(4), 'excludedDates', JSON_ARRAY()),
   '10 minutes before', 'Time to leave', '25 minutes', 'Pacific Time', 'busy', 0, 'https://roadmap.example.com'),
  (1, (SELECT id FROM calendar_lists WHERE profile_id = 1 AND client_id = 'birthdays'), 'dad-birthday',
   'Dad birthday', '2026-06-21 00:00:00', '2026-06-22 00:00:00', 1, NULL, '', '',
   '', 0, JSON_OBJECT('endMode', 'never', 'frequency', 'none', 'interval', 1, 'excludedDates', JSON_ARRAY()),
   '10 minutes before', 'Time to leave', '25 minutes', 'Pacific Time', 'busy', 0, '')
ON DUPLICATE KEY UPDATE
  calendar_list_id = VALUES(calendar_list_id),
  title = VALUES(title),
  start_at = VALUES(start_at),
  end_at = VALUES(end_at),
  all_day = VALUES(all_day),
  location = VALUES(location),
  meeting_link = VALUES(meeting_link),
  notes = VALUES(notes),
  repeat_rule = VALUES(repeat_rule),
  deleted_at = NULL,
  updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO weather_locations (id, profile_id, name, latitude, longitude, is_default)
VALUES
  (1, 1, 'My Location', NULL, NULL, 1),
  (2, 1, 'Cupertino', 37.3230, -122.0322, 0),
  (3, 1, 'New York', 40.7128, -74.0060, 0),
  (4, 1, 'Tokyo', 35.6762, 139.6503, 0),
  (5, 1, 'London', 51.5072, -0.1276, 0)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  latitude = VALUES(latitude),
  longitude = VALUES(longitude),
  is_default = VALUES(is_default),
  deleted_at = NULL,
  updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO exercise_actions (action_key, name, category, difficulty, target_muscles, standard_points)
VALUES (
  'squat',
  'Squat',
  'Lower Body',
  'basic',
  JSON_ARRAY('glutes', 'quads', 'core'),
  JSON_ARRAY('Knees follow toes', 'Back stays neutral', 'Hips move back before knees bend')
)
ON DUPLICATE KEY UPDATE name = VALUES(name), updated_at = CURRENT_TIMESTAMP(3);
