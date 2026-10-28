"""Shared client IP helper.

Trusts CF-Connecting-IP only when the immediate peer is loopback (127.0.0.1, ::1,
or localhost) because Cloudflare tunnel forwards to loopback. Never trusts
X-Forwarded-For. Normalizes IPv6 addresses to /64 networks to prevent
address-cycling / rate-limit bypass attacks.
"""
from __future__ import annotations

import ipaddress
from typing import Any


def is_loopback(peer: str | None) -> bool:
    """Check if the given peer IP string is a loopback address or localhost."""
    if not peer:
        return False
    if peer == "localhost":
        return True
    try:
        return ipaddress.ip_address(peer).is_loopback
    except ValueError:
        return False


def normalize_ip(ip: str) -> str:
    """Normalize IP address, keying IPv6 by /64 to prevent address-cycling attacks."""
    if not ip or ip == "?":
        return ip or "?"
    try:
        addr = ipaddress.ip_address(ip)
        if addr.version == 6:
            net = ipaddress.ip_network(f"{ip}/64", strict=False)
            return str(net.network_address)
        return str(addr)
    except ValueError:
        return ip


def get_client_ip(request: Any) -> str:
    """Extract and normalize client IP.

    CF-Connecting-IP is trusted only if request.remote is a loopback address.
    IPv6 addresses are normalized to /64 network boundaries.
    """
    peer = getattr(request, "remote", None) or "127.0.0.1"
    ip = peer
    if is_loopback(peer):
        headers = getattr(request, "headers", {})
        cf_ip = headers.get("CF-Connecting-IP")
        if cf_ip:
            ip = cf_ip.split(",")[0].strip()
    return normalize_ip(ip)


# Alias for compatibility with cosmetics.py
client_ip = get_client_ip
get_auth_ip_key = normalize_ip
