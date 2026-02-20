# Validates: REQ-F-STREAM-001
"""Tests for SSE broadcaster."""

import asyncio

import pytest

from genesis_monitor.server.broadcaster import SSEBroadcaster


@pytest.mark.asyncio
async def test_subscribe_receives_events():
    """A subscriber receives events sent by the broadcaster."""
    b = SSEBroadcaster()
    b.set_loop(asyncio.get_running_loop())

    received = []

    async def consumer():
        async for event in b.subscribe():
            received.append(event)
            if len(received) >= 2:
                break

    task = asyncio.create_task(consumer())

    # Give the subscriber time to register
    await asyncio.sleep(0.05)

    b.send("test_event", {"msg": "hello"})
    b.send("test_event", {"msg": "world"})

    await asyncio.wait_for(task, timeout=2.0)

    assert len(received) == 2
    assert received[0]["event"] == "test_event"


@pytest.mark.asyncio
async def test_multiple_subscribers():
    """Multiple subscribers each receive the same events."""
    b = SSEBroadcaster()
    b.set_loop(asyncio.get_running_loop())

    results_1 = []
    results_2 = []

    async def consumer(results):
        async for event in b.subscribe():
            results.append(event)
            if len(results) >= 1:
                break

    t1 = asyncio.create_task(consumer(results_1))
    t2 = asyncio.create_task(consumer(results_2))

    await asyncio.sleep(0.05)
    b.send("evt", {"data": 1})

    await asyncio.wait_for(asyncio.gather(t1, t2), timeout=2.0)

    assert len(results_1) == 1
    assert len(results_2) == 1


@pytest.mark.asyncio
async def test_send_without_loop():
    """Sending without setting a loop doesn't crash."""
    b = SSEBroadcaster()
    b.send("event", {"data": "ignored"})  # Should not raise
