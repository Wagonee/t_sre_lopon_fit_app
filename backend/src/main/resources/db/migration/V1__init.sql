create table app_user (
    id            bigint generated always as identity primary key,
    email         varchar(254) not null,
    password_hash varchar(100) not null,
    display_name  varchar(100) not null,
    failed_logins integer      not null default 0,
    locked_until  timestamp with time zone,
    created_at    timestamp with time zone not null default now(),
    updated_at    timestamp with time zone not null default now()
);

create unique index app_user_email_uq on app_user (lower(email));

create table refresh_token (
    id             bigint generated always as identity primary key,
    user_id        bigint      not null references app_user (id) on delete cascade,
    family_id      uuid        not null,
    token_hash     varchar(64) not null unique,
    created_at     timestamp with time zone not null default now(),
    expires_at     timestamp with time zone not null,
    revoked_at     timestamp with time zone,
    replaced_by_id bigint references refresh_token (id) on delete set null
);

create index refresh_token_user_idx on refresh_token (user_id);
create index refresh_token_family_idx on refresh_token (family_id);
create index refresh_token_expires_idx on refresh_token (expires_at);

create table athlete_settings (
    user_id    bigint primary key references app_user (id) on delete cascade,
    settings   jsonb  not null,
    updated_at timestamp with time zone not null default now()
);

create table workout (
    id           bigint generated always as identity primary key,
    user_id      bigint       not null references app_user (id) on delete cascade,
    sha1         varchar(40)  not null,
    filename     varchar(255) not null,
    title        varchar(200) not null,
    description  varchar(10000),
    start_time   timestamp    not null,
    workout_date date         not null,
    sport        varchar(50)  not null,
    sub_sport    varchar(50),
    indoor       boolean      not null,
    has_gps      boolean      not null,
    has_power    boolean      not null,
    has_hr       boolean      not null,
    device       varchar(100) not null,
    created_at   timestamp with time zone not null default now(),
    updated_at   timestamp with time zone not null default now(),
    constraint workout_user_sha1_uq unique (user_id, sha1)
);

create index workout_user_start_idx on workout (user_id, start_time desc, id desc);

create table workout_file (
    workout_id bigint  primary key references workout (id) on delete cascade,
    content    bytea   not null,
    size_bytes integer not null
);

create table workout_summary (
    workout_id          bigint primary key references workout (id) on delete cascade,
    distance_km         double precision not null,
    elapsed_min         double precision not null,
    moving_min          double precision not null,
    stopped_min         double precision not null,
    avg_speed_kmh       double precision not null,
    max_speed_kmh       double precision not null,
    avg_hr              integer,
    max_hr              integer,
    max_hr_speed_kmh    double precision,
    avg_cad             integer,
    max_cad             integer,
    cad_ge80_pct        integer,
    cad_ge90_pct        integer,
    ascent_m            integer          not null,
    descent_m           integer          not null,
    ascent_device_m     integer,
    temp_min            integer,
    temp_avg            double precision,
    temp_max            integer,
    temp_context        varchar(10)      not null,
    kcal                integer,
    load                integer          not null,
    ef                  double precision,
    ef_power            double precision,
    decoupling_pct      double precision,
    decoupling_method   varchar(30)      not null,
    decoupling_reliable boolean          not null,
    hr_zone_minutes     double precision not null,
    z2_band_min         double precision not null,
    z2_band_pct         double precision not null,
    le_z2_top_pct       double precision not null,
    avg_power           integer,
    np                  integer,
    intensity_factor    double precision,
    tss                 integer,
    ftp_used            double precision not null,
    warning_count       integer          not null,
    analysis            jsonb            not null,
    analyzed_at         timestamp with time zone not null default now()
);

create table track_point (
    workout_id  bigint           not null references workout (id) on delete cascade,
    seq         integer          not null,
    t_min       double precision not null,
    distance_km double precision not null,
    latitude    double precision,
    longitude   double precision,
    altitude_m  double precision not null,
    heart_rate  integer,
    cadence     integer,
    power       integer,
    speed_kmh   double precision,
    temperature integer,
    primary key (workout_id, seq)
);
