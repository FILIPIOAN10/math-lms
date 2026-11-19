"""Figures for generated exercises: the model gives exact coordinates, this module draws them and checks them.

A figure is JSON (FIGURE_SCHEMA): points with exact SymPy coordinates, segments (hidden edges dashed), circles, function
graphs and the marks a textbook drawing shows (right angles, lengths, angles). Drawing is matplotlib, no AI call. The
checks cost nothing either: a length or an angle written on the figure must be the one the coordinates give, a
right-angle mark must be a right angle, and every point the statement names should be in the figure.

Solids use the textbook view: x to the right, y into the page (drawn at 40 degrees, half length), z up.
"""

import io
import math
import re
import threading

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import sympy  # noqa: E402
from matplotlib.mathtext import MathTextParser  # noqa: E402

_STR = {"type": "string"}
_BOOL = {"type": "boolean"}
# The optional parts (a point's style, a circle's dashed back half, arcs, shaded regions) may be left out: an exercise
# figure seldom needs them, a lesson figure often does.
FIGURE_SCHEMA = {
    "type": "object",
    "properties": {
        "type": {"type": "string", "enum": ["plane", "solid", "graph"]},
        # style: "point" = a dot and its name (the default); "label" = only the name, no dot (a line "d", a circle "C");
        # "helper" = nothing drawn (the far end of a line, a corner of a shaded region).
        "points": {"type": "array", "items": {"type": "object", "properties": {
            "name": _STR, "x": _STR, "y": _STR, "z": _STR,
            "style": {"type": "string", "enum": ["point", "label", "helper"]}},
            "required": ["name", "x", "y", "z"]}},
        "segments": {"type": "array", "items": {"type": "object", "properties": {"a": _STR, "b": _STR, "hidden": _BOOL},
                                                "required": ["a", "b", "hidden"]}},
        # In a solid, a circle whose centre and `through` point have the same z is horizontal and is drawn as an ellipse;
        # back_hidden dashes its far half (the bottom base of a cylinder or a cone).
        "circles": {"type": "array", "items": {"type": "object", "properties": {
            "center": _STR, "through": _STR, "back_hidden": _BOOL}, "required": ["center", "through"]}},
        # An arc of the circle of centre `center` through `start`, counterclockwise to the direction of `end`;
        # sector = also the two radii; shaded = the sector filled.
        "arcs": {"type": "array", "items": {"type": "object", "properties": {
            "center": _STR, "start": _STR, "end": _STR, "sector": _BOOL, "shaded": _BOOL, "hidden": _BOOL},
            "required": ["center", "start", "end"]}},
        # A shaded polygon, its corners in order.
        "regions": {"type": "array", "items": {"type": "object", "properties": {
            "points": {"type": "array", "items": _STR}, "hatched": _BOOL}, "required": ["points"]}},
        "functions": {"type": "array", "items": {"type": "object", "properties": {"expr": _STR, "x_from": _STR, "x_to": _STR, "label": _STR},
                                                 "required": ["expr", "x_from", "x_to", "label"]}},
        "marks": {"type": "array", "items": {"type": "object", "properties": {
            "kind": {"type": "string", "enum": ["right_angle", "length", "angle", "ticks"]},
            "points": {"type": "array", "items": _STR}, "text": _STR}, "required": ["kind", "points", "text"]}},
    },
    "required": ["type", "points", "segments", "circles", "functions", "marks"],
}
# A graph can show the unit marks of its axes (1, 2, 3 ... on each axis), as a lesson about the coordinate plane does.
FIGURE_SCHEMA["properties"]["units"] = _BOOL
# A number line (type "numberline"): the points are the marked numbers (x = the value, name = what is printed under it)
# and each interval is drawn above the line, "oo" / "-oo" for an end that goes on, closed ends filled, open ends hollow.
FIGURE_SCHEMA["properties"]["type"]["enum"] = ["plane", "solid", "graph", "numberline", "venn"]
FIGURE_SCHEMA["properties"]["intervals"] = {"type": "array", "items": {"type": "object", "properties": {
    "from": _STR, "to": _STR, "from_closed": _BOOL, "to_closed": _BOOL, "label": _STR},
    "required": ["from", "to", "from_closed", "to_closed"]}}
# A Venn diagram of two sets (type "venn"): which parts are shaded, and what is written in each part.
_VENN_PARTS = ["A-B", "B-A", "AnB", "outside"]
FIGURE_SCHEMA["properties"]["venn"] = {"type": "object", "properties": {
    "sets": {"type": "array", "items": _STR},
    "shade": {"type": "array", "items": {"type": "string", "enum": _VENN_PARTS[:3]}},
    "elements": {"type": "array", "items": {"type": "object", "properties": {
        "part": {"type": "string", "enum": _VENN_PARTS}, "text": _STR}, "required": ["part", "text"]}},
    "universe": _STR},
    "required": ["sets", "shade"]}

_NAMES = ("sqrt", "Rational", "pi", "sin", "cos", "tan", "Abs", "oo")
_SAFE = re.compile(r"^[0-9A-Za-z_+\-*/^().,\s]+$")
_VIEWS = [(40, 0.5), (30, 0.5), (50, 0.5), (35, 0.6), (45, 0.4), (25, 0.45), (55, 0.55), (20, 0.6)]  # (angle, depth)


def parse(text, allow_x: bool = False):
    """An exact SymPy value from a short string, or None. Only numbers, + - * / ** and the listed names are allowed."""
    text = str(text).strip() if text is not None else ""
    if not text or not _SAFE.match(text) or "__" in text:
        return None
    allowed = set(_NAMES) | ({"x"} if allow_x else set())
    if any(name not in allowed for name in re.findall(r"[A-Za-z_]\w*", text)):
        return None
    local = {n: getattr(sympy, n) for n in _NAMES}
    if allow_x:
        local["x"] = sympy.Symbol("x")
    scope: dict = {}
    exec("from sympy import Integer, Float, Rational, Symbol", scope)  # what parse_expr's number handling needs
    scope.update(local)
    try:
        return sympy.parse_expr(text.replace("^", "**"), local_dict=local, global_dict=scope)
    except Exception:  # noqa: BLE001 - anything unparsable is "not a value"
        return None


def latex_value(text: str):
    """The number written on a figure ("6 cm", "4\\sqrt{3}", "60^\\circ", "2,5"), or None when it is a letter or a word."""
    t = text.replace("$", "")
    t = re.sub(r"\\(text|mathrm)\s*\{[^{}]*\}", "", t)
    t = re.sub(r"\\[,;! ]|~", "", t)
    t = re.sub(r"\^\s*\{?\s*\\circ\s*\}?|°|\\degree", "", t)
    t = re.sub(r"\s*\b(cm|dm|mm|km|m)\b\.?", "", t)
    t = re.sub(r"\\sqrt\s*\{([^{}]+)\}", r"sqrt(\1)", t)
    t = re.sub(r"\\sqrt\s*(\d+)", r"sqrt(\1)", t)
    t = re.sub(r"\\frac\s*\{([^{}]+)\}\s*\{([^{}]+)\}", r"((\1)/(\2))", t)
    t = re.sub(r"\\(cdot|times)", "*", t)
    t = t.replace(",", ".").replace("{", "(").replace("}", ")")
    t = re.sub(r"(\d|\))\s*(sqrt|\()", r"\1*\2", t)
    return parse(t.strip())


def _equal(a, b) -> bool:
    try:
        return bool(sympy.simplify(a - b) == 0) or abs(float(sympy.N(a - b))) < 1e-6
    except Exception:  # noqa: BLE001
        return False


def _norm(name: str) -> str:
    return name.replace("{", "").replace("}", "").replace(" ", "")


def named_points(statement: str) -> set[str]:
    """Capital-letter point names inside the $...$ math of a statement (A, B', A_1); areas like P_{ABC} give A, B, C."""
    names: set[str] = set()
    for math_part in re.findall(r"\$([^$]+)\$", statement):
        m = re.sub(r"\\(mathbb|mathcal|mathrm|text|operatorname)\s*\{[^{}]*\}", " ", math_part)
        m = re.sub(r"[A-Z]_\{([^{}]*[A-Za-z][^{}]*)\}", r" \1 ", m)  # P_{ABC}, A_{l}: the subscript, not the letter
        m = re.sub(r"[A-Z]_([a-z])\b", " ", m)
        m = re.sub(r"\\[A-Za-z]+", " ", m)
        for name in re.findall(r"[A-Z](?:_\{?\d+\}?|'+)?", m):
            names.add(_norm(name))
    return names


_NAME = r"[A-Z](?:'+|_\{?\d+\}?)?"
_ANGLE = rf"(?:m\s*\(\s*)?\\(?:angle|sphericalangle|measuredangle|widehat)\s*\{{?\s*({_NAME})\s*({_NAME})\s*({_NAME})\s*\}}?\s*\)?"


def stated_values(statement: str) -> tuple[list[tuple], list[tuple]]:
    """The data a statement gives: lengths "$AB = 6\\text{ cm}$" -> (A, B, text), angles "$\\angle ABC = 60^\\circ$" ->
    (A, B, C, text). A chain ("$AB = BC = 6$") gives every pair; a value that is not a number ("$AB = 2BC$") nothing."""
    lengths, angles = [], []
    for part in re.findall(r"\$([^$]+)\$", statement):
        for clause in re.split(r";|,\s+(?=[A-Za-z\\])|\\text\{\s*(?:și|si|iar)\s*\}", part):
            sides = [s.strip() for s in clause.split("=")]
            if len(sides) < 2 or latex_value(sides[-1]) is None:
                continue
            for side in sides[:-1]:
                pair = re.fullmatch(rf"({_NAME})\s*({_NAME})", side)
                angle = re.fullmatch(_ANGLE, side)
                if pair:
                    lengths.append((_norm(pair.group(1)), _norm(pair.group(2)), sides[-1]))
                elif angle:
                    angles.append((_norm(angle.group(1)), _norm(angle.group(2)), _norm(angle.group(3)), sides[-1]))
    return lengths, angles


def _points(figure: dict) -> tuple[dict, list[str]]:
    exact, problems = {}, []
    for p in figure["points"]:
        coords = [parse(p.get(axis) or "0") for axis in ("x", "y", "z")]
        if any(c is None or not c.is_number for c in coords):
            problems.append(f"coordonate necitibile la {p['name']}: {p.get('x')}, {p.get('y')}, {p.get('z')}")
            continue
        exact[_norm(p["name"])] = sympy.Matrix(coords)
    return exact, problems


def check(figure: dict, statement: str) -> dict:
    """Problems = the figure contradicts itself or the statement; warnings = something only a person can judge."""
    if figure.get("type") == "venn":
        venn = figure.get("venn") or {}
        problems = [] if len(venn.get("sets") or []) == 2 else ["o diagramă Venn are aici exact două mulțimi"]
        return {"ok": not problems, "problems": problems, "warnings": []}
    exact, problems = _points(figure)
    warnings: list[str] = []
    for interval in figure.get("intervals") or []:
        ends = [parse(interval.get("from")), parse(interval.get("to"))]
        if any(e is None for e in ends):
            problems.append(f"interval necitibil: {interval.get('from')} .. {interval.get('to')}")
        elif not bool(ends[0] < ends[1]):
            problems.append(f"intervalul {interval.get('from')} .. {interval.get('to')} are capetele inversate")

    def known(names) -> bool:
        missing = [n for n in names if _norm(n) not in exact]
        if missing:
            problems.append(f"punct inexistent în figură: {', '.join(missing)}")
        return not missing

    for s in figure["segments"]:
        known([s["a"], s["b"]])
    for c in figure["circles"]:
        known([c["center"], c["through"]])
    for arc in figure.get("arcs") or []:
        if known([arc["center"], arc["start"], arc["end"]]) and \
                _equal((exact[_norm(arc["start"])] - exact[_norm(arc["center"])]).norm(), 0):
            problems.append(f"arcul cu centrul {arc['center']} începe chiar în centru")
    for region in figure.get("regions") or []:
        if len(region["points"]) < 3:
            problems.append("o regiune hașurată are nevoie de cel puțin 3 colțuri")
        else:
            known(region["points"])
    for f in figure["functions"]:
        if parse(f["expr"], allow_x=True) is None or parse(f["x_from"]) is None or parse(f["x_to"]) is None:
            problems.append(f"funcție necitibilă: {f['expr']} pe [{f['x_from']}, {f['x_to']}]")
    if figure["type"] != "solid" and any(v[2] != 0 for v in exact.values()):
        warnings.append("figură plană cu z diferit de 0 (z e ignorat la desen)")
    ticked: dict[str, list[tuple[str, object]]] = {}  # equal-segment marks: same number of ticks = same length
    for mark in figure["marks"]:
        pts = mark["points"]
        need = 2 if mark["kind"] in ("length", "ticks") else 3
        if len(pts) != need:
            problems.append(f"marcaj {mark['kind']} cu {len(pts)} puncte (trebuie {need})")
            continue
        if not known(pts):
            continue
        p = [exact[_norm(n)] for n in pts]
        if mark["kind"] == "ticks":
            ticked.setdefault(str(mark["text"]).strip() or "1", []).append(
                ("".join(pts), sympy.sqrt((p[0] - p[1]).dot(p[0] - p[1]))))
        elif mark["kind"] == "right_angle":
            if not _equal((p[0] - p[1]).dot(p[2] - p[1]), 0):
                problems.append(f"unghiul {''.join(pts)} e marcat drept, dar nu e de 90°")
        elif mark["kind"] == "length":
            value = latex_value(mark["text"])
            length = sympy.sqrt((p[0] - p[1]).dot(p[0] - p[1]))
            if value is not None and not _equal(length, value):
                problems.append(f"{''.join(pts)} scrie {mark['text']}, dar din coordonate e {sympy.nsimplify(length)}")
        else:
            value = latex_value(mark["text"])
            u, w = p[0] - p[1], p[2] - p[1]
            cos = u.dot(w) / sympy.sqrt(u.dot(u) * w.dot(w))
            degrees = math.degrees(math.acos(max(-1.0, min(1.0, float(sympy.N(cos))))))
            if value is not None and abs(degrees - float(sympy.N(value))) > 0.01:
                problems.append(f"unghiul {''.join(pts)} scrie {mark['text']}, dar din coordonate e {degrees:.2f}°")
    for group in ticked.values():
        first_name, first_length = group[0]
        for name, length in group[1:]:
            if not _equal(length, first_length):
                problems.append(f"{first_name} și {name} sunt marcate egale, dar au lungimi diferite")
    lengths, angles = stated_values(statement)
    for a, b, text in lengths:
        if a in exact and b in exact:
            length = sympy.sqrt((exact[a] - exact[b]).dot(exact[a] - exact[b]))
            if not _equal(length, latex_value(text)):
                problems.append(f"enunțul dă {a}{b} = {text}, dar din coordonate e {sympy.nsimplify(length)}")
    for a, v, b, text in angles:
        if a in exact and v in exact and b in exact:
            u, w = exact[a] - exact[v], exact[b] - exact[v]
            cos = float(sympy.N(u.dot(w) / sympy.sqrt(u.dot(u) * w.dot(w))))
            degrees = math.degrees(math.acos(max(-1.0, min(1.0, cos))))
            if abs(degrees - float(sympy.N(latex_value(text)))) > 0.01:
                problems.append(f"enunțul dă unghiul {a}{v}{b} = {text}, dar din coordonate e {degrees:.2f}°")
    missing = sorted(named_points(statement) - set(exact) - ({"O"} if figure["type"] == "graph" else set()))
    if missing:
        warnings.append(f"puncte din enunț care lipsesc din figură: {', '.join(missing)}")
    return {"ok": not problems, "problems": problems, "warnings": warnings}


def _label(text: str) -> str:
    unit = re.match(r"^\$?(.*?)\$?\s*(cm|dm|mm|km|m)\.?$", text.strip())
    if unit:
        return f"{_label(unit.group(1))} {unit.group(2)}"
    text = re.sub(r"_([A-Za-z0-9]{2,})", r"_{\1}", text)  # M_AB is M with the subscript AB, not M_A then B
    candidate = text if text.startswith("$") else f"${text}$"
    try:
        MathTextParser("path").parse(candidate)
        return candidate
    except Exception:  # noqa: BLE001 - LaTeX matplotlib cannot draw: show it as plain text
        return re.sub(r"[\\${}]", "", text)


def _distance_to_segment(q, a, b) -> float:
    d = b - a
    length2 = float(np.dot(d, d))
    t = 0.0 if length2 < 1e-12 else min(1.0, max(0.0, float(np.dot(q - a, d)) / length2))
    return float(np.linalg.norm(a + t * d - q))


def _project(space: dict, angle: float, scale: float) -> dict:
    dx, dy = scale * math.cos(math.radians(angle)), scale * math.sin(math.radians(angle))
    return {name: np.array([v[0] + v[1] * dx, v[2] + v[1] * dy]) for name, v in space.items()}


def view_issues(figure: dict, space: dict, flat: dict) -> int:
    """How many things look wrong in this 2D view of a solid: two edges drawn on top of each other, or a vertex that
    seems to lie on an edge it is not on in space."""
    xy = np.array(list(flat.values()))
    span = float(max(xy.max(axis=0) - xy.min(axis=0))) or 1.0
    near = 0.012 * span
    segs = [(_norm(s["a"]), _norm(s["b"])) for s in figure["segments"]
            if _norm(s["a"]) in flat and _norm(s["b"]) in flat and _norm(s["a"]) != _norm(s["b"])]
    drawn = {name for name, style in _styles(figure).items() if style == "point"}

    def inside(q, a, b, tol) -> bool:
        d = b - a
        length2 = float(np.dot(d, d))
        if length2 < 1e-12:
            return False
        t = float(np.dot(q - a, d)) / length2
        return 0.02 < t < 0.98 and float(np.linalg.norm(a + t * d - q)) < tol

    issues = 0
    for a, b in segs:
        for name in flat:
            if name in drawn and name not in (a, b) and inside(flat[name], flat[a], flat[b], near) \
                    and not inside(space[name], space[a], space[b], 1e-6 * span):
                issues += 1
    for i, (a, b) in enumerate(segs):
        for c, d in segs[i + 1:]:
            if {a, b} & {c, d}:
                continue
            u, w = flat[b] - flat[a], flat[d] - flat[c]
            if abs(u[0] * w[1] - u[1] * w[0]) > 0.03 * np.linalg.norm(u) * np.linalg.norm(w):
                continue  # not parallel in the drawing
            normal = np.array([-u[1], u[0]]) / (np.linalg.norm(u) or 1)
            if abs(float(np.dot(flat[c] - flat[a], normal))) > near:
                continue  # parallel, but apart
            ts = sorted(float(np.dot(p - flat[a], u)) / float(np.dot(u, u)) for p in (flat[c], flat[d]))
            if min(1.0, ts[1]) - max(0.0, ts[0]) > 0.05:
                issues += 1
    return issues


def _best_projection(figure: dict, space: dict) -> tuple[float, float]:
    """The textbook view (40 degrees, half depth) unless something overlaps in it; then the first view without that.
    A solid with a round base (cylinder, cone, sphere) is seen from the front and a little from above, as a textbook
    draws it: its horizontal circles become level ellipses, the far half higher up."""
    if any(_norm(c["center"]) in space and _norm(c["through"]) in space
           and abs(space[_norm(c["center"])][2] - space[_norm(c["through"])][2]) < 1e-9 for c in figure["circles"]):
        return 90.0, 0.35
    best, best_issues = _VIEWS[0], None
    for angle, scale in _VIEWS:
        issues = view_issues(figure, space, _project(space, angle, scale))
        if best_issues is None or issues < best_issues:
            best, best_issues = (angle, scale), issues
        if issues == 0:
            break
    return best


def _best_view(figure: dict, space: dict) -> dict:
    return _project(space, *_best_projection(figure, space))


def _styles(figure: dict) -> dict[str, str]:
    return {_norm(p["name"]): p.get("style") or "point" for p in figure["points"]}


def _circle3d(center, through, end=None, steps: int = 180) -> np.ndarray:
    """Points of a circle (or of an arc, from `through` counterclockwise to the direction of `end`) in space: in the
    horizontal plane when the centre and `through` have the same z, else in the upright plane facing the viewer (same y),
    else in the side plane (same x). A plane figure has z = 0 everywhere, so its circles are the usual ones."""
    radius_vector = through - center
    radius = float(np.linalg.norm(radius_vector))
    if radius < 1e-12:
        return np.array([center])
    u = radius_vector / radius
    if abs(radius_vector[2]) < 1e-9 * max(1.0, radius):
        normal = np.array([0.0, 0.0, 1.0])
    elif abs(radius_vector[1]) < 1e-9 * max(1.0, radius):
        normal = np.array([0.0, -1.0, 0.0])
    else:
        normal = np.array([1.0, 0.0, 0.0])
    w = np.cross(normal, u)
    w = w / (np.linalg.norm(w) or 1.0)
    turn = 2 * math.pi
    if end is not None:
        e = end - center
        turn = math.atan2(float(np.dot(e, w)), float(np.dot(e, u))) % (2 * math.pi) or 2 * math.pi
    t = np.linspace(0.0, turn, max(8, int(steps * turn / (2 * math.pi))))
    return center + radius * (np.cos(t)[:, None] * u + np.sin(t)[:, None] * w)


def _lines_through(figure: dict, pts: dict, span: float) -> dict[str, list[float]]:
    """For each point, the directions (radians) in which something is drawn from it: segments, a circle it is on, the
    axes and a function graph it lies on. A label must go where none of these are."""
    out: dict[str, list[float]] = {name: [] for name in pts}
    eps = 1e-6 * span

    def both(name: str, vector) -> None:
        angle = math.atan2(vector[1], vector[0])
        out[name] += [angle, angle + math.pi]

    for s in figure["segments"]:
        a, b = pts.get(_norm(s["a"])), pts.get(_norm(s["b"]))
        if a is None or b is None or np.linalg.norm(b - a) < eps:
            continue
        d = b - a
        for name, q in pts.items():
            t = float(np.dot(q - a, d) / np.dot(d, d))
            if np.linalg.norm(a + t * d - q) > eps or t < -1e-9 or t > 1 + 1e-9:
                continue
            if t < 1e-9:
                out[name].append(math.atan2(d[1], d[0]))
            elif t > 1 - 1e-9:
                out[name].append(math.atan2(-d[1], -d[0]))
            else:
                both(name, d)
    for c in figure["circles"]:
        o, t = pts.get(_norm(c["center"])), pts.get(_norm(c["through"]))
        if o is None or t is None:
            continue
        radius = np.linalg.norm(t - o)
        for name, q in pts.items():
            if abs(np.linalg.norm(q - o) - radius) < eps:
                both(name, np.array([-(q - o)[1], (q - o)[0]]))
    if figure["type"] == "graph":
        x = sympy.Symbol("x")
        slopes = []
        for f in figure["functions"]:
            expr = parse(f["expr"], allow_x=True)
            if expr is not None:
                slopes.append((sympy.lambdify(x, expr, "math"), sympy.lambdify(x, sympy.diff(expr, x), "math")))
        for name, q in pts.items():
            if abs(q[0]) < eps:
                both(name, (0, 1))
            if abs(q[1]) < eps:
                both(name, (1, 0))
            for f, df in slopes:
                try:
                    if abs(f(q[0]) - q[1]) < 1e-6 * span:
                        both(name, (1, float(df(q[0]))))
                except (ValueError, ZeroDivisionError, TypeError):
                    continue
    return out


def _free_direction(angles: list[float], away) -> np.ndarray:
    """The middle of the widest empty angle around a point; among equal ones, the one facing away from the figure."""
    if not angles:
        return away
    angles = sorted(a % (2 * math.pi) for a in angles)
    best, best_score = None, None
    for i, start in enumerate(angles):
        end = angles[i + 1] if i + 1 < len(angles) else angles[0] + 2 * math.pi
        middle = (start + end) / 2
        direction = np.array([math.cos(middle), math.sin(middle)])
        score = (round(end - start, 3), round(float(np.dot(direction, away)), 3), direction[1])  # last tie: above
        if best_score is None or score > best_score:
            best, best_score = direction, score
    return best


_LOCK = threading.Lock()  # pyplot keeps global state: one drawing at a time, even with several workers


def render(figure: dict) -> tuple[bytes, bytes]:
    """(PNG for people, JPEG for the model). Raises ValueError when the figure cannot be drawn at all."""
    with _LOCK:
        return _render(figure)


def _save(fig) -> tuple[bytes, bytes]:
    png, jpeg = io.BytesIO(), io.BytesIO()
    fig.savefig(png, format="png", bbox_inches="tight", pad_inches=0.15, facecolor="white")
    fig.savefig(jpeg, format="jpeg", bbox_inches="tight", pad_inches=0.15, facecolor="white")
    plt.close(fig)
    return png.getvalue(), jpeg.getvalue()


def _render_numberline(figure: dict) -> tuple[bytes, bytes]:
    marks = {}  # what is printed under the mark (as given, "\sqrt{2}" keeps its braces) -> its value
    for p in figure["points"]:
        value = parse(p.get("x"))
        if value is not None and value.is_finite:
            marks[p["name"]] = float(sympy.N(value))
    intervals = []
    for interval in figure.get("intervals") or []:
        a, b = parse(interval["from"]), parse(interval["to"])
        if a is not None and b is not None:
            intervals.append((a, b, interval))
    finite = list(marks.values()) + [float(e) for a, b, _ in intervals for e in (a, b) if e.is_finite]
    lo, hi = (min(finite), max(finite)) if finite else (-1.0, 1.0)
    width = (hi - lo) or 2.0
    left, right = lo - 0.25 * width, hi + 0.25 * width
    fig, ax = plt.subplots(figsize=(6.4, 1.6 + 0.35 * len(intervals)), dpi=160)
    ax.axis("off")
    ax.annotate("", xy=(right, 0), xytext=(left, 0), arrowprops={"arrowstyle": "-|>", "color": "black", "lw": 1.1},
                annotation_clip=False)
    tick = 0.06 * width
    for name, x in marks.items():
        ax.plot([x, x], [-tick / 2, tick / 2], color="black", lw=1.1)
        ax.text(x, -tick, _label(name), ha="center", va="top", fontsize=11)
    for level, (a, b, interval) in enumerate(intervals):
        y = (0.18 + 0.16 * level) * width
        x0 = float(a) if a.is_finite else left
        x1 = float(b) if b.is_finite else right
        ax.plot([x0, x1], [y, y], color="#1f4e9c", lw=3, solid_capstyle="butt")
        for end, closed, finite_end in ((x0, interval["from_closed"], a.is_finite), (x1, interval["to_closed"], b.is_finite)):
            ax.plot([end, end], [0, y], color="#1f4e9c", lw=0.8, ls=(0, (3, 2)))
            if finite_end:
                ax.plot(end, y, "o", ms=7, mec="#1f4e9c", mfc="#1f4e9c" if closed else "white", mew=1.6)
        if interval.get("label"):
            ax.text((x0 + x1) / 2, y + 0.05 * width, _label(interval["label"]), ha="center", va="bottom",
                    color="#1f4e9c", fontsize=10)
    ax.set_xlim(left - 0.02 * width, right + 0.02 * width)
    ax.set_ylim(-0.25 * width, (0.3 + 0.16 * len(intervals)) * width)
    ax.set_aspect("equal")
    return _save(fig)


def _render_venn(figure: dict) -> tuple[bytes, bytes]:
    venn = figure.get("venn") or {}
    names = (list(venn.get("sets") or []) + ["A", "B"])[:2]
    shade = set(venn.get("shade") or [])
    fig, ax = plt.subplots(figsize=(5.2, 3.6), dpi=160)
    ax.set_aspect("equal")
    ax.axis("off")
    a_centre, b_centre, radius = (-0.9, 0.0), (0.9, 0.0), 1.6
    grey = "#cfcfcf"

    def disc(centre, colour, zorder):
        return ax.add_patch(plt.Circle(centre, radius, color=colour, lw=0, zorder=zorder))

    if "A-B" in shade:
        disc(a_centre, grey, 1)
    if "B-A" in shade:
        disc(b_centre, grey, 1)
    if shade & {"A-B", "B-A"} or "AnB" in shade:  # the common part painted last, grey or white, cut to both circles
        common = disc(a_centre, grey if "AnB" in shade else "white", 2)
        common.set_clip_path(plt.Circle(b_centre, radius, transform=ax.transData))
    for centre in (a_centre, b_centre):
        ax.add_patch(plt.Circle(centre, radius, fill=False, color="black", lw=1.5, zorder=3))
    if venn.get("universe"):
        ax.add_patch(matplotlib.patches.Rectangle((-3.0, -2.1), 6.0, 4.2, fill=False, color="black", lw=1.2, zorder=3))
        ax.text(-2.85, 1.95, _label(venn["universe"]), ha="left", va="top", fontsize=11)
    ax.text(-2.3, 1.45, _label(names[0]), ha="center", fontsize=13)
    ax.text(2.3, 1.45, _label(names[1]), ha="center", fontsize=13)
    places = {"A-B": (-1.6, 0.0), "B-A": (1.6, 0.0), "AnB": (0.0, 0.0), "outside": (2.55, -1.7)}
    for element in venn.get("elements") or []:
        x, y = places.get(element["part"], (0, 0))
        ax.text(x, y, _label(element["text"]), ha="center", va="center", fontsize=11, zorder=4)
    ax.set_xlim(-3.1, 3.1)
    ax.set_ylim(-2.2, 2.2)
    return _save(fig)


def _render(figure: dict) -> tuple[bytes, bytes]:
    if figure.get("type") == "numberline":
        return _render_numberline(figure)
    if figure.get("type") == "venn":
        return _render_venn(figure)
    exact, problems = _points(figure)
    if problems and not exact:
        raise ValueError("; ".join(problems))
    space = {name: np.array([float(sympy.N(c)) for c in v]) for name, v in exact.items()}
    solid = figure["type"] == "solid"
    view = _best_projection(figure, space) if solid else None

    def flat(v) -> np.ndarray:
        return _project({"p": v}, *view)["p"] if solid else np.asarray(v[:2], dtype=float)

    pts = {name: flat(v) for name, v in space.items()}
    styles = _styles(figure)
    xy = np.array(list(pts.values())) if pts else np.zeros((1, 2))
    if figure["type"] == "graph":
        xy = np.vstack([xy, [0.0, 0.0]])
    lo, hi = xy.min(axis=0), xy.max(axis=0)
    span = float(max(hi - lo)) or 1.0
    centre = xy.mean(axis=0)

    fig, ax = plt.subplots(figsize=(5.2, 4.2), dpi=160)
    ax.set_aspect("equal")
    ax.axis("off")
    if figure["type"] == "graph":
        curves = []
        x = sympy.Symbol("x")
        for f in figure["functions"]:
            expr, a, b = parse(f["expr"], allow_x=True), parse(f["x_from"]), parse(f["x_to"])
            if expr is None or a is None or b is None:
                continue
            xs = np.linspace(float(sympy.N(a)), float(sympy.N(b)), 400)
            with np.errstate(all="ignore"):
                ys = np.broadcast_to(np.asarray(sympy.lambdify(x, expr, "numpy")(xs), dtype=float), xs.shape)
            curves.append((xs, ys, f["label"]))
            lo = np.minimum(lo, [xs.min(), max(ys.min(), lo[1] - span)])
            hi = np.maximum(hi, [xs.max(), min(ys.max(), hi[1] + span)])
        pad = 0.12 * float(max(hi - lo) or 1.0)
        if figure.get("units"):
            pad = max(pad, 0.9)  # room for the last unit mark before the arrow
        (x0, y0), (x1, y1) = lo - pad, hi + pad
        x0, x1, y0, y1 = min(x0, -pad), max(x1, pad), min(y0, -pad), max(y1, pad)
        ax.set_xlim(x0, x1)
        ax.set_ylim(y0, y1)
        arrow = {"arrowstyle": "-|>", "color": "black", "lw": 1.0}
        ax.annotate("", xy=(x1, 0), xytext=(x0, 0), arrowprops=arrow, annotation_clip=False)
        ax.annotate("", xy=(0, y1), xytext=(0, y0), arrowprops=arrow, annotation_clip=False)
        ax.text(x1, -0.04 * span, "$x$", ha="right", va="top")
        ax.text(0.03 * span, y1, "$y$", ha="left", va="top")
        if "O" not in pts:
            ax.text(-0.03 * span, -0.03 * span, "$O$", ha="right", va="top")
        if figure.get("units"):
            reach = max(x1 - x0, y1 - y0)
            step = 1 if reach <= 24 else 2 if reach <= 48 else 5
            tick = 0.012 * reach
            for k in range(math.ceil(x0 / step) * step, math.floor(x1) + 1, step):
                if k and x0 < k < x1 - 0.3 * step:
                    ax.plot([k, k], [-tick, tick], color="black", lw=0.9)
                    ax.text(k, -2.2 * tick, f"${k}$", ha="center", va="top", fontsize=7)
            for k in range(math.ceil(y0 / step) * step, math.floor(y1) + 1, step):
                if k and y0 < k < y1 - 0.3 * step:
                    ax.plot([-tick, tick], [k, k], color="black", lw=0.9)
                    ax.text(-2.2 * tick, k, f"${k}$", ha="right", va="center", fontsize=7)
        for xs, ys, label in curves:
            keep = (ys >= y0) & (ys <= y1)
            ax.plot(np.where(keep, xs, np.nan), np.where(keep, ys, np.nan), color="#1f4e9c", lw=1.6)
            if label:
                i = int(np.nanargmax(np.where(keep, xs, np.nan)))
                ax.text(xs[i], ys[i], " " + _label(label), color="#1f4e9c", va="bottom")
        span = float(max(x1 - x0, y1 - y0))

    dashed = (0, (4, 3))
    for region in figure.get("regions") or []:  # under the lines
        corners = [pts.get(_norm(n)) for n in region["points"]]
        if len(corners) >= 3 and all(c is not None for c in corners):
            poly = np.array(corners)
            if region.get("hatched"):
                ax.fill(poly[:, 0], poly[:, 1], fill=False, hatch="////", edgecolor="#777777", lw=0, zorder=0)
            else:
                ax.fill(poly[:, 0], poly[:, 1], color="#dcdcdc", lw=0, zorder=0)
    for s in figure["segments"]:
        a, b = pts.get(_norm(s["a"])), pts.get(_norm(s["b"]))
        if a is not None and b is not None:
            ax.plot([a[0], b[0]], [a[1], b[1]], color="black", lw=1.4, ls=dashed if s["hidden"] else "-")
    for c in figure["circles"]:
        o3, t3 = space.get(_norm(c["center"])), space.get(_norm(c["through"]))
        if o3 is None or t3 is None:
            continue
        if not solid:
            ax.add_patch(plt.Circle(flat(o3), float(np.linalg.norm(t3 - o3)), fill=False, color="black", lw=1.4))
            continue
        # In a solid: drawn point by point in the view, so a horizontal base becomes an ellipse; its far half dashed.
        ring = _circle3d(o3, t3)
        drawn_ring = np.array([flat(p) for p in ring])
        far = ring[:, 1] > o3[1] + 1e-9 if c.get("back_hidden") else np.zeros(len(ring), dtype=bool)
        start = 0
        for i in range(1, len(ring) + 1):  # each run of the near or the far half as one line, so the dashes show
            if i == len(ring) or far[i] != far[start]:
                piece = drawn_ring[start:min(i + 1, len(ring))]
                ax.plot(piece[:, 0], piece[:, 1], color="black", lw=1.4, ls=dashed if far[start] else "-")
                start = i
    for arc in figure.get("arcs") or []:
        o3, s3, e3 = (space.get(_norm(arc[key])) for key in ("center", "start", "end"))
        if o3 is None or s3 is None or e3 is None:
            continue
        drawn_arc = np.array([flat(p) for p in _circle3d(o3, s3, e3)])
        if arc.get("shaded"):
            poly = np.vstack([flat(o3), drawn_arc])
            ax.fill(poly[:, 0], poly[:, 1], color="#dcdcdc", lw=0, zorder=0)
        ax.plot(drawn_arc[:, 0], drawn_arc[:, 1], color="black", lw=1.4, ls=dashed if arc.get("hidden") else "-")
        if arc.get("sector"):
            for end in (drawn_arc[0], drawn_arc[-1]):
                ax.plot([flat(o3)[0], end[0]], [flat(o3)[1], end[1]], color="black", lw=1.4)
    arcs_here: dict[str, int] = {}
    for mark in figure["marks"]:
        p = [pts.get(_norm(n)) for n in mark["points"]]
        if any(q is None for q in p):
            continue
        if mark["kind"] == "right_angle" and len(p) == 3:
            u = (p[0] - p[1]) / (np.linalg.norm(p[0] - p[1]) or 1)
            w = (p[2] - p[1]) / (np.linalg.norm(p[2] - p[1]) or 1)
            s = 0.05 * span
            square = np.array([p[1] + s * u, p[1] + s * (u + w), p[1] + s * w])
            ax.plot(square[:, 0], square[:, 1], color="black", lw=0.9)
        elif mark["kind"] == "ticks" and len(p) == 2:
            # Equal segments: the same number of short strokes across the middle of each.
            d = p[1] - p[0]
            unit = d / (np.linalg.norm(d) or 1)
            normal = np.array([-unit[1], unit[0]])
            count = max(1, min(3, int(mark["text"]) if str(mark["text"]).strip().isdigit() else 1))
            for k in range(count):
                c = (p[0] + p[1]) / 2 + (k - (count - 1) / 2) * 0.018 * span * unit
                stroke = np.array([c - 0.022 * span * normal, c + 0.022 * span * normal])
                ax.plot(stroke[:, 0], stroke[:, 1], color="black", lw=1.1)
        elif mark["kind"] == "length" and len(p) == 2:
            mid, d = (p[0] + p[1]) / 2, p[1] - p[0]
            this = {_norm(n) for n in mark["points"]}
            crossed = any(_distance_to_segment(mid, pts[_norm(s["a"])], pts[_norm(s["b"])]) < 0.03 * span
                          for s in figure["segments"] if {_norm(s["a"]), _norm(s["b"])} != this
                          and _norm(s["a"]) in pts and _norm(s["b"]) in pts)
            if crossed or any(np.linalg.norm(q - mid) < 0.08 * span for q in pts.values()):
                mid = p[0] + 0.72 * d  # a point or another line (the other diagonal) sits in the middle
            normal = np.array([-d[1], d[0]]) / (np.linalg.norm(d) or 1)
            if np.dot(normal, mid - centre) < 0:
                normal = -normal
            # Written along the segment, as in a textbook (a diagonal's length lies on the diagonal), never upside down.
            angle = math.degrees(math.atan2(d[1], d[0]))
            angle = angle - 180 if angle > 90 else angle + 180 if angle <= -90 else angle
            letter = re.fullmatch(r"\$?\s*(\\[A-Za-z]+|[A-Za-z])('+|_\{?\w+\}?)?\s*\$?", mark["text"].strip())
            if letter or abs(angle) > 75:
                angle = 0  # a letter ("h", "\alpha") or a nearly vertical side reads best upright
            ax.text(*(mid + 0.04 * span * normal), _label(mark["text"]), ha="center", va="center", fontsize=10,
                    color="#9c2b1f", rotation=angle, rotation_mode="anchor")
        elif mark["kind"] == "angle" and len(p) == 3:
            a1 = math.degrees(math.atan2(*(p[0] - p[1])[::-1]))
            a2 = math.degrees(math.atan2(*(p[2] - p[1])[::-1]))
            start, end = sorted((a1, a2))
            if end - start > 180:
                start, end = end, start + 360
            # Two angles marked at one vertex get arcs of different sizes, or they read as one bigger angle.
            vertex = _norm(mark["points"][1])
            arcs_here[vertex] = arcs_here.get(vertex, -1) + 1
            r = (0.08 + 0.035 * arcs_here[vertex]) * span
            ax.add_patch(matplotlib.patches.Arc(p[1], 2 * r, 2 * r, theta1=start, theta2=end, color="#9c2b1f", lw=1.0))
            mid_angle = math.radians((start + end) / 2)
            ax.text(p[1][0] + 1.9 * r * math.cos(mid_angle), p[1][1] + 1.9 * r * math.sin(mid_angle),
                    _label(mark["text"]), ha="center", va="center", fontsize=9, color="#9c2b1f")
    lines = _lines_through(figure, pts, span)
    for name, p in pts.items():
        style = styles.get(name, "point")
        if style == "helper":
            continue  # the far end of a line, a corner of a region: not drawn
        if style == "label":
            ax.text(*p, _label(name), ha="center", va="center", fontsize=11)  # a line "d", a circle "C": the name only
            continue
        away = p - centre
        away = away / np.linalg.norm(away) if np.linalg.norm(away) > 1e-9 else np.array([0.6, 0.8])
        ax.plot(*p, "o", color="black", ms=2.4)
        if name.startswith("_"):
            continue  # an unnamed point the book shows as a dot only
        direction = _free_direction(lines.get(name, []), away)
        ax.text(*(p + 0.055 * span * direction), _label(name), ha="center", va="center", fontsize=11)

    if figure["type"] != "graph":
        ax.autoscale_view()
    png, jpeg = io.BytesIO(), io.BytesIO()
    fig.savefig(png, format="png", bbox_inches="tight", pad_inches=0.15, facecolor="white")
    fig.savefig(jpeg, format="jpeg", bbox_inches="tight", pad_inches=0.15, facecolor="white")
    plt.close(fig)
    return png.getvalue(), jpeg.getvalue()
