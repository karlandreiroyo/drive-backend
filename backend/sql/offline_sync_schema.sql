-- D.R.I.V.E. offline-first sync schema.
-- The Android client writes to Room first; the sync worker upserts these rows
-- through the Express backend, which uses the Supabase service_role key.

create table if not exists public.trips (
    id uuid primary key,
    user_id uuid not null,
    start_time timestamptz not null,
    end_time timestamptz,
    status text not null default 'COMPLETED',
    origin text,
    destination text,
    created_at timestamptz not null default now()
);

create table if not exists public.trip_incidents (
    id uuid primary key,
    trip_id uuid not null references public.trips(id) on delete cascade,
    user_id uuid not null,
    description text not null,
    time text,
    incident_type text,
    confidence double precision,
    device_timestamp bigint,
    is_synced boolean not null default true,
    email_sent boolean not null default false,
    created_at timestamptz not null default now()
);

create index if not exists trips_user_id_idx on public.trips (user_id);
create index if not exists trips_start_time_idx on public.trips (start_time desc);
create index if not exists trip_incidents_trip_id_idx on public.trip_incidents (trip_id);
create index if not exists trip_incidents_user_id_idx on public.trip_incidents (user_id);
create index if not exists trip_incidents_pending_email_idx
    on public.trip_incidents (user_id)
    where email_sent = false;

alter table public.trips enable row level security;
alter table public.trip_incidents enable row level security;

-- The Express backend connects with the service_role key, which bypasses RLS.
-- Add owner-scoped policies only if the Android app ever queries PostgREST directly:
-- create policy "Owners read their trips" on public.trips
--     for select using (auth.uid() = user_id);
