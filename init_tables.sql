CREATE TABLE IF NOT EXISTS bank_data (
  age          INTEGER,
  job          TEXT,
  marital      TEXT,
  education    TEXT,
  default      TEXT,
  balance      INTEGER,
  housing      TEXT,
  loan         TEXT,
  contact      TEXT,
  day          INTEGER,
  month        TEXT,
  duration     INTEGER,
  campaign     INTEGER,
  pdays        INTEGER,
  previous     INTEGER,
  poutcome     TEXT,
  deposit      TEXT
);


CREATE TABLE deposit_by_job (
  job TEXT,
  nb_depots BIGINT
);

CREATE TABLE deposit_by_marital (
  marital TEXT,
  nb_depots BIGINT
);

CREATE TABLE deposit_by_education (
  education TEXT,
  nb_depots BIGINT
);

CREATE TABLE yes_rate_by_contact (
  contact TEXT,
  total BIGINT,
  yes_count BIGINT,
  yes_rate DOUBLE PRECISION
);

CREATE TABLE deposit_by_housing (
  housing TEXT,
  deposit TEXT,
  nb BIGINT
);
