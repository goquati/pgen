create schema if not exists public;

-- ==========================================================
-- Enum type
-- ==========================================================
create type public.order_status as enum ('PENDING', 'PAID', 'CANCELLED', 'REFUNDED');
create type public.product_type as enum ('A', 'B', 'C');

-- ==========================================================
-- Domain types
-- ==========================================================
create domain public.user_id as uuid;
create domain public.order_id as uuid;
create domain public.non_empty_text as text
    check (value is null or length(trim(value)) > 0);

create table public.users
(
    id            public.user_id primary key     default gen_random_uuid(),
    username      public.non_empty_text not null,
    email         text,
    display_name  text,
    roles         text[]                not null default array ['user'],
    preferences   jsonb                 not null default '{}'::jsonb,
    created_at    timestamptz           not null default now(),
    last_login_at timestamptz,
    constraint users_username_unique unique (username),
    constraint users_email_unique unique (email),
    constraint users_roles_not_empty check (array_length(roles, 1) is not null)
);

create table public.products
(
    id          serial primary key,
    sku         text                  not null,
    name        public.non_empty_text not null,
    type        public.product_type   not null,
    description text,
    price_cents int                   not null,
    status      public.order_status   not null default 'PENDING',
    extra_data  json,
    created_at  timestamp             not null default now(),
    constraint products_sku_unique unique (sku),
    constraint products_price_positive check (price_cents > 0)
);

create table public.orders
(
    id               public.order_id primary key,
    order_uuid       uuid                not null default gen_random_uuid(),
    user_id          public.user_id      not null,
    product_id       int                 not null,
    status           public.order_status not null default 'PENDING',
    total_cents      int                 not null,
    notes            text,
    placed_at        timestamptz         not null default now(),
    processed_at     timestamp,
    metadata         jsonb,
    raw_json_payload json,
    receipt_pdf      bytea,
    tags             text[],
    constraint orders_user_fk
        foreign key (user_id) references users (id) on delete cascade,
    constraint orders_product_fk
        foreign key (product_id) references products (id) on delete restrict,
    constraint orders_total_positive check (total_cents >= 0),
    constraint orders_unique_order_uuid unique (order_uuid),
    constraint orders_processed_after_placed check (
        processed_at is null or processed_at >= placed_at
        )
);

create table public.documents
(
    id           uuid primary key        default gen_random_uuid(),
    owner_id     public.user_id not null references users (id),
    title        public.non_empty_text,
    content      bytea,
    content_type text           not null default 'application/octet-stream',
    metadata     jsonb          not null default '{}'::jsonb,
    tags         text[]         not null default '{}',
    created_at   timestamptz    not null default now(),
    updated_at   timestamptz,
    constraint documents_owner_title_unique unique (owner_id, title),
    constraint documents_updated_after_created check (
        updated_at is null or updated_at >= created_at
        )
);