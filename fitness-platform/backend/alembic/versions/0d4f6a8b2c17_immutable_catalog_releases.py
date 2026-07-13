"""Add immutable release-scoped catalog content and atomic activation.

Revision ID: 0d4f6a8b2c17
Revises: f26c8d0e531a
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0d4f6a8b2c17"
down_revision: str | None = "f26c8d0e531a"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute("UPDATE catalog_releases SET status = 'RETIRED'")
    with op.batch_alter_table("catalog_releases") as batch:
        batch.drop_constraint("ck_catalog_release_status", type_="check")
        batch.alter_column(
            "status",
            existing_type=sa.String(20),
            type_=sa.Enum(
                "STAGED",
                "ACTIVE",
                "RETIRED",
                "FAILED",
                name="catalogreleasestatus",
                native_enum=False,
            ),
            existing_nullable=False,
        )
        batch.create_check_constraint(
            "ck_catalog_release_status",
            "status IN ('STAGED','ACTIVE','RETIRED','FAILED')",
        )
    op.create_index(
        "uq_catalog_single_active_status",
        "catalog_releases",
        ["status"],
        unique=True,
        sqlite_where=sa.text("status = 'ACTIVE'"),
        postgresql_where=sa.text("status = 'ACTIVE'"),
    )
    op.create_table(
        "catalog_activation",
        sa.Column("singleton_id", sa.Integer(), primary_key=True),
        sa.Column(
            "catalog_version",
            sa.String(80),
            sa.ForeignKey("catalog_releases.catalog_version", ondelete="RESTRICT"),
            nullable=True,
            unique=True,
        ),
        sa.CheckConstraint("singleton_id = 1", name="ck_catalog_activation_singleton"),
    )
    op.create_table(
        "catalog_release_muscles",
        sa.Column(
            "catalog_version",
            sa.String(80),
            sa.ForeignKey("catalog_releases.catalog_version", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column("slug", sa.String(80), primary_key=True),
        sa.Column("name", sa.String(120), nullable=False),
    )
    op.create_table(
        "catalog_release_equipment",
        sa.Column(
            "catalog_version",
            sa.String(80),
            sa.ForeignKey("catalog_releases.catalog_version", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column("slug", sa.String(80), primary_key=True),
        sa.Column("name", sa.String(120), nullable=False),
    )
    op.create_table(
        "catalog_release_exercises",
        sa.Column(
            "catalog_version",
            sa.String(80),
            sa.ForeignKey("catalog_releases.catalog_version", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("external_id", sa.String(120), nullable=False),
        sa.Column("source", sa.String(120), nullable=False),
        sa.Column("provenance", sa.Text(), nullable=False),
        sa.Column("license_name", sa.String(160), nullable=False),
        sa.Column("license_url", sa.String(500), nullable=False),
        sa.Column("version", sa.String(40), nullable=False),
        sa.Column(
            "status",
            sa.Enum(
                "DRAFT",
                "PUBLISHED",
                "DEPRECATED",
                name="catalogstatus",
                native_enum=False,
            ),
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
                name="trackingtype",
                native_enum=False,
            ),
            nullable=False,
        ),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint(
            "catalog_version",
            "source",
            "external_id",
            name="uq_release_exercise_source_external",
        ),
        sa.CheckConstraint(
            "status IN ('DRAFT','PUBLISHED','DEPRECATED')",
            name="ck_release_exercise_status",
        ),
    )
    op.create_index(
        "ix_release_exercises_name",
        "catalog_release_exercises",
        ["catalog_version", "name"],
    )
    op.create_table(
        "catalog_release_exercise_muscles",
        sa.Column("catalog_version", sa.String(80), primary_key=True),
        sa.Column("exercise_id", sa.Uuid(), primary_key=True),
        sa.Column("muscle_slug", sa.String(80), primary_key=True),
        sa.Column(
            "role",
            sa.Enum("PRIMARY", "SECONDARY", name="musclerole", native_enum=False),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["catalog_version", "exercise_id"],
            ["catalog_release_exercises.catalog_version", "catalog_release_exercises.id"],
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["catalog_version", "muscle_slug"],
            ["catalog_release_muscles.catalog_version", "catalog_release_muscles.slug"],
            ondelete="RESTRICT",
        ),
        sa.CheckConstraint("role IN ('PRIMARY','SECONDARY')", name="ck_release_muscle_role"),
    )
    op.create_index(
        "ix_release_exercise_muscle_filter",
        "catalog_release_exercise_muscles",
        ["catalog_version", "muscle_slug"],
    )
    op.create_table(
        "catalog_release_exercise_equipment",
        sa.Column("catalog_version", sa.String(80), primary_key=True),
        sa.Column("exercise_id", sa.Uuid(), primary_key=True),
        sa.Column("equipment_slug", sa.String(80), primary_key=True),
        sa.ForeignKeyConstraint(
            ["catalog_version", "exercise_id"],
            ["catalog_release_exercises.catalog_version", "catalog_release_exercises.id"],
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["catalog_version", "equipment_slug"],
            ["catalog_release_equipment.catalog_version", "catalog_release_equipment.slug"],
            ondelete="RESTRICT",
        ),
    )
    op.create_index(
        "ix_release_exercise_equipment_filter",
        "catalog_release_exercise_equipment",
        ["catalog_version", "equipment_slug"],
    )

    _migrate_active_legacy_release()


def _migrate_active_legacy_release() -> None:
    connection = op.get_bind()
    active = connection.execute(
        sa.text(
            "SELECT catalog_version FROM catalog_releases "
            "ORDER BY published_at DESC, catalog_version DESC LIMIT 1"
        )
    ).scalar_one_or_none()
    connection.execute(
        sa.text(
            "INSERT INTO catalog_activation (singleton_id, catalog_version) "
            "VALUES (1, :catalog_version)"
        ),
        {"catalog_version": active},
    )
    if active is None:
        return
    connection.execute(
        sa.text("UPDATE catalog_releases SET status = 'ACTIVE' WHERE catalog_version = :version"),
        {"version": active},
    )
    parameters = {"version": active}
    connection.execute(
        sa.text(
            "INSERT INTO catalog_release_muscles (catalog_version, slug, name) "
            "SELECT :version, slug, name FROM muscles"
        ),
        parameters,
    )
    connection.execute(
        sa.text(
            "INSERT INTO catalog_release_equipment (catalog_version, slug, name) "
            "SELECT :version, slug, name FROM equipment"
        ),
        parameters,
    )
    connection.execute(
        sa.text(
            "INSERT INTO catalog_release_exercises "
            "(catalog_version, id, external_id, source, provenance, license_name, license_url, "
            "version, status, reviewed, name, description, tracking_type, created_at, updated_at) "
            "SELECT :version, id, external_id, source, provenance, license_name, license_url, "
            "version, status, reviewed, name, description, tracking_type, created_at, updated_at "
            "FROM catalog_exercises"
        ),
        parameters,
    )
    connection.execute(
        sa.text(
            "INSERT INTO catalog_release_exercise_muscles "
            "(catalog_version, exercise_id, muscle_slug, role) "
            "SELECT :version, relation.exercise_id, muscle.slug, relation.role "
            "FROM catalog_exercise_muscles relation JOIN muscles muscle "
            "ON muscle.id = relation.muscle_id"
        ),
        parameters,
    )
    connection.execute(
        sa.text(
            "INSERT INTO catalog_release_exercise_equipment "
            "(catalog_version, exercise_id, equipment_slug) "
            "SELECT :version, relation.exercise_id, equipment.slug "
            "FROM catalog_exercise_equipment relation "
            "JOIN equipment ON equipment.id = relation.equipment_id"
        ),
        parameters,
    )


def downgrade() -> None:
    op.drop_index("uq_catalog_single_active_status", table_name="catalog_releases")
    op.drop_index(
        "ix_release_exercise_equipment_filter",
        table_name="catalog_release_exercise_equipment",
    )
    op.drop_table("catalog_release_exercise_equipment")
    op.drop_index(
        "ix_release_exercise_muscle_filter", table_name="catalog_release_exercise_muscles"
    )
    op.drop_table("catalog_release_exercise_muscles")
    op.drop_index("ix_release_exercises_name", table_name="catalog_release_exercises")
    op.drop_table("catalog_release_exercises")
    op.drop_table("catalog_release_equipment")
    op.drop_table("catalog_release_muscles")
    op.drop_table("catalog_activation")
    with op.batch_alter_table("catalog_releases") as batch:
        batch.drop_constraint("ck_catalog_release_status", type_="check")
        batch.alter_column(
            "status",
            existing_type=sa.Enum(
                "STAGED",
                "ACTIVE",
                "RETIRED",
                "FAILED",
                name="catalogreleasestatus",
                native_enum=False,
            ),
            type_=sa.String(20),
            existing_nullable=False,
        )
    op.execute("UPDATE catalog_releases SET status = 'RETIRED' WHERE status != 'ACTIVE'")
    op.execute("UPDATE catalog_releases SET status = 'PUBLISHED' WHERE status = 'ACTIVE'")
    with op.batch_alter_table("catalog_releases") as batch:
        batch.create_check_constraint(
            "ck_catalog_release_status", "status IN ('PUBLISHED','RETIRED')"
        )
