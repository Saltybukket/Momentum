"""secure idempotency state and guest recovery

Revision ID: d8f2a1c7e904
Revises: c1a4e6d91b0f
"""

import sqlalchemy as sa
from alembic import op

revision = "d8f2a1c7e904"
down_revision = "c1a4e6d91b0f"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("guest_sessions") as batch:
        batch.add_column(sa.Column("installation_id", sa.Uuid(), nullable=True))
        batch.add_column(sa.Column("recovery_secret_hash", sa.String(64), nullable=True))
        batch.create_index("ix_guest_sessions_installation_id", ["installation_id"], unique=True)

    with op.batch_alter_table("idempotency_records") as batch:
        batch.add_column(
            sa.Column("state", sa.String(20), nullable=False, server_default="COMPLETED")
        )
        batch.add_column(sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True))
        batch.add_column(sa.Column("lease_expires_at", sa.DateTime(timezone=True), nullable=True))
        batch.alter_column("response_status", existing_type=sa.Integer(), nullable=True)
        batch.alter_column("response_body", existing_type=sa.JSON(), nullable=True)
    op.execute(
        "UPDATE idempotency_records SET updated_at = created_at, lease_expires_at = expires_at"
    )
    op.execute("DELETE FROM idempotency_records WHERE scope = 'create-guest-session'")
    with op.batch_alter_table("idempotency_records") as batch:
        batch.alter_column("updated_at", existing_type=sa.DateTime(timezone=True), nullable=False)
        batch.alter_column(
            "lease_expires_at", existing_type=sa.DateTime(timezone=True), nullable=False
        )
        batch.alter_column("state", server_default=None)


def downgrade() -> None:
    with op.batch_alter_table("idempotency_records") as batch:
        batch.alter_column(
            "response_status", existing_type=sa.Integer(), nullable=False, server_default="500"
        )
        batch.alter_column(
            "response_body", existing_type=sa.JSON(), nullable=False, server_default="{}"
        )
        batch.drop_column("lease_expires_at")
        batch.drop_column("updated_at")
        batch.drop_column("state")
    with op.batch_alter_table("guest_sessions") as batch:
        batch.drop_index("ix_guest_sessions_installation_id")
        batch.drop_column("recovery_secret_hash")
        batch.drop_column("installation_id")
