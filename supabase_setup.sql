-- Run this entire script in Supabase Dashboard -> SQL Editor -> New query.
create extension if not exists pgcrypto;

create table if not exists public.reports (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    title text not null check (char_length(title) between 2 and 100),
    category text not null,
    description text not null default '',
    location text not null,
    report_type text not null check (report_type in ('lost', 'found')),
    image_url text,
    created_at timestamptz not null default now()
);

alter table public.reports enable row level security;

drop policy if exists "Anyone signed in can read reports" on public.reports;
create policy "Anyone signed in can read reports"
on public.reports for select to authenticated using (true);

drop policy if exists "Users can create own reports" on public.reports;
create policy "Users can create own reports"
on public.reports for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "Users can update own reports" on public.reports;
create policy "Users can update own reports"
on public.reports for update to authenticated
using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Users can delete own reports" on public.reports;
create policy "Users can delete own reports"
on public.reports for delete to authenticated using (auth.uid() = user_id);

-- Create a PUBLIC bucket so uploaded report photos can be displayed by URL.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('item-photos', 'item-photos', true, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update set public = true, file_size_limit = 5242880,
allowed_mime_types = array['image/jpeg','image/png','image/webp'];

drop policy if exists "Signed in users can upload own item photos" on storage.objects;
create policy "Signed in users can upload own item photos"
on storage.objects for insert to authenticated
with check (bucket_id = 'item-photos' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "Anyone can view item photos" on storage.objects;
create policy "Anyone can view item photos"
on storage.objects for select to public
using (bucket_id = 'item-photos');

drop policy if exists "Users can delete own item photos" on storage.objects;
create policy "Users can delete own item photos"
on storage.objects for delete to authenticated
using (bucket_id = 'item-photos' and (storage.foldername(name))[1] = auth.uid()::text);
