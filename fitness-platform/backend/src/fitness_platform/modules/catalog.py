from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class ModuleBoundary:
    name: str
    responsibility: str
    depends_on: tuple[str, ...]
    implementation_status: str


MODULES: tuple[ModuleBoundary, ...] = (
    ModuleBoundary("identity", "Guest and future registered identities", (), "basis implemented"),
    ModuleBoundary(
        "user_profile",
        "User preferences and optional profile data",
        ("identity",),
        "basis implemented",
    ),
    ModuleBoundary("onboarding", "Optional profile setup flow", ("user_profile",), "contract only"),
    ModuleBoundary(
        "exercises",
        "Catalog and private custom exercises",
        ("identity",),
        "custom exercise slice implemented",
    ),
    ModuleBoundary("equipment", "Equipment taxonomy", (), "data contract only"),
    ModuleBoundary(
        "training_locations", "Locations and available equipment", ("equipment",), "contract only"
    ),
    ModuleBoundary(
        "workout_planning",
        "Plans and schedule",
        ("exercises", "training_locations"),
        "contract only",
    ),
    ModuleBoundary(
        "workout_execution",
        "Offline workout lifecycle",
        ("exercises",),
        "minimal slice implemented",
    ),
    ModuleBoundary(
        "activity_tracking",
        "Activities and active minutes",
        ("integrations",),
        "provider port only",
    ),
    ModuleBoundary(
        "step_tracking", "Step source selection and aggregation", ("health_data",), "contract only"
    ),
    ModuleBoundary(
        "nutrition",
        "Nutrition records and daily summaries",
        ("integrations",),
        "provider port only",
    ),
    ModuleBoundary(
        "body_measurements", "Weight and composition", ("integrations",), "provider port only"
    ),
    ModuleBoundary(
        "health_data",
        "Normalized health records and provenance",
        ("integrations",),
        "provider port only",
    ),
    ModuleBoundary(
        "integrations",
        "External provider adapters",
        (),
        "ports and deterministic mocks implemented",
    ),
    ModuleBoundary(
        "analytics",
        "Derived statistics and projections",
        ("workout_execution", "health_data"),
        "contract only",
    ),
    ModuleBoundary(
        "gamification", "Server-authoritative XP, level and rewards", ("security",), "contract only"
    ),
    ModuleBoundary("quests", "Quest definitions and progress", ("gamification",), "contract only"),
    ModuleBoundary(
        "streaks", "Streaks and freeze lifecycle", ("gamification", "commerce"), "contract only"
    ),
    ModuleBoundary(
        "boss_events",
        "Global capped contribution events",
        ("gamification", "security"),
        "contract only",
    ),
    ModuleBoundary("groups", "Friend groups, roles and membership", ("identity",), "contract only"),
    ModuleBoundary(
        "tournaments", "Fair normalized group competition", ("groups", "security"), "contract only"
    ),
    ModuleBoundary(
        "social",
        "Structured workout posts and reactions",
        ("identity", "moderation"),
        "contract only",
    ),
    ModuleBoundary(
        "moderation", "Reports, queues, appeals and audit", ("administration",), "contract only"
    ),
    ModuleBoundary(
        "notifications",
        "Local and push notification policy",
        ("integrations",),
        "provider port only",
    ),
    ModuleBoundary(
        "commerce", "Products, purchases and entitlements", ("security",), "provider port only"
    ),
    ModuleBoundary(
        "advertising", "Optional rewarded ads", ("commerce", "security"), "provider port only"
    ),
    ModuleBoundary(
        "administration", "Audited content and operations API", ("identity",), "contract only"
    ),
    ModuleBoundary(
        "ai_helper", "Optional privacy-minimized assistant", ("integrations",), "provider port only"
    ),
    ModuleBoundary("security", "Integrity, fraud signals and audit", (), "foundation implemented"),
)


def validate_acyclic_dependencies() -> None:
    graph = {module.name: set(module.depends_on) for module in MODULES}
    temporary: set[str] = set()
    permanent: set[str] = set()

    def visit(name: str) -> None:
        if name in permanent:
            return
        if name in temporary:
            raise ValueError(f"cyclic module dependency detected at {name}")
        temporary.add(name)
        for dependency in graph[name]:
            if dependency not in graph:
                raise ValueError(f"unknown module dependency: {dependency}")
            visit(dependency)
        temporary.remove(name)
        permanent.add(name)

    for module_name in graph:
        visit(module_name)
