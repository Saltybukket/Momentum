from fitness_platform.modules.catalog import MODULES, validate_acyclic_dependencies


def test_module_catalog_is_acyclic_and_unique() -> None:
    validate_acyclic_dependencies()
    names = [module.name for module in MODULES]
    assert len(names) == len(set(names))
    assert "gamification" in names
    assert "security" in names
