# Implements: REQ-F-STREAM-001
"""SSE broadcaster — manages connected clients and pushes events."""

from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import AsyncGenerator

logger = logging.getLogger(__name__)


class SSEBroadcaster:
    """Manages SSE client subscriptions and broadcasts events."""

    def __init__(self) -> None:
        self._queues: set[asyncio.Queue] = set()
        self._lock = asyncio.Lock()
        self._loop: asyncio.AbstractEventLoop | None = None

    def set_loop(self, loop: asyncio.AbstractEventLoop) -> None:
        """Set the asyncio event loop (called during startup)."""
        self._loop = loop

    def send(self, event_type: str, data: dict) -> None:
        """Push an event to all connected clients.

        Thread-safe — can be called from the watchdog thread.
        """
        if not self._loop:
            return

        payload = json.dumps(data)

        def _enqueue():
            dead: list[asyncio.Queue] = []
            for q in self._queues:
                try:
                    q.put_nowait({"event": event_type, "data": payload})
                except asyncio.QueueFull:
                    dead.append(q)
            for q in dead:
                self._queues.discard(q)

        self._loop.call_soon_threadsafe(_enqueue)

    async def subscribe(self) -> AsyncGenerator[dict, None]:
        """Yield SSE events for a single client connection."""
        queue: asyncio.Queue = asyncio.Queue(maxsize=100)
        self._queues.add(queue)
        try:
            while True:
                event = await queue.get()
                yield event
        finally:
            self._queues.discard(queue)
