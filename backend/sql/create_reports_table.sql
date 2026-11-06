-- Run this in the Supabase Dashboard → SQL Editor (or psql).

create table if not exists public.reports (
    id          uuid primary key default gen_random_uuid(),
    user_id     bigint not null references public.users(id) on delete cascade,
    subject     text not null,
    description text not null,
    status      text not null default 'Pending',
    created_at  timestamptz not null default now()
);

create index if not exists reports_user_id_idx on public.reports (user_id);
