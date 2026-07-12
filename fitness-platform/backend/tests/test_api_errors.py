import logging

from httpx import ASGITransport, AsyncClient


async def test_request_id_is_bounded_and_unsafe_value_is_not_reflected(app_client, caplog) -> None:
    client, _ = app_client
    unsafe = "x" * 16_000
    with caplog.at_level(logging.INFO):
        response = await client.get("/does-not-exist", headers={"X-Request-ID": unsafe})
    request_id = response.headers["X-Request-ID"]
    assert response.status_code == 404
    assert 1 <= len(request_id) <= 64
    assert request_id != unsafe
    assert unsafe not in caplog.text


async def test_unexpected_error_uses_safe_envelope_and_request_id(app_client, caplog) -> None:
    original_client, _ = app_client
    app = original_client._transport.app

    async def boom() -> None:
        raise RuntimeError("SQL password=do-not-reflect")

    app.add_api_route("/__test__/boom", boom)
    with caplog.at_level(logging.ERROR):
        async with AsyncClient(
            transport=ASGITransport(app=app, raise_app_exceptions=False),
            base_url="http://test",
        ) as client:
            response = await client.get(
                "/__test__/boom", headers={"X-Request-ID": "safe-request-123"}
            )
    assert response.status_code == 500
    assert response.headers["X-Request-ID"] == "safe-request-123"
    assert response.json() == {
        "error": {
            "code": "INTERNAL_ERROR",
            "message": "An internal error occurred.",
            "details": [],
            "request_id": "safe-request-123",
        }
    }
    assert "do-not-reflect" not in response.text
    assert "do-not-reflect" not in caplog.text
