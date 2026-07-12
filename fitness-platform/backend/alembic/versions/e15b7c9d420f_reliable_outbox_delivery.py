"""reliable outbox delivery

Revision ID: e15b7c9d420f
Revises: d94a1f6c730e
Create Date: 2026-07-12
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "e15b7c9d420f"
down_revision: str | None = "d94a1f6c730e"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("outbox_events", sa.Column("status", sa.String(length=24), nullable=True))
    op.add_column("outbox_events", sa.Column("claim_owner", sa.String(length=120), nullable=True))
    op.add_column(
        "outbox_events", sa.Column("lease_expires_at", sa.DateTime(timezone=True), nullable=True)
    )
    op.add_column(
        "outbox_events", sa.Column("next_attempt_at", sa.DateTime(timezone=True), nullable=True)
    )
    op.add_column("outbox_events", sa.Column("max_attempts", sa.Integer(), nullable=True))
    op.execute("UPDATE outbox_events SET status = 'PENDING' WHERE processed_at IS NULL")
    op.execute("UPDATE outbox_events SET status = 'PROCESSED' WHERE processed_at IS NOT NULL")
    op.execute("UPDATE outbox_events SET next_attempt_at = occurred_at")
    op.execute("UPDATE outbox_events SET max_attempts = 5")
    with op.batch_alter_table("outbox_events") as batch_op:
        batch_op.alter_column("status", existing_type=sa.String(length=24), nullable=False)
        batch_op.alter_column(
            "next_attempt_at", existing_type=sa.DateTime(timezone=True), nullable=False
        )
        batch_op.alter_column("max_attempts", existing_type=sa.Integer(), nullable=False)
    op.create_index("ix_outbox_events_status", "outbox_events", ["status"])
    op.create_index("ix_outbox_events_claim_owner", "outbox_events", ["claim_owner"])
    op.create_index("ix_outbox_events_lease_expires_at", "outbox_events", ["lease_expires_at"])
    op.create_index("ix_outbox_events_next_attempt_at", "outbox_events", ["next_attempt_at"])


def downgrade() -> None:
    op.drop_index("ix_outbox_events_next_attempt_at", table_name="outbox_events")
    op.drop_index("ix_outbox_events_lease_expires_at", table_name="outbox_events")
    op.drop_index("ix_outbox_events_claim_owner", table_name="outbox_events")
    op.drop_index("ix_outbox_events_status", table_name="outbox_events")
    op.drop_column("outbox_events", "max_attempts")
    op.drop_column("outbox_events", "next_attempt_at")
    op.drop_column("outbox_events", "lease_expires_at")
    op.drop_column("outbox_events", "claim_owner")
    op.drop_column("outbox_events", "status")
