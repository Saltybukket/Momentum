import unicodedata

_BIDI_CONTROLS = frozenset("\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069")


def normalize_single_line(value: str, *, field: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{field} must not be blank")
    _reject_unsafe(normalized, allow_layout=False)
    return normalized


def normalize_multiline(value: str) -> str:
    normalized = value.strip()
    _reject_unsafe(normalized, allow_layout=True)
    return normalized


def _reject_unsafe(value: str, *, allow_layout: bool) -> None:
    for character in value:
        if character in _BIDI_CONTROLS:
            raise ValueError("bidirectional control characters are not allowed")
        category = unicodedata.category(character)
        if category != "Cc":
            continue
        if allow_layout and character in "\n\t":
            continue
        raise ValueError("control characters are not allowed")
