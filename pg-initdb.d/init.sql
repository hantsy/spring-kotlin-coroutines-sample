CREATE SEQUENCE posts_id_seq;

CREATE TABLE IF NOT EXISTS posts(
id INTEGER NOT NULL PRIMARY KEY DEFAULT nextval('posts_id_seq') ,
title VARCHAR(255) NOT NULL,
content VARCHAR(255) NOT NULL
);

ALTER SEQUENCE posts_id_seq OWNED BY posts.id;

CREATE SEQUENCE comments_id_seq;

CREATE TABLE IF NOT EXISTS comments(
id INTEGER NOT NULL PRIMARY KEY DEFAULT nextval('comments_id_seq') ,
post_id INTEGER REFERENCES posts(id),
content VARCHAR(255) NOT NULL
);

ALTER SEQUENCE comments_id_seq OWNED BY comments.id;
