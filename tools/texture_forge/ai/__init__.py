"""Optional local-LLM bridge: natural language → generation parameters."""
from .ollama import LLMError, OllamaClient, OpenAICompatibleClient, make_client
from .prompt_parser import ParseResult, PromptParser, keyword_params

__all__ = ["LLMError", "OllamaClient", "OpenAICompatibleClient", "make_client",
           "ParseResult", "PromptParser", "keyword_params"]
