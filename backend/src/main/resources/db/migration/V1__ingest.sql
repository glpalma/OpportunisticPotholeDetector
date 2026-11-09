CREATE TABLE clients (
    client_id text PRIMARY KEY,
    last_seen_at timestamptz NOT NULL,
    queue_size integer
);

CREATE TABLE potholes (
    id uuid PRIMARY KEY,
    lat double precision NOT NULL,
    lon double precision NOT NULL,
    status text NOT NULL CHECK (status IN ('open', 'fixed', 'resunk')),
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    sighting_count integer NOT NULL,
    max_confidence double precision NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX potholes_lat_lon_idx ON potholes (lat, lon);
CREATE INDEX potholes_status_idx ON potholes (status);
CREATE INDEX potholes_last_seen_at_idx ON potholes (last_seen_at);

CREATE TABLE sightings (
    sighting_id uuid PRIMARY KEY,
    client_id text NOT NULL REFERENCES clients (client_id),
    captured_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    lat double precision NOT NULL,
    lon double precision NOT NULL,
    heading double precision NOT NULL,
    speed double precision NOT NULL,
    horizontal_accuracy_m double precision NOT NULL,
    confidence double precision NOT NULL,
    bbox_x integer NOT NULL,
    bbox_y integer NOT NULL,
    bbox_width integer NOT NULL,
    bbox_height integer NOT NULL,
    image_path text NOT NULL,
    thumbnail_path text,
    pothole_id uuid REFERENCES potholes (id),
    review text CHECK (review IS NULL OR review IN ('pothole', 'false_positive')),
    reviewed_by uuid,
    reviewed_at timestamptz
);

CREATE INDEX sightings_captured_at_idx ON sightings (captured_at);
CREATE INDEX sightings_lat_lon_idx ON sightings (lat, lon);
CREATE INDEX sightings_pothole_id_idx ON sightings (pothole_id);
CREATE INDEX sightings_unreviewed_captured_at_idx ON sightings (captured_at) WHERE review IS NULL;

CREATE TABLE jobs (
    id uuid PRIMARY KEY,
    type text NOT NULL,
    sighting_id uuid REFERENCES sightings (sighting_id),
    status text NOT NULL CHECK (status IN ('pending', 'done', 'failed')),
    attempts integer NOT NULL DEFAULT 0,
    last_error text,
    run_after timestamptz NOT NULL,
    created_at timestamptz NOT NULL
);
