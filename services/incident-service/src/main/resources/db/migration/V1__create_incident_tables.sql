CREATE TABLE incidents (
  id                   uuid PRIMARY KEY,
  triggering_event_id  uuid           NOT NULL,
  device_id            varchar(100)   NOT NULL,
  metric               varchar(30)    NOT NULL,
  severity             varchar(20)    NOT NULL,
  status               varchar(30)    NOT NULL,
  reason               varchar(50)    NOT NULL,
  measured_value       numeric(10, 2) NOT NULL,
  threshold            numeric(10, 2) NOT NULL,
  violation_started_at timestamptz    NOT NULL,
  detected_at          timestamptz    NOT NULL,
  resolved_at          timestamptz,
  version              bigint         NOT NULL,
  created_at           timestamptz    NOT NULL,
  updated_at           timestamptz    NOT NULL,
  CONSTRAINT uq_incidents_triggering_event UNIQUE (triggering_event_id)
);

CREATE INDEX idx_incidents_device_id ON incidents (device_id);
CREATE INDEX idx_incidents_status ON incidents (status);

CREATE TABLE incident_history (
  id          uuid PRIMARY KEY,
  incident_id uuid          NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
  action      varchar(30)   NOT NULL,
  note        varchar(1000),
  occurred_at timestamptz   NOT NULL
);

CREATE INDEX idx_history_incident_id ON incident_history (incident_id);

CREATE TABLE processed_events (
  event_id     uuid PRIMARY KEY,
  processed_at timestamptz NOT NULL
);
