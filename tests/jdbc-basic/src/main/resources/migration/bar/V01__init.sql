create schema if not exists public;
create extension if not exists vector;

create table public.item_embeddings
(
    id          bigserial primary key,
    item_uuid   uuid        not null default gen_random_uuid(),
    title       text        not null,
    description text,
    embedding   vector(64)  not null,
    metadata    jsonb                default '{}'::jsonb,
    created_at  timestamptz not null default now(),

    constraint item_embeddings_item_uuid_unique unique (item_uuid),
    constraint item_embeddings_title_not_empty
        check (length(trim(title)) > 0)
);