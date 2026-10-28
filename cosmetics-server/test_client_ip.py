"""Tests for client_ip helper and rate limiting IP behavior (B/2)."""
import pytest
from client_ip import is_loopback, normalize_ip, get_client_ip, client_ip


class DummyRequest:
    def __init__(self, remote=None, headers=None):
        self.remote = remote
        self.headers = headers or {}


def test_is_loopback():
    assert is_loopback("127.0.0.1") is True
    assert is_loopback("127.0.0.2") is True
    assert is_loopback("::1") is True
    assert is_loopback("localhost") is True
    assert is_loopback("192.168.1.1") is False
    assert is_loopback("10.0.0.1") is False
    assert is_loopback("2001:db8::1") is False
    assert is_loopback(None) is False
    assert is_loopback("invalid-ip") is False


def test_normalize_ip():
    # IPv4 unchanged
    assert normalize_ip("127.0.0.1") == "127.0.0.1"
    assert normalize_ip("192.168.1.50") == "192.168.1.50"
    # IPv6 normalized to /64 network address
    assert normalize_ip("2001:db8:abcd:0012:0000:0000:0000:0001") == "2001:db8:abcd:12::"
    assert normalize_ip("2606:4700:4700::1111") == "2606:4700:4700::"
    assert normalize_ip("::1") == "::"
    # Fallback
    assert normalize_ip("?") == "?"
    assert normalize_ip("unknown") == "unknown"


def test_loopback_trusts_cf_connecting_ip():
    req = DummyRequest(remote="127.0.0.1", headers={"CF-Connecting-IP": "203.0.113.195"})
    assert get_client_ip(req) == "203.0.113.195"
    assert client_ip(req) == "203.0.113.195"

    # Multi-value CF-Connecting-IP takes first
    req2 = DummyRequest(remote="127.0.0.1", headers={"CF-Connecting-IP": "203.0.113.195, 10.0.0.1"})
    assert get_client_ip(req2) == "203.0.113.195"

    # IPv6 in CF-Connecting-IP is /64 keyed
    req3 = DummyRequest(remote="::1", headers={"CF-Connecting-IP": "2600:1400:1::1234"})
    assert get_client_ip(req3) == "2600:1400:1::"


def test_non_loopback_ignores_spoofed_headers():
    # External caller trying to spoof CF-Connecting-IP
    req = DummyRequest(remote="192.168.2.69", headers={"CF-Connecting-IP": "1.1.1.1"})
    assert get_client_ip(req) == "192.168.2.69"

    # External caller with X-Forwarded-For is never trusted
    req2 = DummyRequest(remote="192.168.2.69", headers={"X-Forwarded-For": "8.8.8.8"})
    assert get_client_ip(req2) == "192.168.2.69"

    # Loopback with X-Forwarded-For only is not trusted (only CF-Connecting-IP)
    req3 = DummyRequest(remote="127.0.0.1", headers={"X-Forwarded-For": "8.8.8.8"})
    assert get_client_ip(req3) == "127.0.0.1"


def test_missing_remote_defaults_to_loopback():
    req = DummyRequest(remote=None)
    assert get_client_ip(req) == "127.0.0.1"
