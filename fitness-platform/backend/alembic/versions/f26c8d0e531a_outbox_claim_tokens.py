"""outbox claim tokens

Revision ID: f26c8d0e531a
Revises: e15b7c9d420f
Create Date: 2026-07-13
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "f26c8d0e531a"
down_revision: str | None = "e15b7c9d420f"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("outbox_events", sa.Column("claim_token", sa.Uuid(), nullable=True))
    op.create_index("ix_outbox_events_claim_token", "outbox_events", ["claim_token"])


def downgrade() -> None:
    op.drop_index("ix_outbox_events_claim_token", table_name="outbox_events")
    op.drop_column("outbox_events", "claim_token")
