"""Hash existing idempotency keys at rest.

Revision ID: a91c4e2f6b70
Revises: d8f2a1c7e904
Create Date: 2026-07-12
"""

from collections.abc import Sequence
import hashlib

from alembic import op
import sqlalchemy as sa

revision: str = "a91c4e2f6b70"
down_revision: str | None = "d8f2a1c7e904"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def _storage_key(scope: str, key: str) -> str:
    return hashlib.sha256(f"{scope}\0{key}".encode()).hexdigest()


def upgrade() -> None:
    connection = op.get_bind()
    records = sa.table(
        "idempotency_records",
        sa.column("id", sa.Uuid()),
        sa.column("scope", sa.String()),
        sa.column("key", sa.String()),
    )
    for row in connection.execute(sa.select(records.c.id, records.c.scope, records.c.key)):
        if len(row.key) == 64 and all(character in "0123456789abcdef" for character in row.key):
            continue
        connection.execute(
            records.update()
            .where(records.c.scope == row.scope, records.c.key == row.key)
            .values(key=_storage_key(row.scope, row.key))
        )


def downgrade() -> None:
    # Hashing is deliberately one-way; rows remain valid opaque keys for the older schema.
    pass
