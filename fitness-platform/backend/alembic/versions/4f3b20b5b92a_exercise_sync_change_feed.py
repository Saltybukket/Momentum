"""exercise sync change feed

Revision ID: 4f3b20b5b92a
Revises: 08adec2dab35
"""

from alembic import op
import sqlalchemy as sa

revision = "4f3b20b5b92a"
down_revision = "08adec2dab35"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("exercises", sa.Column("revision", sa.Integer(), nullable=False, server_default="1"))
    op.create_table(
        "exercise_changes",
        sa.Column("sequence", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("owner_user_id", sa.Uuid(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("exercise_id", sa.Uuid(), sa.ForeignKey("exercises.id", ondelete="CASCADE"), nullable=False),
        sa.Column("revision", sa.Integer(), nullable=False),
        sa.Column("changed_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_exercise_changes_owner_sequence", "exercise_changes", ["owner_user_id", "sequence"])


def downgrade() -> None:
    op.drop_index("ix_exercise_changes_owner_sequence", table_name="exercise_changes")
    op.drop_table("exercise_changes")
    op.drop_column("exercises", "revision")
