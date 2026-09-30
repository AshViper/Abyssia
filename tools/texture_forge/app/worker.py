"""Background execution so generation never freezes the UI.

Work runs on a ``QThreadPool``; results come back to the GUI thread through
queued signals.  Each *channel* runs at most one job the GUI cares about:
submitting a new job on a channel cancels the previous one (used by the
live preview so a slider drag only renders the latest state).
"""
from __future__ import annotations

import threading
import traceback
from dataclasses import dataclass
from typing import Any, Callable

from PySide6.QtCore import QObject, QRunnable, QThreadPool, Signal

from core.generator import GenerationCancelled

WorkFn = Callable[[Callable[[float, str], None], Callable[[], bool]], Any]


class _Signals(QObject):
    progress = Signal(int, float, str)
    done = Signal(int, object)
    error = Signal(int, str)


class _Job(QRunnable):
    def __init__(self, jid: int, fn: WorkFn, signals: _Signals, cancel: threading.Event):
        super().__init__()
        self.jid = jid
        self.fn = fn
        self.signals = signals
        self.cancel = cancel
        self.setAutoDelete(True)

    def run(self) -> None:  # worker thread
        try:
            result = self.fn(lambda p, m: self.signals.progress.emit(self.jid, float(p), str(m)),
                             self.cancel.is_set)
            if self.cancel.is_set():
                self.signals.error.emit(self.jid, "__cancelled__")
            else:
                self.signals.done.emit(self.jid, result)
        except GenerationCancelled:
            self.signals.error.emit(self.jid, "__cancelled__")
        except Exception as exc:  # report every failure to the GUI instead of dying silently
            tb = traceback.format_exc(limit=6)
            self.signals.error.emit(self.jid, f"{type(exc).__name__}: {exc}\n\n{tb}")


@dataclass
class _Pending:
    channel: str
    cancel: threading.Event
    on_done: Callable[[Any], None] | None
    on_error: Callable[[str], None] | None
    on_progress: Callable[[float, str], None] | None


class TaskRunner(QObject):
    busyChanged = Signal(bool)
    progress = Signal(float, str)

    def __init__(self, parent: QObject | None = None):
        super().__init__(parent)
        self.pool = QThreadPool(self)
        self.pool.setMaxThreadCount(3)
        self._signals = _Signals()
        self._signals.progress.connect(self._on_progress)
        self._signals.done.connect(self._on_done)
        self._signals.error.connect(self._on_error)
        self._next = 1
        self._jobs: dict[int, _Pending] = {}
        self._latest: dict[str, int] = {}

    def submit(self, channel: str, fn: WorkFn, on_done: Callable[[Any], None] | None = None,
               on_error: Callable[[str], None] | None = None,
               on_progress: Callable[[float, str], None] | None = None, supersede: bool = True) -> int:
        if supersede:
            self.cancel(channel)
        jid = self._next
        self._next += 1
        cancel = threading.Event()
        self._jobs[jid] = _Pending(channel, cancel, on_done, on_error, on_progress)
        self._latest[channel] = jid
        was_busy = self.busy()
        self.pool.start(_Job(jid, fn, self._signals, cancel))
        if not was_busy:
            self.busyChanged.emit(True)
        return jid

    def cancel(self, channel: str) -> None:
        for p in self._jobs.values():
            if p.channel == channel:
                p.cancel.set()

    def busy(self, channel: str | None = None) -> bool:
        return any(not p.cancel.is_set() and (channel is None or p.channel == channel)
                   for p in self._jobs.values())

    def wait(self, msecs: int = 3000) -> None:
        for p in self._jobs.values():
            p.cancel.set()
        self.pool.waitForDone(msecs)

    # ----------------------------------------------------------- slots
    def _is_current(self, jid: int) -> bool:
        p = self._jobs.get(jid)
        return p is not None and self._latest.get(p.channel) == jid and not p.cancel.is_set()

    def _on_progress(self, jid: int, value: float, msg: str) -> None:
        if not self._is_current(jid):
            return
        p = self._jobs[jid]
        if p.on_progress:
            p.on_progress(value, msg)
        self.progress.emit(value, msg)

    def _finish(self, jid: int) -> _Pending | None:
        p = self._jobs.pop(jid, None)
        if not self.busy():
            self.busyChanged.emit(False)
        return p

    def _on_done(self, jid: int, result: object) -> None:
        current = self._is_current(jid)
        p = self._finish(jid)
        if p and current and p.on_done:
            p.on_done(result)

    def _on_error(self, jid: int, message: str) -> None:
        current = self._is_current(jid)
        p = self._finish(jid)
        if p and current and message != "__cancelled__" and p.on_error:
            p.on_error(message)
