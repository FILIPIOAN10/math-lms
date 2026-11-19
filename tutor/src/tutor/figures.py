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
FIGURE_SCHEMA = {
    "type": "object",
    "properties": {
        "type": {"type": "string", "enum": ["plane", "solid", "graph"]},
        "points": {"type": "array", "items": {"type": "object", "properties": {"name": _STR, "x": _STR, "y": _STR, "z": _STR},
                                              "required": ["name", "x", "y", "z"]}},
        "segments": {"type": "array", "items": {"type": "object", "properties": {"a": _STR, "b": _STR, "hidden": {"type": "boolean"}},
                                                "required": ["a", "b", "hidden"]}},
        "circles": {"type": "array", "items": {"type": "object", "properties": {"center": _STR, "through": _STR},
                                               "required": ["center", "through"]}},
        "functions": {"type": "array", "items": {"type": "object", "properties": {"expr": _STR, "x_from": _STR, "x_to": _STR, "label": _STR},
                                                 "required": ["expr", "x_from", "x_to", "label"]}},
        "marks": {"type": "array", "items": {"type": "object", "properties": {
            "kind": {"type": "string", "enum": ["right_angle", "length", "angle", "ticks"]},
            "points": {"type": "array", "items": _STR}, "text": _STR}, "required": ["kind", "points", "text"]}},
    },
    "required": ["type", "points", "segments", "circles", "functions", "marks"],
}

_NAMES = ("sqrt", "Rational", "pi", "sin", "cos", "tan", "Abs")
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
    exact, problems = _points(figure)
    warnings: list[str] = []

    def known(names) -> bool:
        missing = [n for n in names if _norm(n) not in exact]
        if missing:
            problems.append(f"punct inexistent în figură: {', '.join(missing)}")
        return not missing

    for s in figure["segments"]:
        known([s["a"], s["b"]])
    for c in figure["circles"]:
        known([c["center"], c["through"]])
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
            if name not in (a, b) and inside(flat[name], flat[a], flat[b], near) \
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


def _best_view(figure: dict, space: dict) -> dict:
    """The textbook view (40 degrees, half depth) unless something overlaps in it; then the first view without that."""
    best, best_issues = None, None
    for angle, scale in _VIEWS:
        flat = _project(space, angle, scale)
        issues = view_issues(figure, space, flat)
        if best_issues is None or issues < best_issues:
            best, best_issues = flat, issues
        if issues == 0:
            break
    return best


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


def _render(figure: dict) -> tuple[bytes, bytes]:
    exact, problems = _points(figure)
    if problems and not exact:
        raise ValueError("; ".join(problems))
    space = {name: np.array([float(sympy.N(c)) for c in v]) for name, v in exact.items()}
    pts = _best_view(figure, space) if figure["type"] == "solid" else {name: v[:2] for name, v in space.items()}
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
        for xs, ys, label in curves:
            keep = (ys >= y0) & (ys <= y1)
            ax.plot(np.where(keep, xs, np.nan), np.where(keep, ys, np.nan), color="#1f4e9c", lw=1.6)
            if label:
                i = int(np.nanargmax(np.where(keep, xs, np.nan)))
                ax.text(xs[i], ys[i], " " + _label(label), color="#1f4e9c", va="bottom")
        span = float(max(x1 - x0, y1 - y0))

    for s in figure["segments"]:
        a, b = pts.get(_norm(s["a"])), pts.get(_norm(s["b"]))
        if a is not None and b is not None:
            ax.plot([a[0], b[0]], [a[1], b[1]], color="black", lw=1.4, ls=(0, (4, 3)) if s["hidden"] else "-")
    for c in figure["circles"]:
        o, t = pts.get(_norm(c["center"])), pts.get(_norm(c["through"]))
        if o is not None and t is not None:
            ax.add_patch(plt.Circle(o, float(np.linalg.norm(t - o)), fill=False, color="black", lw=1.4))
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
            r = 0.08 * span
            ax.add_patch(matplotlib.patches.Arc(p[1], 2 * r, 2 * r, theta1=start, theta2=end, color="#9c2b1f", lw=1.0))
            mid_angle = math.radians((start + end) / 2)
            ax.text(p[1][0] + 1.9 * r * math.cos(mid_angle), p[1][1] + 1.9 * r * math.sin(mid_angle),
                    _label(mark["text"]), ha="center", va="center", fontsize=9, color="#9c2b1f")
    lines = _lines_through(figure, pts, span)
    for name, p in pts.items():
        away = p - centre
        away = away / np.linalg.norm(away) if np.linalg.norm(away) > 1e-9 else np.array([0.6, 0.8])
        ax.plot(*p, "o", color="black", ms=2.4)
        direction = _free_direction(lines.get(name, []), away)
        ax.text(*(p + 0.055 * span * direction), _label(name), ha="center", va="center", fontsize=11)

    if figure["type"] != "graph":
        ax.autoscale_view()
    png, jpeg = io.BytesIO(), io.BytesIO()
    fig.savefig(png, format="png", bbox_inches="tight", pad_inches=0.15, facecolor="white")
    fig.savefig(jpeg, format="jpeg", bbox_inches="tight", pad_inches=0.15, facecolor="white")
    plt.close(fig)
    return png.getvalue(), jpeg.getvalue()
