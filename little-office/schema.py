"""Strict input schemas for Little Office, in the style of Zod (z.object(...).strict().parse()).

Every byte that reaches the service from the network is parsed against a schema before use:
unknown keys, wrong types, out of range numbers, over long strings and strings that don't match
their pattern are all rejected. Nothing is coerced silently.
"""
import re


class Invalid(Exception):
    pass


class Schema:
    def parse(self, value, where="value"):
        raise NotImplementedError

    def safe_parse(self, value):
        try:
            return True, self.parse(value)
        except Invalid as e:
            return False, str(e)

    def optional(self, default=None):
        return Optional(self, default)


class Optional(Schema):
    def __init__(self, inner, default):
        self.inner, self.default = inner, default

    def parse(self, value, where="value"):
        return self.default if value is None else self.inner.parse(value, where)


class String(Schema):
    def __init__(self, min=0, max=200, pattern=None):
        self.min, self.max = min, max
        self.pattern = re.compile(pattern) if pattern else None

    def parse(self, value, where="value"):
        if not isinstance(value, str):
            raise Invalid(f"{where}: expected text")
        if not (self.min <= len(value) <= self.max):
            raise Invalid(f"{where}: length must be {self.min} to {self.max}")
        if any(ord(ch) < 32 and ch not in "\t\n" for ch in value):
            raise Invalid(f"{where}: control characters not allowed")
        if self.pattern and not self.pattern.fullmatch(value):
            raise Invalid(f"{where}: bad format")
        return value


class Int(Schema):
    def __init__(self, min=None, max=None):
        self.min, self.max = min, max

    def parse(self, value, where="value"):
        if isinstance(value, bool) or not isinstance(value, int):
            raise Invalid(f"{where}: expected a whole number")
        if (self.min is not None and value < self.min) or (self.max is not None and value > self.max):
            raise Invalid(f"{where}: out of range")
        return value


class Enum(Schema):
    def __init__(self, *choices):
        self.choices = choices

    def parse(self, value, where="value"):
        if value not in self.choices:
            raise Invalid(f"{where}: must be one of {', '.join(map(str, self.choices))}")
        return value


class Object(Schema):
    """Strict object: every declared key is validated, any undeclared key is an error."""

    def __init__(self, **fields):
        self.fields = fields

    def parse(self, value, where="body"):
        if not isinstance(value, dict):
            raise Invalid(f"{where}: expected an object")
        extra = set(value) - set(self.fields)
        if extra:
            raise Invalid(f"{where}: unexpected field {sorted(extra)[0]!r}")
        out = {}
        for k, s in self.fields.items():
            if k not in value and not isinstance(s, Optional):
                raise Invalid(f"{where}.{k}: required")
            out[k] = s.parse(value.get(k), f"{where}.{k}")
        return out


HEX = lambda n: String(n, n, f"[0-9a-f]{{{n}}}")

# ---- the schemas the service accepts ----
PAIR_START = Object(device=HEX(32), nonce=HEX(32), name=String(1, 40, r"[\w .'’\-]+"), days=Int(1, 90).optional(90))
PAIR_FINISH = Object(device=HEX(32), proof=HEX(64))
DECISION = Object(decision=Enum("allow", "deny"))
EMPTY = Object()
HOOK_REQUEST = Object(tool=String(1, 80, r"[\w.:\-]+"), summary=String(0, 300), detail=String(0, 2000), cwd=String(0, 1024))
REQUEST_ID = HEX(16)
SIGNED_HEADERS = Object(device=HEX(32), time=String(9, 11, r"\d+"), nonce=HEX(16), sig=HEX(64))
WAIT_SECONDS = Int(1, 30)
