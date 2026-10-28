"""The HTTP service in front of the help chain: the token, the request rules, the answer the backend reads.
No network and no database: the chain runs with the fake model and retriever from `fakes`."""

import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr

from fakes import FakeModel, passage, service, usable, written
from tutor.api import create_app, help_service
from tutor.config import Settings
from tutor.help_chain import Found, HelpError

TOKEN = "s3cret-token"


def client(model: FakeModel | None = None, found: Found | None = None, token: str | None = TOKEN) -> tuple[TestClient, list]:
    settings = Settings(_env_file=None, service_token=SecretStr(token) if token else None)
    app = create_app(settings)
    help, calls = service(model or FakeModel(written("Indiciul 1."), written("Indiciul 2."), written("Soluția.")), found)
    app.dependency_overrides[help_service] = lambda: help
    return TestClient(app), calls


def ask(test_client: TestClient, body: dict, token: str | None = TOKEN):
    return test_client.post("/help", json=body, headers={"X-Tutor-Token": token} if token else {})


def test_health_needs_no_token():
    test_client, _ = client()

    assert test_client.get("/health").json() == {"status": "ok"}


def test_three_levels_with_their_sources_and_no_textbook_text():
    test_client, calls = client(found=usable(passage(1, page=12), passage(2, code="AIS5P6", page=13)))

    response = ask(test_client, {"statement": "Calculează 20 - 2·(7 - 2).", "slot": "I.1"})

    assert response.status_code == 200
    body = response.json()
    assert body["answered"] is True
    assert [step["text"] for step in body["steps"]] == ["Indiciul 1.", "Indiciul 2.", "Soluția."]
    assert body["steps"][0]["sources"] == [{"n": 1, "code": "AIS5P5", "title": "Ordinea efectuării operațiilor",
                                            "book_page": 12}]
    assert body["refusal"] is None
    assert body["strategy"] == "slot"
    assert [p["code"] for p in body["passages"]] == ["AIS5P5", "AIS5P6"]
    assert "content" not in body["passages"][0]  # the textbook stays in the database, not in the answer
    assert body["input_tokens"] == 300 and body["output_tokens"] == 60
    assert body["cost_usd"] > 0
    assert calls == [("Calculează 20 - 2·(7 - 2).", "I.1")]  # retrieved once for the three levels


def test_a_refusal_is_an_answer_not_an_error():
    test_client, _ = client(model=FakeModel(written("", ids=(), covered=False)))

    body = ask(test_client, {"statement": "Aria cercului de rază 3.", "levels": 1}).json()

    assert body["answered"] is False
    assert body["steps"] == []
    assert body["refusal"] == {"reason": "not_covered", "message": "Nu am asta în manual."}


@pytest.mark.parametrize("token", [None, "wrong"])
def test_help_needs_the_service_token(token):
    test_client, calls = client()

    assert ask(test_client, {"statement": "2 + 2"}, token=token).status_code == 401
    assert calls == []


def test_without_a_configured_token_the_service_refuses_everyone():
    test_client, calls = client(token=None)

    assert ask(test_client, {"statement": "2 + 2"}, token="anything").status_code == 503
    assert calls == []


@pytest.mark.parametrize("body", [
    {"statement": ""},
    {"statement": "x" * 4001},
    {"statement": "2 + 2", "levels": 4},
    {"statement": "2 + 2", "slot": "I.1; DROP"},
])
def test_bad_requests_are_refused_before_the_chain(body):
    test_client, calls = client()

    assert ask(test_client, body).status_code == 422
    assert calls == []


def test_a_model_answer_in_the_wrong_shape_is_a_502_with_a_safe_message():
    class Failing:
        def run(self, *_args, **_kwargs):
            raise HelpError("Modelul nu a întors JSON-ul așteptat.")

    test_client, _ = client()
    test_client.app.dependency_overrides[help_service] = Failing

    response = ask(test_client, {"statement": "2 + 2"})

    assert response.status_code == 502
    assert response.json() == {"detail": "Modelul nu a întors JSON-ul așteptat."}
