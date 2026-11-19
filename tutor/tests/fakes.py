"""Stand-ins for the model and the retriever, so the chain and the conversation are tested with no network."""

from langchain_core.runnables import RunnableLambda

from tutor.conversation import Condensed
from tutor.help_chain import Draft, Drafted, Found, HelpService, Passage


def passage(n: int = 1, code: str = "AIS5P5", title: str = "Ordinea efectuării operațiilor", page: int = 12,
            content: str = "Întâi parantezele, apoi înmulțirea, apoi adunarea.") -> Passage:
    return Passage(n=n, code=code, title=title, book_page=page, chunk_id=n, content=content)


def usable(*passages: Passage, strategy: str = "slot") -> Found:
    return Found(strategy, list(passages) or [passage()], None)


def written(text: str = "Începe cu paranteza.", ids: tuple[int, ...] = (1,), covered: bool = True,
            tokens: tuple[int, int] = (100, 20)) -> Drafted:
    return Drafted(Draft(covered=covered, text=text, source_ids=list(ids)), *tokens)


class FakeModel:
    """Answers with the drafts it was given, in order, and remembers every prompt it was shown."""

    def __init__(self, *drafts: Drafted):
        self.drafts = list(drafts)
        self.prompts: list[str] = []

    def __call__(self, prompt_value) -> Drafted:
        self.prompts.append("\n".join(message.content for message in prompt_value.to_messages()))
        return self.drafts.pop(0)

    @property
    def runnable(self) -> RunnableLambda:
        return RunnableLambda(self)


def service(model: FakeModel, found: Found | None = None) -> tuple[HelpService, list]:
    calls: list = []

    def retriever(statement: str, slot: str | None) -> Found:
        calls.append((statement, slot))
        return found or usable()

    return HelpService(retriever, model.runnable), calls


class FakeAsk:
    """The strong model for the solver: `answer(request, model)` returns the JSON dict (or None for an empty answer, or
    an `Asked`). Remembers every call; the solver asks from several threads at once, so the list is locked."""

    def __init__(self, answer):
        import threading

        self.answer = answer
        self.calls: list = []
        self._lock = threading.Lock()

    def __call__(self, request, model: str, attempts: int):
        from tutor.solver import Asked

        with self._lock:
            self.calls.append((request, model, attempts))
        result = self.answer(request, model)
        if isinstance(result, Asked):
            return result
        return Asked(result, model, 100, 50) if result is not None else Asked(None, model, 10, 0, "răspuns gol")


def solution(*answers: tuple[str, str, str | None], kind: str = "NUMERIC", steps: tuple[str, ...] = ("Pasul 1.",),
             needs_figure: bool = False) -> dict:
    """What the solving model returns: answers as (label, text, sympy)."""
    return {"kind": kind, "steps": list(steps), "answers": [{"label": l, "text": t, "sympy": s} for l, t, s in answers],
            "needs_figure": needs_figure, "unsure": False}


class FakeCondenser:
    """Answers the rewrite prompt with the texts it was given, in order, and remembers every prompt it was shown."""

    def __init__(self, *texts: str | Condensed):
        self.texts = list(texts)
        self.prompts: list[str] = []

    def __call__(self, prompt_value) -> Condensed:
        self.prompts.append("\n".join(message.content for message in prompt_value.to_messages()))
        text = self.texts.pop(0)
        return text if isinstance(text, Condensed) else Condensed(text, 20, 5)

    @property
    def runnable(self) -> RunnableLambda:
        return RunnableLambda(self)
