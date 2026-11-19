"""The HTTP side of „Teme recapitulative": /solve, /similar-exercises, /recap - the token, the request rules, the
answers the backend reads. No network: fake models, the real figures and the real SymPy comparison."""

import base64
import io

import pytest
from fastapi.testclient import TestClient
from PIL import Image
from pydantic import SecretStr

from fakes import FakeAsk, FakeModel, passage, solution, usable, written
from tutor.api import create_app, recap_service, similar_service, solve_service
from tutor.config import Settings
from tutor.help_chain import Found
from tutor.recap import RecapService
from tutor.solver import SimilarService, SolveService, SolveUnavailable

TOKEN = "s3cret-token"
HEADERS = {"X-Tutor-Token": TOKEN}


def jpeg_b64() -> str:
    out = io.BytesIO()
    Image.new("RGB", (40, 30), "white").save(out, format="JPEG")
    return base64.b64encode(out.getvalue()).decode()


def app_with(ask: FakeAsk | None = None, recap: RecapService | None = None) -> TestClient:
    app = create_app(Settings(_env_file=None, service_token=SecretStr(TOKEN)))
    ask = ask or FakeAsk(lambda request, model: solution(("", "4", "4")))
    solver = SolveService(ask, "strong", "stand-in")
    app.dependency_overrides[solve_service] = lambda: solver
    app.dependency_overrides[similar_service] = lambda: SimilarService(ask, "strong", "stand-in", solver)
    if recap is not None:
        app.dependency_overrides[recap_service] = lambda: recap
    return TestClient(app)


def test_solve_returns_the_verdict_both_solutions_and_the_cost():
    ask = FakeAsk(lambda request, model: solution(("a", "4", "4")))

    response = app_with(ask).post("/solve", json={"statement": "Aflați $x$.", "images": [jpeg_b64()]}, headers=HEADERS)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "AGREE" and len(body["runs"]) == 2
    assert body["runs"][0]["answers"] == [{"label": "a", "text": "4", "sympy": "4"}]
    assert body["input_tokens"] == 200 and body["output_tokens"] == 100 and body["cost_usd"] > 0
    assert all(request.images[0].startswith(b"\xff\xd8\xff") for request, _, _ in ask.calls)


def test_solve_with_no_answer_from_the_model_is_a_502_with_a_safe_message():
    response = app_with(FakeAsk(lambda request, model: None)).post("/solve", json={"statement": "x"}, headers=HEADERS)

    assert response.status_code == 502
    assert response.json() == {"detail": "Modelul nu a dat o rezolvare; încearcă din nou mai târziu."}


@pytest.mark.parametrize("path", ["/solve", "/similar-exercises", "/recap"])
@pytest.mark.parametrize("token", [None, "wrong"])
def test_every_new_endpoint_needs_the_service_token(path, token):
    ask = FakeAsk(lambda request, model: solution(("", "4", "4")))
    client = app_with(ask)

    response = client.post(path, json={}, headers={"X-Tutor-Token": token} if token else {})

    assert response.status_code == 401 and ask.calls == []


@pytest.mark.parametrize("body", [
    {"statement": ""},
    {"statement": "x" * 4001},
    {"statement": "x", "images": ["not base64!"]},
    {"statement": "x", "images": [base64.b64encode(b"GIF89a....").decode()]},
    {"statement": "x", "images": [jpeg_b64()] * 4},
])
def test_solve_bad_requests_are_refused_before_the_model(body):
    ask = FakeAsk(lambda request, model: solution(("", "4", "4")))

    assert app_with(ask).post("/solve", json=body, headers=HEADERS).status_code == 422
    assert ask.calls == []


def test_an_image_that_only_looks_like_a_jpeg_is_refused():
    fake = base64.b64encode(b"\xff\xd8\xff" + b"not really").decode()

    response = app_with().post("/solve", json={"statement": "x", "images": [fake]}, headers=HEADERS)

    assert response.status_code == 422


def test_similar_exercises_come_back_checked():
    def answer(request, model):
        if request.system.startswith("You write NEW"):
            return {"exercises": [{"statement": "Aflați $x$ din $3x = 12$.", "steps": ["Împărțim."], "kind": "NUMERIC",
                                   "difficulty": "same", "answers": [{"label": "", "text": "4", "sympy": "4"}]}]}
        return solution(("", "4", "4"))

    body = {"statement": "Aflați $x$ din $2x = 6$.", "steps": ["Împărțim la 2."],
            "answers": [{"label": "", "text": "3", "sympy": "3"}], "count": 1}

    response = app_with(FakeAsk(answer)).post("/similar-exercises", json=body, headers=HEADERS)

    assert response.status_code == 200
    exercise = response.json()["exercises"][0]
    assert exercise["status"] == "AGREE" and exercise["verified"] is True and exercise["figure_png"] is None
    assert response.json()["cost_usd"] > 0


@pytest.mark.parametrize("body", [
    {"statement": "x", "answers": []},
    {"statement": "x", "answers": [{"label": "", "text": "3"}], "count": 4},
    {"statement": "x", "answers": [{"label": "", "text": "3"}], "steps": ["s"] * 41},
])
def test_similar_bad_requests_are_refused_before_the_model(body):
    ask = FakeAsk(lambda request, model: solution(("", "4", "4")))

    assert app_with(ask).post("/similar-exercises", json=body, headers=HEADERS).status_code == 422
    assert ask.calls == []


def test_recap_answers_with_its_pages():
    def retriever(statement, slot):
        return usable(passage(1, code="G6", title="Teorema lui Pitagora", page=40))

    recap = RecapService(retriever, FakeModel(written("- $a^2 + b^2 = c^2$.", ids=(1,))).runnable)

    response = app_with(recap=recap).post("/recap", json={"exercises": [{"statement": "Ex.", "slot": "III.4"}]},
                                          headers=HEADERS)

    assert response.status_code == 200
    body = response.json()
    assert body["answered"] is True and body["text"] == "- $a^2 + b^2 = c^2$."
    assert body["sources"] == [{"n": 1, "code": "G6", "title": "Teorema lui Pitagora", "book_page": 40}]


@pytest.mark.parametrize("body", [
    {"exercises": []},
    {"exercises": [{"statement": "x"}] * 11},
    {"exercises": [{"statement": "x", "slot": "I.1; DROP"}]},
])
def test_recap_bad_requests_are_refused(body):
    calls = []
    recap = RecapService(lambda s, slot: calls.append(s) or Found("vector", [], None, "no_passages"),
                         FakeModel().runnable)

    assert app_with(recap=recap).post("/recap", json=body, headers=HEADERS).status_code == 422
    assert calls == []


def test_without_gemini_the_solver_says_so():
    app = create_app(Settings(_env_file=None, service_token=SecretStr(TOKEN)))

    def unavailable():
        raise SolveUnavailable("Rezolvarea exercițiilor merge doar cu LLM_PROVIDER=gemini.")

    app.dependency_overrides[solve_service] = unavailable

    response = TestClient(app).post("/solve", json={"statement": "x"}, headers=HEADERS)

    assert response.status_code == 503 and "LLM_PROVIDER=gemini" in response.json()["detail"]
