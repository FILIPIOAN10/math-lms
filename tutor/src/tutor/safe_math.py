"""Reading a short math string with SymPy without letting it run away.

SymPy computes what it parses: "9^9^9" is a number with hundreds of millions of digits and never finishes (health
audit 2026-11-19 M1). So a string is read unevaluated first, its powers are checked, and only a tame one is read again
for real. The answer check, the figure check and the lesson check use the same rule.
"""

import sympy

MAX_EXPONENT = 100


def tame(expression) -> bool:
    """No power that could take forever: an exponent is a plain number up to MAX_EXPONENT (or a fraction of one), never
    itself a power ("9**9**9")."""
    for power in expression.atoms(sympy.Pow):
        exponent = power.exp
        if exponent.atoms(sympy.Pow) or not exponent.is_number:
            return False
        value = exponent.doit()  # sums and products of plain numbers ("-3", "3 - 1"): cheap to compute
        if not value.is_Rational or abs(value) > MAX_EXPONENT:
            return False
    return True


def parse_tame(text: str, local_dict: dict, global_dict: dict):
    """The evaluated SymPy value of `text`, or None when one of its powers is not tame. Parse errors propagate."""
    scope = {**global_dict, "Add": sympy.Add, "Mul": sympy.Mul, "Pow": sympy.Pow}  # what evaluate=False calls
    raw = sympy.parse_expr(text, local_dict=local_dict, global_dict=scope, evaluate=False)
    if not tame(raw):
        return None
    return sympy.parse_expr(text, local_dict=local_dict, global_dict=global_dict)
