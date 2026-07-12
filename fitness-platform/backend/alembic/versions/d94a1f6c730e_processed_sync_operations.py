"""Add owner-scoped sync operation deduplication.

Revision ID: d94a1f6c730e
Revises: c83f7a6d219b
Create Date: 2026-07-12
"""

from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa

revision: str = "d94a1f6c730e"
down_revision: str | None = "c83f7a6d219b"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "processed_sync_operations",
        sa.Column("owner_user_id", sa.Uuid(), nullable=False),
        sa.Column("operation_id", sa.Uuid(), nullable=False),
        sa.Column("request_hash", sa.String(length=64), nullable=False),
        sa.Column("result", sa.JSON()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["owner_user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("owner_user_id", "operation_id"),
    )
    op.create_index(
        "ix_processed_sync_operations_expires",
        "processed_sync_operations",
        ["expires_at"],
    )


def downgrade() -> None:
    op.drop_index("ix_processed_sync_operations_expires", table_name="processed_sync_operations")
    op.drop_table("processed_sync_operations")
