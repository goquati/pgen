create extension if not exists citext;
create schema if not exists public;

create domain public.user_id as uuid;
create domain public.order_id as uuid;
create type public.order_status as enum ('pending', 'paid', 'cancelled', 'refunded');
create type public.address as
(
    street      text,
    city        text,
    postal_code text,
    country     text
);

create table public.full_test_table
(
    key                 text primary key,
    user_id_nullable    public.user_id,
    user_id             public.user_id        not null,
    order_id_nullable   public.order_id,
    order_id            public.order_id       not null,
    address_nullable    public.address,
    address             public.address        not null,
    text_nullable       citext,
    text                citext                not null,
    table_nullable      regclass,
    "table"             regclass              not null,
    duration_nullable   interval,
    duration            interval              not null,
    i_range_nullable    int4range,
    i_range             int4range             not null,
    l_range_nullable    int8range,
    l_range             int8range             not null,
    i_mrange_nullable   int4multirange,
    i_mrange            int4multirange        not null,
    l_mrange_nullable   int8multirange,
    l_mrange            int8multirange        not null,
    enum_array_nullable public.order_status[],
    enum_array          public.order_status[] not null
);

create table public.date_time_test_table
(
    key           text primary key,
    tsz           timestamptz not null,
    tsz_nullable  timestamptz,
    ts            timestamp   not null,
    ts_nullable   timestamp,
    date          date        not null,
    date_nullable date,
    time          time        not null,
    time_nullable time
);

create table public.domain_test_table
(
    key               text primary key,
    user_id_nullable  public.user_id,
    user_id           public.user_id  not null,
    order_id_nullable public.order_id,
    order_id          public.order_id not null
);

create table public.enum_test_table
(
    key                  text primary key,
    enumeration_nullable public.order_status,
    enumeration          public.order_status not null
);

create table public.sync_test_table
(
    group_id int  not null,
    name     text not null,
    constraint sync_test_table_pk primary key (group_id, name)
);

create table public.citext_test_table
(
    key           text primary key,
    text_nullable citext,
    text          citext not null
);

create table public.regclass_test_table
(
    key            text primary key,
    table_nullable regclass,
    "table"        regclass not null
);

create table public.struct_test_table
(
    key              text primary key,
    address_nullable public.address,
    address          public.address not null
);

create table public.duration_test_table
(
    key               text primary key,
    duration          interval not null,
    duration_nullable interval
);

create table public.ranges_test_table
(
    key              text primary key,
    i_range_nullable int4range,
    i_range          int4range not null,
    l_range_nullable int8range,
    l_range          int8range not null
);


create table public.multi_ranges_test_table
(
    key               text primary key,
    i_mrange_nullable int4multirange,
    i_mrange          int4multirange not null,
    l_mrange_nullable int8multirange,
    l_mrange          int8multirange not null
);

create table public.enum_array_test_table
(
    key           text primary key,
    data          public.order_status[] not null,
    data_nullable public.order_status[]
);
