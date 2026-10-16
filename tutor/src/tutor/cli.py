"""Command line for trying the help chain by hand: paste an exercise, ask for more, follow up with a short message.

Run:  uv run python -X utf8 -m tutor.cli [--slot I.1] [--show-passages]
      uv run python -X utf8 -m tutor.cli < session.txt     (one message per line: a scripted demo)

Plain text is a question or an exercise (a short one after the first is first rewritten with the recent messages, so
"și dacă x < 0?" works); a line starting with "/" is a command. All the logic is in `Conversation`; this file only
reads lines and prints, so the HTTP service reuses the same class.
"""

import argparse
import sys
from collections.abc import Iterable, Iterator
from typing import TextIO

from tutor.config import get_settings
from tutor.conversation import Conversation, Reply
from tutor.help_chain import passage_lines, refusal_line, step_lines
from tutor.transcribe import cost_usd

HELP = """\
Scrie un enunț sau o întrebare (și continuări scurte, ca „și dacă x < 0?").
  /mai        următoarea treaptă a întrebării deschise (2, apoi 3)
  /loc I.1    locul din test pentru întrebările următoare ("/loc -" îl șterge, "/loc" îl arată)
  /pasaje     arată / ascunde pasajele aduse (fără text)
  /istoric    mesajele păstrate acum
  /nou        uită conversația și locul din test
  /ajutor     acest text
  /iesire     închide (sau Ctrl+Z, Enter pe Windows / Ctrl+D)"""

QUIT = {"/iesire", "/exit", "/quit"}


def format_reply(reply: Reply, show_passages: bool) -> str:
    lines: list[str] = []
    if reply.rewritten:
        lines.append(f"(am înțeles: {reply.question})")
    if show_passages and reply.found and (reply.step is None or reply.step.level == 1):
        lines.append(f"Pasaje ({reply.found.strategy}"
                     + (f", cea mai apropiată {reply.found.best_distance:.2f}" if reply.found.best_distance is not None else "")
                     + "):")
        lines += passage_lines(reply.found.passages)
    if reply.step:
        lines += step_lines(reply.step)
    if reply.refusal:
        lines.append(refusal_line(reply.refusal))
    if reply.notice:
        lines.append(reply.notice)
    return "\n".join(lines)


def run_session(lines: Iterable[str], conversation: Conversation, out: TextIO, *, slot: str | None = None,
                show_passages: bool = False, echo: bool = False) -> tuple[int, int]:
    """Handles the lines one by one; returns the (input, output) tokens spent."""
    tokens = [0, 0]

    def say(text: str) -> None:
        print(text, file=out)

    def show(reply: Reply) -> None:
        tokens[0] += reply.input_tokens
        tokens[1] += reply.output_tokens
        say(format_reply(reply, show_passages))

    for raw in lines:
        line = raw.strip()
        if not line:
            continue
        if echo:
            say(f"> {line}")
        command, _, argument = line.partition(" ")
        argument = argument.strip()
        try:
            if command in QUIT:
                break
            elif command == "/ajutor":
                say(HELP)
            elif command == "/mai":
                show(conversation.more())
            elif command == "/loc":
                if argument == "-":
                    slot = None
                    say("Locul din test șters.")
                elif argument:
                    slot = argument
                    say(f"Loc din test: {slot}")
                else:
                    say(f"Loc din test: {slot}" if slot else "Niciun loc din test setat.")
            elif command == "/pasaje":
                show_passages = not show_passages
                say("Pasajele se arată." if show_passages else "Pasajele sunt ascunse.")
            elif command == "/istoric":
                for turn in conversation.history:
                    text = turn.text if len(turn.text) <= 120 else turn.text[:120] + "…"
                    say(f"{'Elev' if turn.role == 'user' else 'Tutor'}: {text}")
                if not conversation.history:
                    say("Nimic păstrat încă.")
            elif command == "/nou":
                conversation.reset()
                slot = None
                say("Conversația a fost uitată.")
            elif command.startswith("/"):
                say(f"Comandă necunoscută: {command}. Scrie /ajutor.")
            else:
                show(conversation.ask(line, slot=slot))
        except Exception as error:  # a network or provider problem must not end the session
            say(f"Eroare ({type(error).__name__}): {str(error).splitlines()[0][:200] if str(error) else ''}")
    return tokens[0], tokens[1]


def _typed_lines(prompt: str = "> ") -> Iterator[str]:
    while True:
        try:
            yield input(prompt)
        except EOFError:
            return


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Încearcă lanțul de ajutor de la tastatură.")
    parser.add_argument("--slot", help="locul din test pentru întrebări (ex. I.1)")
    parser.add_argument("--show-passages", action="store_true", help="arată pasajele aduse (fără text)")
    args = parser.parse_args(argv)

    sys.stdout.reconfigure(encoding="utf-8")
    sys.stdin.reconfigure(encoding="utf-8")
    settings = get_settings()
    conversation = Conversation.from_settings(settings)
    interactive = sys.stdin.isatty()
    if interactive:
        print(HELP + "\n")
    tokens_in, tokens_out = run_session(_typed_lines() if interactive else sys.stdin, conversation, sys.stdout,
                                        slot=args.slot, show_passages=args.show_passages, echo=not interactive)
    print(f"\nTokeni: {tokens_in} intrare, {tokens_out} ieșire (~{cost_usd(tokens_in, tokens_out, settings):.5f} USD)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
