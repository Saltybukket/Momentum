from typing import Protocol
from uuid import UUID, uuid4


class UuidProvider(Protocol):
    def new(self) -> UUID: ...


class RandomUuidProvider:
    def new(self) -> UUID:
        return uuid4()
