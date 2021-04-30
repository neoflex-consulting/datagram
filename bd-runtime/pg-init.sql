CREATE DATABASE "hivemetastoredb";
CREATE DATABASE "oozie_metastore";
CREATE DATABASE "teneo";
CREATE DATABASE "hue";
CREATE DATABASE "dwh";

\c dwh;
create table if not exists public.interpretation_needed
(
    productmodelid integer not null,
    productmodel   varchar(255),
    language       char(64),
    description    varchar(99999)
);
