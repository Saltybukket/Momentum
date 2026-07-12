"""Purge ambiguous legacy idempotency keys.

Revision ID: c83f7a6d219b
Revises: b72e5c8a104f
Create Date: 2026-07-12
"""

from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa

revision: str = "c83f7a6d219b"
down_revision: str | None = "b72e5c8a104f"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute(sa.text("DELETE FROM idempotency_records"))


def downgrade() -> None:
    # Ephemeral, one-way-purged replay records cannot be reconstructed.
    pass
