import io

from fakes import FakeCondenser, FakeModel, passage, service, usable, written
from langchain_core.runnables import RunnableLambda

from tutor.cli import format_reply, run_session
from tutor.conversation import Conversation
from tutor.help_chain import Found, HelpService


def session(lines: list[str], model: FakeModel, condenser: FakeCondenser | None = None, **options):
    help_service, searches = service(model, usable(passage(1), passage(2, "AIS5P1", "Puteri", 11)))
    talk = Conversation(help_service, (condenser or FakeCondenser()).runnable)
    out = io.StringIO()
    tokens = run_session(lines, talk, out, **options)
    return out.getvalue(), tokens, talk, searches


def test_an_exercise_gets_its_first_level_with_the_pages_it_came_from():
    text, _, _, _ = session(["Calculează 20 - 2·(7 - 2)."], FakeModel(written("Indiciu A")))

    assert "Treapta 1:\nIndiciu A\nSurse: Ordinea efectuării operațiilor (pag. 12)" in text


def test_mai_gives_the_next_levels_in_order():
    text, _, _, _ = session(["Calculează 2 + 3.", "/mai", "/mai", "/mai"],
                            FakeModel(written("A"), written("B"), written("C")))

    assert text.index("Treapta 1:") < text.index("Treapta 2:") < text.index("Treapta 3:")
    assert "soluția completă" in text  # the fourth ask has nothing left to give


def test_a_follow_up_shows_how_it_was_understood():
    text, _, _, searches = session(["Calculează 2 + 3.", "și dacă e 4?"], FakeModel(written("A"), written("B")),
                                   FakeCondenser("Calculează 2 + 4."))

    assert "(am înțeles: Calculează 2 + 4.)" in text
    assert searches[-1][0] == "Calculează 2 + 4."


def test_the_exam_position_is_set_shown_cleared_and_used_for_the_next_question():
    text, _, _, searches = session(
        ["/loc", "/loc I.1", "/loc", "Calculează 2 + 3.", "/loc -", "/loc"], FakeModel(written("A")))

    assert text.splitlines() == ["Niciun loc din test setat.", "Loc din test: I.1", "Loc din test: I.1",
                                 "Treapta 1:", "A", "Surse: Ordinea efectuării operațiilor (pag. 12)",
                                 "Locul din test șters.", "Niciun loc din test setat."]
    assert searches == [("Calculează 2 + 3.", "I.1")]


def test_the_starting_position_applies_until_it_is_changed():
    _, _, _, searches = session(["Calculează 2 + 3."], FakeModel(written("A")), slot="I.2")

    assert searches == [("Calculează 2 + 3.", "I.2")]


def test_nou_forgets_the_conversation_and_the_position():
    text, _, talk, searches = session(["/loc I.1", "Calculează 2 + 3.", "/nou", "Calculează 5 + 5.", "/istoric"],
                                      FakeModel(written("A"), written("B")))

    assert searches[-1] == ("Calculează 5 + 5.", None)
    assert "Conversația a fost uitată." in text
    assert text.count("Elev: Calculează 2 + 3.") == 0 and "Elev: Calculează 5 + 5." in text


def test_passages_are_shown_only_when_asked_and_only_for_a_new_question():
    text, _, _, _ = session(["/pasaje", "Calculează 2 + 3.", "/mai"], FakeModel(written("A"), written("B")))

    assert text.count("[1] AIS5P5 - Ordinea efectuării operațiilor (pag. 12)") == 1  # not repeated for level 2
    assert "Pasajele se arată." in text and "Pasaje (slot):" in text


def test_commands_that_do_not_exist_and_blank_lines_are_handled_quietly():
    text, tokens, _, _ = session(["", "   ", "/zzz"], FakeModel())

    assert text == "Comandă necunoscută: /zzz. Scrie /ajutor.\n" and tokens == (0, 0)


def test_iesire_stops_before_the_lines_that_follow():
    text, _, talk, searches = session(["/iesire", "Calculează 2 + 3."], FakeModel(written("A")))

    assert text == "" and searches == [] and talk.current is None


def test_a_failure_of_the_provider_is_reported_and_the_session_goes_on():
    class Boom(FakeModel):
        def __call__(self, prompt_value):
            if not self.drafts:
                raise TimeoutError("cererea a expirat\nal doilea rând care nu se arată")
            return super().__call__(prompt_value)

    text, _, _, _ = session(["Calculează 2 + 3.", "/mai", "/ajutor"], Boom(written("A")))

    assert "Eroare (TimeoutError): cererea a expirat" in text and "al doilea rând" not in text
    assert "/iesire" in text  # still answering commands afterwards


def test_tokens_are_added_up_over_the_session():
    _, tokens, _, _ = session(["Calculează 2 + 3.", "/mai"],
                              FakeModel(written(tokens=(100, 10)), written(tokens=(150, 30))))

    assert tokens == (250, 40)


def test_input_that_is_not_typed_can_echo_its_lines_so_a_transcript_reads_like_a_session():
    text, _, _, _ = session(["Calculează 2 + 3."], FakeModel(written("A")), echo=True)

    assert text.startswith("> Calculează 2 + 3.\nTreapta 1:")


def test_a_refusal_prints_the_message_and_the_reason():
    refused = Found("vector", [passage()], 0.61, "too_far")
    talk = Conversation(HelpService(lambda statement, slot: refused, RunnableLambda(lambda _: None)),
                        FakeCondenser().runnable)

    text = format_reply(talk.ask("Cum calculez aria unui cerc?"), show_passages=False)

    assert text == "Nu am asta în manual.  [motiv: too_far]"
