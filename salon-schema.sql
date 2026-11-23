-- Anamika Lux Salon: role-based management schema
-- Run this once in Supabase SQL Editor.

create table if not exists public.user_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null,
  phone text not null unique,
  email text not null,
  age int,
  gender text,
  role text not null default 'client' check (role in ('client','staff','admin')),
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.salon_staff (
  id uuid primary key default gen_random_uuid(),
  user_id uuid unique references public.user_profiles(id) on delete cascade,
  staff_code text not null unique,
  display_name text not null,
  phone text,
  service_skills text[] default '{}',
  commission_percent numeric(5,2) default 0,
  status text not null default 'active' check (status in ('active','inactive','on_leave')),
  created_at timestamptz not null default now()
);

create table if not exists public.salon_appointments (
  id uuid primary key default gen_random_uuid(),
  booking_code text not null unique default ('AL-' || upper(substr(replace(gen_random_uuid()::text,'-',''),1,8))),
  client_id uuid references public.user_profiles(id) on delete set null,
  client_name text not null,
  client_phone text not null,
  service_name text not null,
  service_price numeric(12,2) not null default 0,
  appointment_date date not null,
  appointment_slot text not null,
  address text,
  assigned_staff_id uuid references public.salon_staff(id) on delete set null,
  status text not null default 'pending' check (status in ('pending','confirmed','assigned','in_progress','completed','cancelled')),
  payment_status text not null default 'unpaid' check (payment_status in ('unpaid','partial','paid')),
  notes text,
  created_at timestamptz not null default now()
);

create table if not exists public.staff_attendance (
  id uuid primary key default gen_random_uuid(),
  staff_id uuid not null references public.salon_staff(id) on delete cascade,
  attendance_date date not null default current_date,
  check_in timestamptz,
  check_out timestamptz,
  status text not null default 'present' check (status in ('present','absent','leave')),
  unique(staff_id, attendance_date)
);

create table if not exists public.salon_bills (
  id uuid primary key default gen_random_uuid(),
  appointment_id uuid references public.salon_appointments(id) on delete set null,
  client_id uuid references public.user_profiles(id) on delete set null,
  subtotal numeric(12,2) not null default 0,
  discount numeric(12,2) not null default 0,
  total numeric(12,2) not null default 0,
  payment_method text,
  payment_status text not null default 'paid',
  created_at timestamptz not null default now()
);

create index if not exists idx_appointments_date on public.salon_appointments(appointment_date);
create index if not exists idx_appointments_staff on public.salon_appointments(assigned_staff_id);
create index if not exists idx_appointments_client on public.salon_appointments(client_id);

alter table public.user_profiles enable row level security;
alter table public.salon_staff enable row level security;
alter table public.salon_appointments enable row level security;
alter table public.staff_attendance enable row level security;
alter table public.salon_bills enable row level security;

-- Basic policies. Admin/staff management is done through the SECURITY DEFINER RPCs below.
drop policy if exists "users own profile" on public.user_profiles;
create policy "users own profile" on public.user_profiles for select using (id = auth.uid());

drop policy if exists "client own appointments" on public.salon_appointments;
create policy "client own appointments" on public.salon_appointments for select using (client_id = auth.uid());

create or replace function public.get_login_email_by_phone(p_phone text)
returns text
language sql security definer set search_path = public
as $$ select email from public.user_profiles where phone = regexp_replace(p_phone,'\D','','g') and active = true limit 1 $$;

create or replace function public.verify_client_identity(p_phone text, p_email text)
returns boolean
language sql security definer set search_path = public
as $$ select exists(select 1 from public.user_profiles where phone = regexp_replace(p_phone,'\D','','g') and lower(email)=lower(p_email) and role='client' and active=true) $$;

create or replace function public.create_client_profile(
  p_user_id uuid, p_name text, p_phone text, p_email text, p_age int, p_gender text
) returns public.user_profiles
language plpgsql security definer set search_path = public
as $$
declare v public.user_profiles;
begin
  insert into public.user_profiles(id,full_name,phone,email,age,gender,role)
  values(p_user_id,p_name,regexp_replace(p_phone,'\D','','g'),lower(p_email),p_age,p_gender,'client')
  returning * into v;
  return v;
end $$;

create or replace function public.my_role()
returns text
language sql security definer set search_path = public
as $$ select role from public.user_profiles where id=auth.uid() and active=true limit 1 $$;

grant execute on function public.get_login_email_by_phone(text) to anon, authenticated;
grant execute on function public.verify_client_identity(text,text) to anon, authenticated;
grant execute on function public.create_client_profile(uuid,text,text,text,int,text) to authenticated;
grant execute on function public.my_role() to authenticated;
