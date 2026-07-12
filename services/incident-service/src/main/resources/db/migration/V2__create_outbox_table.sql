CREATE TABLE outbox_events (
  id             uuid PRIMARY KEY,
  event_id       uuid         NOT NULL,
  aggregate_type varchar(30)  NOT NULL,
  aggregate_id   varchar(100) NOT NULL,
  event_type     varchar(50)  NOT NULL,
  topic          varchar(100) NOT NULL,
  message_key    varchar(100) NOT NULL,
  payload        text         NOT NULL,
  status         varchar(20)  NOT NULL,
  created_at     timestamptz  NOT NULL,
  published_at   timestamptz
);

CREATE INDEX idx_outbox_status_created ON outbox_events (status, created_at);
