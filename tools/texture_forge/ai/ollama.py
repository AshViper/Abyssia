"""Local LLM clients (stdlib only - no extra dependencies).

* :class:`OllamaClient` – Ollama's native HTTP API (``/api/chat``, ``/api/tags``)
* :class:`OpenAICompatibleClient` – any OpenAI-compatible local server
  (llama.cpp ``llama-server``, LM Studio, vLLM, ...) at ``<host>/v1``

No external API is required; hosts default to localhost.
"""
from __future__ import annotations

import json
import re
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Protocol

from core.config import AIConfig


class LLMError(RuntimeError):
    pass


class LLMClient(Protocol):
    def chat(self, system: str, user: str, schema: dict | None = None) -> str: ...
    def list_models(self) -> list[str]: ...
    def test(self) -> tuple[bool, str]: ...


def _request(url: str, payload: dict | None, timeout: float, headers: dict | None = None) -> dict:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST" if data else "GET",
                                 headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")[:300]
        raise LLMError(f"HTTP {e.code} from {url}: {detail}") from e
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        raise LLMError(f"cannot reach {url}: {getattr(e, 'reason', e)}") from e
    try:
        return json.loads(body)
    except ValueError as e:
        raise LLMError(f"invalid JSON from {url}") from e


_THINK = re.compile(r"<think>.*?</think>", re.S | re.I)


def strip_thinking(text: str) -> str:
    """Remove reasoning blocks some local models (e.g. Qwen) emit."""
    return _THINK.sub("", text or "").strip()


@dataclass
class OllamaClient:
    host: str = "http://127.0.0.1:11434"
    model: str = "qwen3.5:4b"
    timeout: float = 60.0
    temperature: float = 0.2

    def _url(self, path: str) -> str:
        return self.host.rstrip("/") + path

    def list_models(self) -> list[str]:
        data = _request(self._url("/api/tags"), None, min(self.timeout, 10))
        return sorted(m.get("name", "") for m in data.get("models", []) if m.get("name"))

    def chat(self, system: str, user: str, schema: dict | None = None) -> str:
        base = {
            "model": self.model,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
            "stream": False,
            "options": {"temperature": self.temperature},
        }
        # newest API first (JSON-schema output, thinking off), then plain JSON mode
        attempts = [{"format": schema or "json", "think": False}, {"format": "json"}]
        last: LLMError | None = None
        for extra in attempts:
            try:
                data = _request(self._url("/api/chat"), {**base, **extra}, self.timeout)
                return strip_thinking(data.get("message", {}).get("content", ""))
            except LLMError as e:
                last = e
                if not str(e).startswith("HTTP 4"):
                    break  # connection problems won't be fixed by a simpler payload
        raise last or LLMError("no response")

    def test(self) -> tuple[bool, str]:
        try:
            models = self.list_models()
        except LLMError as e:
            return False, str(e)
        if self.model and self.model not in models and f"{self.model}:latest" not in models:
            return False, f"connected, but model '{self.model}' is not pulled (available: {', '.join(models[:8]) or 'none'})"
        return True, f"connected to Ollama · {len(models)} model(s)"


@dataclass
class OpenAICompatibleClient:
    host: str = "http://127.0.0.1:8080/v1"
    model: str = "local"
    api_key: str = ""
    timeout: float = 60.0
    temperature: float = 0.2

    def _url(self, path: str) -> str:
        base = self.host.rstrip("/")
        if not base.endswith("/v1"):
            base += "/v1"
        return base + path

    def _headers(self) -> dict:
        return {"Authorization": f"Bearer {self.api_key}"} if self.api_key else {}

    def list_models(self) -> list[str]:
        data = _request(self._url("/models"), None, min(self.timeout, 10), self._headers())
        return sorted(m.get("id", "") for m in data.get("data", []) if m.get("id"))

    def chat(self, system: str, user: str, schema: dict | None = None) -> str:
        payload = {
            "model": self.model,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
            "temperature": self.temperature,
            "response_format": {"type": "json_object"},
        }
        data = _request(self._url("/chat/completions"), payload, self.timeout, self._headers())
        try:
            return strip_thinking(data["choices"][0]["message"]["content"])
        except (KeyError, IndexError, TypeError) as e:
            raise LLMError("unexpected response shape") from e

    def test(self) -> tuple[bool, str]:
        try:
            models = self.list_models()
        except LLMError as e:
            return False, str(e)
        return True, f"connected · {len(models)} model(s)"


PROVIDERS = {
    "ollama": "Ollama",
    "llamacpp": "llama.cpp (OpenAI-compatible)",
    "openai": "OpenAI-compatible local API",
    "none": "Off (keyword parser only)",
}


def make_client(cfg: AIConfig) -> LLMClient | None:
    if cfg.provider == "ollama":
        return OllamaClient(cfg.host, cfg.model, cfg.timeout, cfg.temperature)
    if cfg.provider in ("openai", "llamacpp"):
        return OpenAICompatibleClient(cfg.host, cfg.model, cfg.api_key, cfg.timeout, cfg.temperature)
    return None
