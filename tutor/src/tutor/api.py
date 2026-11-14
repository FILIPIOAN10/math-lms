"""The help chain over HTTP, for the backend only: one exercise in, up to three levels of help out, with the textbook
pages each level relied on. The browser never calls this service - Spring does, after checking who may ask, and the
teacher approves the text before any student sees it.

    POST /help    {"statement": ..., "slot": "I.1" | null, "levels": 1..3}  (header X-Tutor-Token)  ->  HelpResponse
    POST /explain {"question": ..., "history": [{"role": "user" | "bot", "text": ...}] (at most 6)}  ->  ExplainResponse
                  the student's "Nu înțeleg…" box: Spring has already checked the student, the daily limit and that no
                  graded test is open, and it keeps the history (this service keeps no state between requests)
    POST /mistake {"statement": ..., "slot": ..., "chosen": ..., "correct": ...}  ->  MistakeResponse
                  „De ce am greșit?": a draft for the teacher of why the chosen option is wrong, from the lessons
    GET  /health  {"status": "ok"}

The textbook passages stay in the database: the answer names them (chapter, page) but does not carry their text.

Run:  uv run uvicorn tutor.api:app --port 8001
"""

import hmac
from functools import lru_cache
from typing import Annotated, Literal

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from tutor.config import Settings, get_settings
from tutor.conversation import Turn
from tutor.explain import ExplainService
from tutor.mistake import MistakeService
from tutor.help_chain import LEVELS, HelpError, HelpService, HelpStep, Refusal, Source
from tutor.transcribe import cost_usd


class HelpRequest(BaseModel):
    statement: str = Field(min_length=1, max_length=4000)
    # The exam position ("I.1", "III.4"); anything else would only be a way to smuggle text into a query.
    slot: str | None = Field(default=None, max_length=10, pattern=r"^[IVX]{1,4}\.[0-9]{1,2}$")
    levels: int = Field(default=len(LEVELS), ge=1, le=len(LEVELS))


class PassageRef(BaseModel):
    n: int
    code: str
    title: str
    book_page: int
    distance: float | None


class HelpResponse(BaseModel):
    answered: bool
    steps: list[HelpStep]
    refusal: Refusal | None
    strategy: str
    best_distance: float | None
    passages: list[PassageRef]
    input_tokens: int
    output_tokens: int
    cost_usd: float


class TurnIn(BaseModel):
    role: Literal["user", "bot"]
    text: str = Field(min_length=1, max_length=1000)


class ExplainRequest(BaseModel):
    question: str = Field(min_length=1, max_length=500)
    # What was said before, oldest first: only so a short follow-up can be rewritten into a full question.
    history: list[TurnIn] = Field(default_factory=list, max_length=6)


class ExplainResponse(BaseModel):
    answered: bool
    text: str
    sources: list[Source]
    refusal: Refusal | None
    question: str  # the standalone question that was searched for (the teacher sees it when nothing was found)
    rewritten: bool
    input_tokens: int
    output_tokens: int
    cost_usd: float


class MistakeRequest(BaseModel):
    statement: str = Field(min_length=1, max_length=4000)
    slot: str | None = Field(default=None, max_length=10, pattern=r"^[IVX]{1,4}\.[0-9]{1,2}$")
    chosen: str = Field(min_length=1, max_length=1000)  # the wrong option a student chose
    correct: str | None = Field(default=None, max_length=1000)


class MistakeResponse(BaseModel):
    answered: bool
    text: str
    sources: list[Source]
    refusal: Refusal | None
    input_tokens: int
    output_tokens: int
    cost_usd: float


@lru_cache
def help_service() -> HelpService:
    """Built on first use (it needs the model access and the database), then shared; tests override it."""
    return HelpService.from_settings(get_settings())


@lru_cache
def explain_service() -> ExplainService:
    return ExplainService.from_settings(get_settings())


@lru_cache
def mistake_service() -> MistakeService:
    return MistakeService.from_settings(get_settings())


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(title="math-lms tutor", docs_url=None, redoc_url=None, openapi_url=None)

    def check_token(x_tutor_token: Annotated[str | None, Header()] = None) -> None:
        if settings.service_token is None:
            raise HTTPException(503, "Serviciul tutor nu are setat SERVICE_TOKEN.")
        expected = settings.service_token.get_secret_value().encode()
        if x_tutor_token is None or not hmac.compare_digest(x_tutor_token.encode(), expected):
            raise HTTPException(401, "Token lipsă sau greșit.")

    @app.get("/health")
    def health() -> dict:
        return {"status": "ok"}

    # A plain `def`: the chain is synchronous, so FastAPI runs it in its thread pool.
    @app.post("/help", response_model=HelpResponse, dependencies=[Depends(check_token)])
    def write_help(request: HelpRequest, service: Annotated[HelpService, Depends(help_service)]) -> HelpResponse:
        try:
            result = service.run(request.statement, slot=request.slot, levels=request.levels)
        except HelpError as e:
            raise HTTPException(502, str(e)) from e
        return HelpResponse(
            answered=result.answered,
            steps=result.steps,
            refusal=result.refusal,
            strategy=result.strategy,
            best_distance=result.best_distance,
            passages=[PassageRef(n=p.n, code=p.code, title=p.title, book_page=p.book_page, distance=p.distance)
                      for p in result.passages],
            input_tokens=result.input_tokens,
            output_tokens=result.output_tokens,
            cost_usd=round(cost_usd(result.input_tokens, result.output_tokens, settings), 6),
        )

    @app.post("/explain", response_model=ExplainResponse, dependencies=[Depends(check_token)])
    def write_explanation(request: ExplainRequest,
                          service: Annotated[ExplainService, Depends(explain_service)]) -> ExplainResponse:
        try:
            result = service.explain(request.question, [Turn(t.role, t.text) for t in request.history])
        except HelpError as e:
            raise HTTPException(502, str(e)) from e
        return ExplainResponse(
            answered=result.answered, text=result.text, sources=result.sources, refusal=result.refusal,
            question=result.question, rewritten=result.rewritten,
            input_tokens=result.input_tokens, output_tokens=result.output_tokens,
            cost_usd=round(cost_usd(result.input_tokens, result.output_tokens, settings), 6),
        )

    @app.post("/mistake", response_model=MistakeResponse, dependencies=[Depends(check_token)])
    def write_mistake(request: MistakeRequest,
                      service: Annotated[MistakeService, Depends(mistake_service)]) -> MistakeResponse:
        try:
            result = service.draft(request.statement, slot=request.slot, chosen=request.chosen,
                                   correct=request.correct)
        except HelpError as e:
            raise HTTPException(502, str(e)) from e
        return MistakeResponse(
            answered=result.answered, text=result.text, sources=result.sources, refusal=result.refusal,
            input_tokens=result.input_tokens, output_tokens=result.output_tokens,
            cost_usd=round(cost_usd(result.input_tokens, result.output_tokens, settings), 6),
        )

    return app


app = create_app()
