CREATE TABLE locations (
  id          uuid PRIMARY KEY,
  name        varchar(100) NOT NULL,
  description varchar(500),
  created_at  timestamptz  NOT NULL,
  updated_at  timestamptz  NOT NULL,
  CONSTRAINT uq_locations_name UNIQUE (name)
);

CREATE TABLE devices (
  id          uuid PRIMARY KEY,
  device_id   varchar(100) NOT NULL,
  name        varchar(200) NOT NULL,
  location_id uuid REFERENCES locations (id),
  created_at  timestamptz  NOT NULL,
  updated_at  timestamptz  NOT NULL,
  CONSTRAINT uq_devices_device_id UNIQUE (device_id)
);

CREATE INDEX idx_devices_location_id ON devices (location_id);

CREATE TABLE monitoring_policies (
  id                         uuid PRIMARY KEY,
  device_id                  uuid           NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
  metric                     varchar(30)    NOT NULL,
  minimum                    numeric(10, 2) NOT NULL,
  maximum                    numeric(10, 2) NOT NULL,
  violation_duration_seconds integer        NOT NULL,
  severity                   varchar(20)    NOT NULL,
  created_at                 timestamptz    NOT NULL,
  updated_at                 timestamptz    NOT NULL,
  CONSTRAINT uq_policies_device_metric UNIQUE (device_id, metric),
  CONSTRAINT ck_policies_bounds CHECK (minimum < maximum),
  CONSTRAINT ck_policies_duration CHECK (violation_duration_seconds > 0)
);

CREATE INDEX idx_policies_device_id ON monitoring_policies (device_id);
