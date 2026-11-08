-- Run this in the Supabase Dashboard → SQL Editor (or psql).

create table if not exists public.detections (
    id               uuid primary key default gen_random_uuid(),
    user_id          bigint not null references public.users(id) on delete cascade,
    detection_type   text not null,
    confidence       real not null check (confidence >= 0 and confidence <= 1),
    device_timestamp bigint,
    created_at       timestamptz not null default now()
);

create index if not exists detections_user_id_created_at_idx
    on public.detections (user_id, created_at desc);
