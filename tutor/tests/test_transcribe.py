import io
import json

import httpx
import pytest
from PIL import Image

from tutor.config import Settings
from tutor.transcribe import (
    TranscriptionError,
    build_request,
    cost_usd,
    parse_pages,
    parse_response,
    pdf_pages,
    prepare,
    transcribe_page,
)


def settings(**overrides) -> Settings:
    return Settings(_env_file=None, openai_api_key="sk-test", **overrides)


def photo(width: int, height: int) -> bytes:
    buffer = io.BytesIO()
    Image.new("RGB", (width, height), "white").save(buffer, format="PNG")
    return buffer.getvalue()


def answer(page_number=9, markdown="# I. Mulțimea numerelor naturale", usage=(1200, 300)) -> dict:
    payload = {"page_number": page_number, "markdown": markdown, "warnings": []}
    return {
        "status": "completed",
        "model": "gpt-6-luna",
        "output": [
            {"type": "reasoning", "summary": []},
            {"type": "message", "content": [{"type": "output_text", "text": json.dumps(payload)}]},
        ],
        "usage": {"input_tokens": usage[0], "output_tokens": usage[1]},
    }


def test_prepare_shrinks_the_long_edge_and_returns_jpeg():
    jpeg, width, height = prepare(photo(3000, 2000), max_side=1800)

    assert max(width, height) == 1800
    assert (width, height) == (1800, 1200)
    assert jpeg[:2] == b"\xff\xd8"  # JPEG magic number


def test_prepare_never_enlarges_a_small_image():
    _, width, height = prepare(photo(400, 300), max_side=1800)

    assert (width, height) == (400, 300)


def test_request_asks_for_strict_json_and_stores_nothing():
    body = build_request(b"jpeg-bytes", settings())

    assert body["store"] is False
    assert body["text"]["format"]["strict"] is True
    assert body["reasoning"] == {"effort": "low"}
    image = body["input"][0]["content"][1]
    assert image["type"] == "input_image"
    assert image["image_url"].startswith("data:image/jpeg;base64,")
    assert "UNIVERSUL MATEMATIC" in body["instructions"]  # the watermark is named so it can be ignored


def test_parse_reads_text_page_number_and_usage():
    result = parse_response(answer(page_number=10, markdown="## 2.2 Scăderea"), "fallback")

    assert result.page_number == 10
    assert result.markdown == "## 2.2 Scăderea"
    assert (result.input_tokens, result.output_tokens) == (1200, 300)


def test_parse_rejects_a_truncated_answer():
    truncated = answer()
    truncated["status"] = "incomplete"

    with pytest.raises(TranscriptionError):
        parse_response(truncated, "fallback")


def test_cost_uses_the_configured_prices():
    config = settings(price_in_per_million=0.10, price_out_per_million=0.50)

    assert cost_usd(1_000_000, 1_000_000, config) == pytest.approx(0.60)


def test_a_401_becomes_a_clear_message_without_the_key():
    transport = httpx.MockTransport(lambda request: httpx.Response(401, json={"error": {"message": "bad key"}}))
    with httpx.Client(transport=transport) as client:
        with pytest.raises(TranscriptionError) as error:
            transcribe_page(b"x", settings(), client)

    assert "sk-test" not in str(error.value)


def test_transcribe_page_sends_the_bearer_key_and_parses_the_answer():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["auth"] = request.headers["authorization"]
        seen["url"] = str(request.url)
        return httpx.Response(200, json=answer())

    with httpx.Client(transport=httpx.MockTransport(handler)) as client:
        result = transcribe_page(b"x", settings(), client)

    assert seen["auth"] == "Bearer sk-test"
    assert seen["url"].endswith("/responses")
    assert result.page_number == 9


def test_missing_key_is_reported_before_any_request():
    config = Settings(_env_file=None)  # no key
    with httpx.Client(transport=httpx.MockTransport(lambda r: pytest.fail("no request expected"))) as client:
        with pytest.raises(TranscriptionError):
            transcribe_page(b"x", config, client)


def test_pdf_pages_extracts_one_image_per_page(tmp_path):
    pdf = tmp_path / "scan.pdf"
    first = Image.new("RGB", (900, 600), "white")
    second = Image.new("RGB", (600, 900), "white")
    first.save(pdf, format="PDF", save_all=True, append_images=[second])

    pages = pdf_pages(pdf, max_side=500)

    assert [p.pdf_page for p in pages] == [1, 2]
    assert max(pages[0].width, pages[0].height) == 500


def test_parse_pages_accepts_ranges_and_lists():
    assert parse_pages("1-3,5") == {1, 2, 3, 5}
    assert parse_pages(None) is None
    with pytest.raises(ValueError):
        parse_pages("a-b")
