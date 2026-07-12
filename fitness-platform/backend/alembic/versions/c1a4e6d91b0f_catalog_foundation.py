"""add public exercise catalog foundation

Revision ID: c1a4e6d91b0f
Revises: 4f3b20b5b92a
"""

import sqlalchemy as sa
from alembic import op

revision = "c1a4e6d91b0f"
down_revision = "4f3b20b5b92a"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "muscles",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("slug", sa.String(80), nullable=False, unique=True),
        sa.Column("name", sa.String(120), nullable=False),
    )
    op.create_table(
        "equipment",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("slug", sa.String(80), nullable=False, unique=True),
        sa.Column("name", sa.String(120), nullable=False),
    )
    op.create_table(
        "catalog_exercises",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("external_id", sa.String(120), nullable=False),
        sa.Column("source", sa.String(120), nullable=False),
        sa.Column("provenance", sa.Text(), nullable=False),
        sa.Column("license_name", sa.String(160), nullable=False),
        sa.Column("license_url", sa.String(500), nullable=False),
        sa.Column("version", sa.String(40), nullable=False),
        sa.Column(
            "status",
            sa.Enum("DRAFT", "PUBLISHED", "DEPRECATED", name="catalogstatus", native_enum=False),
            nullable=False,
        ),
        sa.Column("reviewed", sa.Boolean(), nullable=False),
        sa.Column("name", sa.String(120), nullable=False),
        sa.Column("description", sa.Text(), nullable=False),
        sa.Column(
            "tracking_type",
            sa.Enum(
                "REPS_WEIGHT",
                "REPS",
                "DURATION",
                "DISTANCE_DURATION",
                "MANUAL",
                name="trackingtype",
                native_enum=False,
            ),
            nullable=False,
        ),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("source", "external_id", name="uq_catalog_exercise_source_external_id"),
        sa.CheckConstraint(
            "status IN ('DRAFT','PUBLISHED','DEPRECATED')", name="ck_catalog_exercise_status"
        ),
    )
    op.create_index(
        "ix_catalog_exercises_status_reviewed", "catalog_exercises", ["status", "reviewed"]
    )
    op.create_table(
        "catalog_exercise_muscles",
        sa.Column(
            "exercise_id",
            sa.Uuid(),
            sa.ForeignKey("catalog_exercises.id", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column(
            "muscle_id",
            sa.Uuid(),
            sa.ForeignKey("muscles.id", ondelete="RESTRICT"),
            primary_key=True,
        ),
        sa.Column(
            "role",
            sa.Enum("PRIMARY", "SECONDARY", name="musclerole", native_enum=False),
            nullable=False,
        ),
        sa.CheckConstraint(
            "role IN ('PRIMARY','SECONDARY')", name="ck_catalog_exercise_muscle_role"
        ),
    )
    op.create_index(
        "ix_catalog_exercise_muscles_muscle",
        "catalog_exercise_muscles",
        ["muscle_id", "exercise_id"],
    )
    op.create_table(
        "catalog_exercise_equipment",
        sa.Column(
            "exercise_id",
            sa.Uuid(),
            sa.ForeignKey("catalog_exercises.id", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column(
            "equipment_id",
            sa.Uuid(),
            sa.ForeignKey("equipment.id", ondelete="RESTRICT"),
            primary_key=True,
        ),
    )
    op.create_index(
        "ix_catalog_exercise_equipment_equipment",
        "catalog_exercise_equipment",
        ["equipment_id", "exercise_id"],
    )


def downgrade() -> None:
    op.drop_index(
        "ix_catalog_exercise_equipment_equipment", table_name="catalog_exercise_equipment"
    )
    op.drop_table("catalog_exercise_equipment")
    op.drop_index("ix_catalog_exercise_muscles_muscle", table_name="catalog_exercise_muscles")
    op.drop_table("catalog_exercise_muscles")
    op.drop_index("ix_catalog_exercises_status_reviewed", table_name="catalog_exercises")
    op.drop_table("catalog_exercises")
    op.drop_table("equipment")
    op.drop_table("muscles")
