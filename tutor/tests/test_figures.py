"""Figures drawn by our code from the coordinates the model gives, and the free checks that they match the statement."""

import numpy as np

from tutor import figures


def P(name, x, y, z="0"):
    return {"name": name, "x": x, "y": y, "z": z}


def S(a, b, hidden=False):
    return {"a": a, "b": b, "hidden": hidden}


def plane(points, segments, marks=(), circles=()):
    return {"type": "plane", "points": points, "segments": segments, "circles": list(circles), "functions": [],
            "marks": list(marks)}


EQUILATERAL = plane([P("A", "3", "3*sqrt(3)"), P("B", "0", "0"), P("C", "6", "0"), P("D", "3", "0")],
                    [S("A", "B"), S("B", "C"), S("C", "A"), S("A", "D")],
                    [{"kind": "right_angle", "points": ["A", "D", "C"], "text": ""},
                     {"kind": "length", "points": ["B", "C"], "text": "6 cm"},
                     {"kind": "angle", "points": ["A", "B", "C"], "text": "60^\\circ"},
                     {"kind": "length", "points": ["A", "D"], "text": "3\\sqrt{3}"}])


def test_a_true_figure_passes_every_check():
    result = figures.check(EQUILATERAL, "Triunghiul echilateral $ABC$ are $BC = 6\\text{ cm}$ și $AD \\perp BC$.")

    assert result == {"ok": True, "problems": [], "warnings": []}


def test_a_length_written_on_the_figure_must_be_the_real_one():
    wrong = {**EQUILATERAL, "marks": [{"kind": "length", "points": ["A", "B"], "text": "5"}]}

    result = figures.check(wrong, "Triunghiul $ABC$.")

    assert not result["ok"] and "AB scrie 5" in result["problems"][0]


def test_the_lengths_and_angles_of_the_statement_must_hold_in_the_coordinates():
    statement = "În $\\triangle ABC$, $AB = BC = 7$ și $m(\\sphericalangle ABC) = 60^\\circ$."

    problems = figures.check(EQUILATERAL, statement)["problems"]

    assert any("AB = 7" in p for p in problems) and any("BC = 7" in p for p in problems)
    assert not any("ABC" in p for p in problems)  # the 60 degrees are right


def test_a_right_angle_mark_must_be_a_right_angle():
    wrong = {**EQUILATERAL, "marks": [{"kind": "right_angle", "points": ["A", "B", "C"], "text": ""}]}

    assert "nu e de 90°" in figures.check(wrong, "")["problems"][0]


def test_a_named_point_missing_from_the_figure_is_a_warning_but_the_circle_name_is_not_a_point():
    result = figures.check(EQUILATERAL, "Fie $M$ mijlocul lui $AB$ și cercul $\\mathcal{C}(O, r)$.")

    assert result["warnings"] == ["puncte din enunț care lipsesc din figură: M, O"]


def test_segments_ticked_alike_must_be_equal():
    isosceles = plane([P("A", "3", "5"), P("B", "0", "0"), P("C", "6", "0"), P("M", "3", "0")],
                      [S("A", "B"), S("A", "C"), S("B", "C"), S("A", "M")],
                      [{"kind": "ticks", "points": ["A", "B"], "text": "1"},
                       {"kind": "ticks", "points": ["A", "C"], "text": "1"},
                       {"kind": "ticks", "points": ["B", "M"], "text": "2"},
                       {"kind": "ticks", "points": ["M", "C"], "text": "2"}])
    wrong = {**isosceles, "marks": [{"kind": "ticks", "points": ["A", "B"], "text": "1"},
                                    {"kind": "ticks", "points": ["B", "C"], "text": "1"}]}

    assert figures.check(isosceles, "")["ok"]
    assert figures.check(wrong, "")["problems"] == ["AB și BC sunt marcate egale, dar au lungimi diferite"]
    assert figures.render(isosceles)[0].startswith(b"\x89PNG")


def test_a_diagonal_value_is_written_along_the_diagonal(monkeypatch):
    square = plane([P("A", "0", "0"), P("B", "6", "0"), P("C", "6", "6"), P("D", "0", "6")],
                   [S("A", "B"), S("B", "C"), S("C", "D"), S("D", "A"), S("A", "C"), S("B", "D")],
                   [{"kind": "length", "points": ["A", "C"], "text": "6\\sqrt{2}"},
                    {"kind": "length", "points": ["D", "A"], "text": "a"}])
    written = []
    real_text = figures.plt.Axes.text

    def spy(self, x, y, s, *args, **kwargs):
        written.append((s, kwargs.get("rotation", 0)))
        return real_text(self, x, y, s, *args, **kwargs)

    monkeypatch.setattr(figures.plt.Axes, "text", spy)
    figures.render(square)

    rotations = dict(written)
    assert rotations["$6\\sqrt{2}$"] == 45  # along the diagonal
    assert rotations["$a$"] == 0  # a single letter stays upright, even on a vertical side


def test_lesson_figures_draw_arcs_regions_round_solids_and_unit_marks():
    sector = plane([P("O", "0", "0"), P("A", "4", "0"), P("B", "2", "2*sqrt(3)")], [],
                   circles=[{"center": "O", "through": "A"}])
    sector["arcs"] = [{"center": "O", "start": "A", "end": "B", "sector": True, "shaded": True}]
    sector["regions"] = [{"points": ["O", "A", "B"], "hatched": True}]
    cylinder = {"type": "solid", "points": [P("O", "0", "0"), P("A", "3", "0"), P("O'", "0", "0", "6"),
                                            P("A'", "3", "0", "6")],
                "segments": [S("A", "A'"), S("O", "O'", True)], "functions": [], "marks": [],
                "circles": [{"center": "O", "through": "A", "back_hidden": True}, {"center": "O'", "through": "A'"}]}
    plane_xy = {"type": "graph", "units": True, "points": [P("O", "0", "0"), P("A", "2", "3")], "segments": [],
                "circles": [], "functions": [], "marks": []}

    for figure in (sector, cylinder, plane_xy):
        assert figures.check(figure, "")["ok"]
        assert figures.render(figure)[0].startswith(b"\x89PNG")


def test_a_round_solid_is_seen_from_the_front_so_its_bases_are_level_ellipses():
    cylinder = {"type": "solid", "points": [P("O", "0", "0"), P("A", "3", "0")], "segments": [], "functions": [],
                "marks": [], "circles": [{"center": "O", "through": "A"}]}
    space = {"O": np.zeros(3), "A": np.array([3.0, 0.0, 0.0])}

    assert figures._best_projection(cylinder, space) == (90.0, 0.35)


def test_number_lines_and_venn_diagrams_draw():
    line = {"type": "numberline", "points": [P("-2", "-2", "0"), P("\\sqrt{2}", "sqrt(2)", "0")], "segments": [],
            "circles": [], "functions": [], "marks": [],
            "intervals": [{"from": "-2", "to": "oo", "from_closed": True, "to_closed": False, "label": "[-2, \\infty)"}]}
    venn = {"type": "venn", "points": [], "segments": [], "circles": [], "functions": [], "marks": [],
            "venn": {"sets": ["A", "B"], "shade": ["A-B", "AnB"], "elements": [{"part": "AnB", "text": "3"}]}}
    backwards = {**line, "intervals": [{"from": "3", "to": "-2", "from_closed": True, "to_closed": True}]}

    for figure in (line, venn):
        assert figures.check(figure, "")["ok"]
        assert figures.render(figure)[0].startswith(b"\x89PNG")
    assert "capetele inversate" in figures.check(backwards, "")["problems"][0]
    assert not figures.check({**venn, "venn": {"sets": ["A"], "shade": []}}, "")["ok"]


def test_an_arc_goes_counterclockwise_from_start_towards_end():
    ring = figures._circle3d(np.zeros(3), np.array([2.0, 0.0, 0.0]), np.array([0.0, 5.0, 0.0]))

    assert np.allclose(ring[0], [2, 0, 0]) and np.allclose(ring[-1], [0, 2, 0], atol=1e-9)
    assert all(np.isclose(np.linalg.norm(p), 2) for p in ring)


def test_helper_points_are_not_drawn_and_label_points_have_no_dot(monkeypatch):
    lines = plane([P("_a1", "-5", "2") | {"style": "helper"}, P("_a2", "5", "2") | {"style": "helper"},
                   P("a", "5.6", "2") | {"style": "label"}, P("M_AB", "0", "0")], [S("_a1", "_a2")])
    written = []
    real_text = figures.plt.Axes.text
    monkeypatch.setattr(figures.plt.Axes, "text",
                        lambda self, x, y, s, *a, **k: written.append(s) or real_text(self, x, y, s, *a, **k))

    figures.render(lines)

    assert sorted(written) == ["$M_{AB}$", "$a$"]  # no "_a1"; the subscript AB is one subscript


def test_an_arc_that_starts_at_its_centre_or_a_two_corner_region_is_a_problem():
    bad = plane([P("O", "0", "0"), P("A", "1", "0")], [])
    bad["arcs"] = [{"center": "O", "start": "O", "end": "A"}]
    bad["regions"] = [{"points": ["O", "A"]}]

    assert len(figures.check(bad, "")["problems"]) == 2


def test_unreadable_coordinates_are_a_problem_and_code_is_never_evaluated():
    bad = plane([P("A", "__import__('os')", "0"), P("B", "1", "0")], [S("A", "B")])

    assert not figures.check(bad, "")["ok"]
    assert figures.parse("exp(1)") is None


def test_the_drawing_is_a_png_for_people_and_a_jpeg_for_the_model():
    png, jpeg = figures.render(EQUILATERAL)

    assert png.startswith(b"\x89PNG") and jpeg.startswith(b"\xff\xd8\xff")


def test_a_graph_and_a_solid_draw_too():
    graph = {"type": "graph", "points": [P("A", "Rational(1,2)", "0"), P("B", "0", "-1")], "segments": [],
             "circles": [], "functions": [{"expr": "2*x-1", "x_from": "-1", "x_to": "2", "label": "G_f"}], "marks": []}
    pyramid = {"type": "solid", "points": [P("A", "0", "0"), P("B", "6", "0"), P("C", "6", "6"), P("D", "0", "6"),
                                           P("V", "3", "3", "4")],
               "segments": [S("A", "B"), S("B", "C"), S("C", "D", True), S("D", "A", True), S("V", "A"), S("V", "B"),
                            S("V", "C"), S("V", "D", True)], "circles": [], "functions": [], "marks": []}

    assert figures.render(graph)[0].startswith(b"\x89PNG")
    assert figures.render(pyramid)[0].startswith(b"\x89PNG")


def test_a_solid_is_drawn_from_a_view_where_no_edge_lies_on_another():
    # A pool 12 x 8 x 2.5: in the textbook view the hidden edge DC falls exactly on the top edge A'B'.
    names = {"A": ("0", "0", "0"), "B": ("12", "0", "0"), "C": ("12", "8", "0"), "D": ("0", "8", "0"),
             "A'": ("0", "0", "5/2"), "B'": ("12", "0", "5/2"), "C'": ("12", "8", "5/2"), "D'": ("0", "8", "5/2")}
    box = {"type": "solid", "points": [P(n, *xyz) for n, xyz in names.items()], "circles": [], "functions": [],
           "marks": [], "segments": [S("A", "B"), S("B", "C"), S("C", "D", True), S("D", "A", True), S("A'", "B'"),
                                     S("B'", "C'"), S("C'", "D'"), S("D'", "A'"), S("A", "A'"), S("B", "B'"),
                                     S("C", "C'"), S("D", "D'", True)]}
    space = {n: np.array([float(figures.parse(v)) for v in xyz]) for n, xyz in names.items()}

    textbook = figures._project(space, 40, 0.5)
    chosen = figures._best_view(box, space)

    assert figures.view_issues(box, space, textbook) > 0
    assert figures.view_issues(box, space, chosen) == 0
