CREATE TABLE telemetry_readings (
  id              uuid PRIMARY KEY,
  event_id        uuid           NOT NULL,
  device_id       varchar(100)   NOT NULL,
  temperature     numeric(10, 2) NOT NULL,
  humidity        numeric(10, 2) NOT NULL,
  operating_state varchar(30)    NOT NULL,
  occurred_at     timestamptz    NOT NULL,
  recorded_at     timestamptz    NOT NULL,
  correlation_id  uuid           NOT NULL,
  CONSTRAINT uq_readings_event_id UNIQUE (event_id)
);

CREATE INDEX idx_readings_device_occurred ON telemetry_readings (device_id, occurred_at);
