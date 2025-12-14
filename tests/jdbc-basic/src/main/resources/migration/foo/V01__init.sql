create extension if not exists citext;
create schema if not exists public;

-- ==========================================================
-- Enum type
-- ==========================================================
create type public.order_status as enum ('pending', 'paid', 'cancelled', 'refunded');
create type public.product_type as enum ('a', 'b', 'c');

-- ==========================================================
-- Composite type
-- ==========================================================
--create type public.address as -- TODO
--(
--    street      text,
--    city        text,
--    postal_code text,
--    country     text
--);

-- ==========================================================
-- Domain types
-- ==========================================================
-- Domain based on uuid
create domain public.user_id as uuid;
create domain public.order_id as uuid;

-- Domain based on text
create domain public.non_empty_text_domain as text
    check (value is null or length(trim(value)) > 0);

-- ==========================================================
-- Table: users
-- Covers: uuid, text, timestamptz, jsonb, array of text, domain types, unique constraints
-- ==========================================================
create table public.users
(
    id            public.user_id primary key            default gen_random_uuid(),
    username      public.non_empty_text_domain not null,
    email         text,
    display_name  text,
    roles         text[]                       not null default array ['user'],
    preferences   jsonb                        not null default '{}'::jsonb,
    created_at    timestamptz                  not null default now(),
    last_login_at timestamptz,

    -- Constraints
    constraint users_username_unique unique (username),
    constraint users_email_unique unique (email),
    constraint users_roles_not_empty check (array_length(roles, 1) is not null)
);

-- ==========================================================
-- Table: products
-- Covers: int, text, numeric checks, enum, json, timestamp
-- ==========================================================
create table public.products
(
    id          serial primary key,
    sku         text                         not null,
    name        public.non_empty_text_domain not null,
    type        public.product_type          not null,
    description text,
    price_cents int                          not null,
    status      public.order_status          not null default 'pending',
    extra_data  json,
    created_at  timestamp                    not null default now(),

    -- Constraints
    constraint products_sku_unique unique (sku),
    constraint products_price_positive check (price_cents > 0)
);

-- ==========================================================
-- Table: orders
-- Covers: foreign key, composite type, jsonb, bytea, timestamp/timestamptz, enum, check
-- ==========================================================
create table public.orders
(
    id               public.order_id primary key,
    order_uuid       uuid                not null default gen_random_uuid(),
    user_id          public.user_id      not null,
    product_id       int                 not null,
    status           public.order_status not null default 'pending',
    total_cents      int                 not null,
    notes            text,
    placed_at        timestamptz         not null default now(),
    processed_at     timestamp,
    metadata         jsonb,
    raw_json_payload json,
    receipt_pdf      bytea,
    --shipping_address public.address,
    tags             text[],

    -- Foreign keys
    constraint orders_user_fk
        foreign key (user_id) references users (id) on delete cascade,
    constraint orders_product_fk
        foreign key (product_id) references products (id) on delete restrict,

    -- Constraints
    constraint orders_total_positive check (total_cents >= 0),
    constraint orders_unique_order_uuid unique (order_uuid),
    constraint orders_processed_after_placed check (
        processed_at is null or processed_at >= placed_at
        )
);

-- ==========================================================
-- Table: documents
-- Covers: bytea, uuid domain, text domain, unique+check combos, nullable types
-- ==========================================================
create table public.documents
(
    id           uuid primary key        default gen_random_uuid(), -- domain(uuid)
    owner_id     public.user_id not null references users (id),     -- FK
    title        public.non_empty_text_domain,                      -- domain(text) nullable
    content      bytea,                                             -- bytea, nullable
    content_type text           not null default 'application/octet-stream',
    metadata     jsonb          not null default '{}'::jsonb,
    tags         text[]         not null default '{}',              -- array of text
    created_at   timestamptz    not null default now(),
    updated_at   timestamptz,

    constraint documents_owner_title_unique unique (owner_id, title),
    constraint documents_updated_after_created check (
        updated_at is null or updated_at >= created_at
        )
);


create table public.pgen_test_table
(
    key              citext primary key,
    i_range_nullable int4range,
    i_range          int4range not null,
    l_range_nullable int8range,
    l_range          int8range not null
);