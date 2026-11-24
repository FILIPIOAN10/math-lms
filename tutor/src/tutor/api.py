"""The help chain over HTTP, for the backend only: one exercise in, up to three levels of help out, with the textbook
pages each level relied on. The browser never calls this service - Spring does, after checking who may ask, and the
teacher approves the text before any student sees it.

    POST /help    {"statement": ..., "slot": "I.1" | null, "levels": 1..3}  (header X-Tutor-Token)  ->  HelpResponse
    POST /explain {"question": ..., "history": [{"role": "user" | "bot", "text": ...}] (at most 6)}  ->  ExplainResponse
                  the student's "Nu înțeleg…" box: Spring has already checked the student, the daily limit and that no
                  graded test is open, and it keeps the history (this service keeps no state between requests)
    POST /mistake {"statement": ..., "slot": ..., "chosen": ..., "correct": ...}  ->  MistakeResponse
                  „De ce am greșit?": a draft for the teacher of why the chosen option is wrong, from the lessons
    POST /similar-index  ->  {"inserted", "unchanged", "removed"}
                  „Dă-mi una asemănătoare": embeds the new or changed statements (the backend's nightly job)
    POST /solve   {"statement": ..., "images": [base64 JPEG/PNG] (at most 3: the figure or the page)}  ->  SolveResponse
                  „Teme recapitulative": solved twice, independently, by the strong model; the status says whether the
                  final answers agree (SymPy). Takes a minute or two: the backend calls it from a worker.
    POST /similar-exercises {"statement", "steps", "answers", "images", "count": 1..3, "with_figure"}  ->  SimilarResponse
                  new exercises like a solved one, each solved again from scratch by a separate call; with a figure,
                  the figure comes back drawn by this service (PNG) and checked against the statement
    POST /recap   {"exercises": [{"statement", "slot"}] (1..10)}  ->  RecapResponse
                  the theory recap for a pack, drafted only from the lesson passages, with their pages
    GET  /health  {"status": "ok"}

The textbook passages stay in the database: the answer names them (chapter, page) but does not carry their text.

Run:  uv run uvicorn tutor.api:app --port 8001
"""

import base64
import binascii
import hmac
from collections.abc import Callable
from functools import lru_cache
from typing import Annotated, Literal

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, field_validator

from tutor.config import Settings, get_settings
from tutor.conversation import Turn
from tutor.explain import ExplainService
from tutor.mistake import MistakeService
from tutor.recap import RecapService
from tutor.lesson_index import run_lesson_index
from tutor.similar_index import IndexReport, run_index
from tutor.help_chain import LEVELS, HelpError, HelpService, HelpStep, Refusal, Source
from tutor.solver import (Answer, GeneratedExercise, SimilarService, SolveError, SolveRun, SolveService,
                          SolveUnavailable, strong_cost_usd)
from tutor.transcribe import cost_usd, prepare

SLOT = r"^[IVX]{1,4}\.[0-9]{1,2}$"
MAX_IMAGE_BYTES = 6 * 1024 * 1024
_IMAGE_MAGIC = (b"\xff\xd8\xff", b"\x89PNG\r\n\x1a\n")


def _image(value: str) -> str:
    """A base64 JPEG or PNG no larger than MAX_IMAGE_BYTES; anything else is refused before any model is asked."""
    try:
        raw = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError) as error:
        raise ValueError("imaginea nu e base64 valid") from error
    if len(raw) > MAX_IMAGE_BYTES:
        raise ValueError("imaginea e prea mare")
    if not raw.startswith(_IMAGE_MAGIC):
        raise ValueError("imaginea trebuie să fie JPEG sau PNG")
    return value


def _jpegs(images: list[str]) -> tuple[bytes, ...]:
    """The images as the model gets them: JPEG, long side at most 1800 px."""
    try:
        return tuple(prepare(base64.b64decode(image), 1800)[0] for image in images)
    except OSError as error:  # Pillow could not read it, although the header looked right
        raise HTTPException(422, "O imagine nu poate fi citită.") from error


class SolveRequest(BaseModel):
    statement: str = Field(min_length=1, max_length=4000)
    images: list[str] = Field(default_factory=list, max_length=3)

    @field_validator("images")
    @classmethod
    def images_are_pictures(cls, images: list[str]) -> list[str]:
        return [_image(image) for image in images]


class SolveResponse(BaseModel):
    status: str
    runs: list[SolveRun]
    input_tokens: int
    output_tokens: int
    cost_usd: float


class AnswerIn(BaseModel):
    label: str = Field(max_length=200)
    text: str = Field(max_length=1000)
    sympy: str | None = Field(default=None, max_length=500)


class SimilarRequest(BaseModel):
    statement: str = Field(min_length=1, max_length=4000)
    steps: list[Annotated[str, Field(max_length=2000)]] = Field(default_factory=list, max_length=40)
    answers: list[AnswerIn] = Field(min_length=1, max_length=10)
    images: list[str] = Field(default_factory=list, max_length=3)
    count: int = Field(default=2, ge=1, le=3)
    with_figure: bool = False

    @field_validator("images")
    @classmethod
    def images_are_pictures(cls, images: list[str]) -> list[str]:
        return [_image(image) for image in images]


class SimilarResponse(BaseModel):
    exercises: list[GeneratedExercise]
    input_tokens: int
    output_tokens: int
    cost_usd: float


class RecapItem(BaseModel):
    statement: str = Field(min_length=1, max_length=4000)
    slot: str | None = Field(default=None, max_length=10, pattern=SLOT)


class RecapRequest(BaseModel):
    exercises: list[RecapItem] = Field(min_length=1, max_length=10)


class RecapResponse(BaseModel):
    answered: bool
    text: str
    sources: list[Source]
    refusal: Refusal | None
    input_tokens: int
    output_tokens: int
    cost_usd: float


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


def similar_indexer() -> Callable[[], IndexReport]:
    """What POST /similar-index runs; tests override it."""
    return run_index


def lesson_indexer() -> Callable[[], IndexReport]:
    """What POST /lesson-index runs; tests override it."""
    return run_lesson_index


@lru_cache
def solve_service() -> SolveService:
    return SolveService.from_settings(get_settings())


@lru_cache
def similar_service() -> SimilarService:
    return SimilarService.from_settings(get_settings())


@lru_cache
def recap_service() -> RecapService:
    return RecapService.from_settings(get_settings())


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(title="math-lms tutor", docs_url=None, redoc_url=None, openapi_url=None)

    def check_token(x_tutor_token: Annotated[str | None, Header()] = None) -> None:
        if settings.service_token is None:
            raise HTTPException(503, "Serviciul tutor nu are setat SERVICE_TOKEN.")
        expected = settings.service_token.get_secret_value().encode()
        if x_tutor_token is None or not hmac.compare_digest(x_tutor_token.encode(), expected):
            raise HTTPException(401, "Token lipsă sau greșit.")

    @app.exception_handler(SolveUnavailable)
    def unavailable(_request: Request, error: SolveUnavailable) -> JSONResponse:
        return JSONResponse({"detail": str(error)}, status_code=503)

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

    # Plain `def`s: the model calls are synchronous and slow (minutes), so FastAPI runs them in its thread pool.
    @app.post("/solve", response_model=SolveResponse, dependencies=[Depends(check_token)])
    def solve(request: SolveRequest, service: Annotated[SolveService, Depends(solve_service)]) -> SolveResponse:
        try:
            solved = service.solve(request.statement, _jpegs(request.images))
        except SolveError as e:
            raise HTTPException(502, str(e)) from e
        return SolveResponse(
            status=solved.status, runs=solved.runs, input_tokens=solved.input_tokens,
            output_tokens=solved.output_tokens,
            cost_usd=round(strong_cost_usd(solved.input_tokens, solved.output_tokens, settings), 6))

    @app.post("/similar-exercises", response_model=SimilarResponse, dependencies=[Depends(check_token)])
    def similar_exercises(request: SimilarRequest,
                          service: Annotated[SimilarService, Depends(similar_service)]) -> SimilarResponse:
        try:
            generated = service.generate(
                request.statement, request.steps, [Answer(**a.model_dump()) for a in request.answers],
                images=_jpegs(request.images), count=request.count, with_figure=request.with_figure)
        except SolveError as e:
            raise HTTPException(502, str(e)) from e
        return SimilarResponse(
            exercises=generated.exercises, input_tokens=generated.input_tokens, output_tokens=generated.output_tokens,
            cost_usd=round(strong_cost_usd(generated.input_tokens, generated.output_tokens, settings), 6))

    @app.post("/recap", response_model=RecapResponse, dependencies=[Depends(check_token)])
    def write_recap(request: RecapRequest, service: Annotated[RecapService, Depends(recap_service)]) -> RecapResponse:
        try:
            result = service.recap([(item.statement, item.slot) for item in request.exercises])
        except HelpError as e:
            raise HTTPException(502, str(e)) from e
        return RecapResponse(
            answered=result.answered, text=result.text, sources=result.sources, refusal=result.refusal,
            input_tokens=result.input_tokens, output_tokens=result.output_tokens,
            cost_usd=round(cost_usd(result.input_tokens, result.output_tokens, settings), 6))

    # A plain `def`: embedding and writing are synchronous, so FastAPI runs it in its thread pool.
    @app.post("/similar-index", dependencies=[Depends(check_token)])
    def write_similar_index(run: Annotated[Callable[[], IndexReport], Depends(similar_indexer)]) -> dict:
        report = run()
        return {"inserted": report.inserted, "unchanged": report.unchanged, "removed": report.removed}

    # Plan step 6: the approved and shown lessons, embedded for the search (the backend calls it after an approval or a
    # show / hide). A plain `def`, like /similar-index.
    @app.post("/lesson-index", dependencies=[Depends(check_token)])
    def write_lesson_index(run: Annotated[Callable[[], IndexReport], Depends(lesson_indexer)]) -> dict:
        report = run()
        return {"inserted": report.inserted, "unchanged": report.unchanged, "removed": report.removed}

    return app


app = create_app()
