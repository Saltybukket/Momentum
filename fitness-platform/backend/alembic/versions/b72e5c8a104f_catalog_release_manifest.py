"""Add canonical catalog release manifests.

Revision ID: b72e5c8a104f
Revises: a91c4e2f6b70
Create Date: 2026-07-12
"""

from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa

revision: str = "b72e5c8a104f"
down_revision: str | None = "a91c4e2f6b70"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "catalog_releases",
        sa.Column("catalog_version", sa.String(length=80), primary_key=True),
        sa.Column("schema_version", sa.String(length=20), nullable=False),
        sa.Column("content_hash", sa.String(length=71), nullable=False, unique=True),
        sa.Column("published_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("batch_id", sa.String(length=120), nullable=False, unique=True),
        sa.Column("sources", sa.JSON(), nullable=False),
        sa.Column("licenses", sa.JSON(), nullable=False),
        sa.Column("exercise_count", sa.Integer(), nullable=False),
        sa.Column("status", sa.String(length=20), nullable=False),
        sa.CheckConstraint("status IN ('PUBLISHED','RETIRED')", name="ck_catalog_release_status"),
    )


def downgrade() -> None:
    op.drop_table("catalog_releases")
