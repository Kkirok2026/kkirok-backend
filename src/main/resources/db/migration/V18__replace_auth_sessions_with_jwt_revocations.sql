create table auth_token_revocation (
    token_jti varchar(80) primary key,
    user_id bigint not null,
    expires_at timestamp not null,
    revoked_at timestamp not null default current_timestamp,
    constraint fk_auth_token_revocation_user
        foreign key (user_id) references user_account(user_id) on delete cascade
);

create index idx_auth_token_revocation_expires_at on auth_token_revocation(expires_at);

drop table if exists auth_sessions;
