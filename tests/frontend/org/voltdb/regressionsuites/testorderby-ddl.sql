
-- partitioned in test on pkey
CREATE TABLE O1 (
 PKEY          INTEGER NOT NULL,
 A_INT         INTEGER,
 A_INLINE_STR  VARCHAR(10),
 A_POOL_STR    VARCHAR(1024),
 PRIMARY KEY (PKEY)
);

-- replicated in test
CREATE TABLE O2 (
 PKEY          INTEGER,
 A_INT         INTEGER,
 A_INLINE_STR  VARCHAR(10),
 A_POOL_STR    VARCHAR(1024),
 PRIMARY KEY (PKEY)
);

CREATE TABLE O3 (
 PK1 INTEGER NOT NULL,
 PK2 INTEGER NOT NULL,
 I3  INTEGER NOT NULL,
 I4  INTEGER NOT NULL,
 PRIMARY KEY (PK1, PK2)
 );

 CREATE INDEX O3_TREE ON O3 (I3 DESC);

create table a
(
  a integer not null
);
create table b
(
  a integer not null
);

